package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.OpsNotificationEvent

import com.clipping.mcpserver.service.notification.OperationsNotificationService
import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.model.*
import com.clipping.mcpserver.service.dto.clipping.*
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerErrorNotifier
import com.clipping.mcpserver.observability.TokenHealthTracker
import com.clipping.mcpserver.observability.TokenStatus
import com.clipping.mcpserver.resilience.InMemoryCircuitBreaker
import com.clipping.mcpserver.resilience.TokenBucketRateLimiter
import com.clipping.mcpserver.service.port.ImportanceScreeningResult
import com.clipping.mcpserver.service.port.LlmArticleSummaryResult
import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.support.GeminiErrorCategory
import com.clipping.mcpserver.support.GeminiErrorClassifier
import com.clipping.mcpserver.support.InterruptibleSleep
import com.clipping.mcpserver.support.SlackFailureSeverity
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CachedSummary
import com.clipping.mcpserver.store.LlmRunStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.SummaryCacheStore
import com.clipping.mcpserver.store.SummaryEnrichmentStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import com.fasterxml.jackson.core.JsonProcessingException
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Semaphore

private val log = KotlinLogging.logger {}

/**
 * 개별 RSS 아이템 요약 처리 서비스.
 * Spring AOP 프록시가 동작하도록 별도 @Service로 분리하여
 * per-item @Transactional을 보장한다.
 * 서킷 브레이커와 세마포어로 Gemini API 호출을 보호한다.
 */
@Service
class ItemSummarizationService(
    private val itemStore: RssItemStore,
    private val summaryStore: BatchSummaryStore,
    private val summaryEnrichmentStore: SummaryEnrichmentStore,
    private val llmRunStore: LlmRunStore,
    @param:Lazy private val summarizer: LlmSummarizationPort,
    private val runtimeSettingService: RuntimeSettingService,
    private val properties: ClippingMcpServerProperties,
    private val transactionTemplate: TransactionTemplate,
    private val llmCostService: LlmCostService,
    private val operationsNotificationService: OperationsNotificationService,
    @param:Value("\${spring.ai.google.genai.chat.options.model:unknown}")
    private val aiModelName: String,
    private val summaryCacheStore: SummaryCacheStore,
    private val metrics: ClippingMetrics,
    private val geminiRateLimiter: TokenBucketRateLimiter? = null,
    /** 토큰 헬스 배너/엔드포인트용 상태 트래커. 테스트 편의를 위해 optional. */
    private val tokenHealthTracker: TokenHealthTracker? = null,
    /** CRITICAL severity 알림을 위한 스케줄러 알림 컴포넌트. 순환 의존 회피를 위해 Lazy + optional. */
    @param:Lazy private val schedulerErrorNotifier: SchedulerErrorNotifier? = null
) {

    private val articlePromptVersion = "article.v3"
    private val screeningPromptVersion = "screening.v1"

    /** Gemini API 동시 호출 제한 세마포어 (300명 × 5구독 기준 10 동시 호출) */
    val aiSemaphore = Semaphore(10)

    /** Gemini API 서킷 브레이커 — 일시적 잘림 오류가 연쇄 전멸을 유발하지 않도록 임계값을 완화한다 */
    val geminiCircuitBreaker = InMemoryCircuitBreaker(
        name = "gemini_api",
        failureThreshold = 15,
        resetTimeoutSeconds = 30
    )

    /** 재요약 동시 실행 방지 플래그 */
    private val resummarizing = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * 단일 아이템을 요약 처리한다.
     * AI 호출은 트랜잭션 바깥에서 수행하여 DB 커넥션 점유를 방지한다.
     * DB 저장만 @Transactional saveSummarizationResult()로 위임한다.
     */
    fun summarizeSingleItem(
        category: Category,
        itemId: String,
        persona: Persona?
    ): ItemSummarizationResult {
        // 월 예산 초과 시 fallback summary를 생성한다
        if (llmCostService.isMonthlyBudgetExceeded()) {
            log.warn { "LLM monthly budget exceeded — generating fallback for item '$itemId'" }
            operationsNotificationService.sendOps(
                OpsNotificationEvent.BUDGET_EXCEEDED,
                "LLM 월 예산 초과 — 요약 자동 중단됨. 예산 설정을 확인해 주세요.",
                mapOf("date" to java.time.LocalDate.now().toString())
            )
            val item = itemStore.findById(itemId) ?: return ItemSummarizationResult(success = false)
            return saveFallbackSummary(category, item)
        }

        // 아이템 조회
        val item = itemStore.findById(itemId)
            ?: return ItemSummarizationResult(success = false)
        // 잘못된 카테고리로 같은 item을 처리하면 요약/비용 데이터가 오염되므로 즉시 중단한다.
        if (item.categoryId != category.id) {
            log.error {
                "Category mismatch while summarizing item '${item.id}': " +
                    "requested=${category.id}, actual=${item.categoryId}"
            }
            return ItemSummarizationResult(success = false)
        }
        if (item.isProcessed) {
            log.debug { "Skipping already processed item '${item.id}'" }
            return ItemSummarizationResult(success = true, skippedByScreening = true)
        }

        val runtime = runtimeSettingService.current()
        val content = item.content?.take(runtime.summaryInputMaxChars) ?: item.title
        val contentPreview = content.take(500)

        // 스크리닝 점수 확인 또는 산출
        val screenedScore = item.screenedScore ?: runScreening(category, item, contentPreview)

        // 스크리닝 임계값 미달 시 처리 건너뛰기
        if (screenedScore < properties.screeningThreshold) {
            itemStore.markProcessed(item.id)
            log.debug { "Skipped item '${item.title}' by screening (score=$screenedScore)" }
            return ItemSummarizationResult(
                success = true,
                importanceScore = screenedScore,
                skippedByScreening = true
            )
        }

        // 요약 캐시 확인 — 같은 기사+페르소나 조합이면 Gemini 호출 없이 결과 재사용
        val cacheKey = buildCacheKey(item.title, content, item.language, persona)
        val cached = summaryCacheStore.findByKey(cacheKey)
        if (cached != null) {
            metrics.recordSummaryCacheHit()
            return saveCachedSummary(category, item, cached)
        }
        metrics.recordSummaryCacheMiss()

        // 서킷 브레이커로 AI 호출 보호
        return summarizeWithProtection(category, item, content, persona, cacheKey)
    }

    /**
     * 스크리닝 점수를 AI로 산출하고 결과를 DB에 저장한다.
     * screening 호출도 llm_runs에 남겨 비용/운영 대시보드에서 빠지지 않게 한다.
     */
    private fun runScreening(category: Category, item: RssItem, contentPreview: String): Float {
        val inputPayload = "${item.title}\n\n$contentPreview"
        // Semaphore 먼저 → 슬롯 확보 후 Rate Limiter → 토큰 낭비 방지
        val screeningResult = runCatching {
            aiSemaphore.acquire()
            try {
                geminiRateLimiter?.acquire()
                retryOnTransient { summarizer.screenImportance(item.title, contentPreview) }
            } finally {
                aiSemaphore.release()
            }
        }.getOrElse { error ->
            log.warn(error) { "Screening failed for item ${item.id}: ${error.message}" }
            ImportanceScreeningResult(
                score = 0.5f,
                status = "FAILED",
                inputChars = inputPayload.length,
                outputChars = 0,
                durationMs = 0,
                errorMessage = error.message?.take(500)
            )
        }
        // ChatResponse 메타데이터에서 실제 토큰 사용량을 추출한다
        val tokenUsage = summarizer.getLastTokenUsage()
        val persistedStatus = normalizeLlmRunStatus(screeningResult.status)
        val persistedErrorMessage = buildScreeningErrorMessage(screeningResult)

        // screening 호출도 llm_runs에 적재해 비용/요청 수 집계에서 누락되지 않게 한다.
        llmRunStore.save(
            LlmRun(
                id = "",
                categoryId = category.id,
                rssItemId = item.id,
                model = aiModelName,
                promptVersion = screeningPromptVersion,
                inputHash = sha256Hex(inputPayload),
                inputChars = screeningResult.inputChars,
                outputChars = screeningResult.outputChars,
                status = persistedStatus,
                errorMessage = persistedErrorMessage,
                durationMs = screeningResult.durationMs,
                tokensIn = tokenUsage?.promptTokens,
                tokensOut = tokenUsage?.completionTokens
            )
        )
        itemStore.updateScreenedScore(item.id, screeningResult.score)
        return screeningResult.score
    }

    /**
     * 서킷 브레이커 + 세마포어로 보호된 AI 요약 호출.
     * 서킷 OPEN 시 즉시 실패 반환, 세마포어로 동시 호출 수를 제한한다.
     */
    private fun summarizeWithProtection(
        category: Category,
        item: RssItem,
        content: String,
        persona: Persona?,
        cacheKey: String,
    ): ItemSummarizationResult {
        // 서킷 브레이커 OPEN 시 fallback summary 생성
        if (!geminiCircuitBreaker.canCall()) {
            log.warn { "Circuit breaker OPEN — generating fallback for item '${item.title}'" }
            return saveFallbackSummary(category, item)
        }

        val inputPayload = "${item.title}\n\n$content"
        val startedAt = Instant.now()
        var status = "FAILED"
        var errorMessage: String? = null

        // Semaphore 먼저, Rate Limiter 나중 — 슬롯 확보 후 토큰 소비
        aiSemaphore.acquire()
        var tokensIn: Int? = null
        var tokensOut: Int? = null
        val aiResult = try {
            geminiRateLimiter?.acquire()
            // 일시적 오류(timeout, 429, 503 등)는 재시도한다.
            retryOnTransient {
                summarizer.summarizeArticle(item.title, content, item.language.toLlmArticleLanguage(), persona?.toLlmPersona())
            }.also { result ->
                status = if (result != null) "SUCCEEDED" else "EMPTY_RESULT"
                // 품질 검증 실패로 null이 반환된 경우, 거부 사유를 error_message에 기록한다.
                if (result == null) {
                    errorMessage = summarizer.getLastRejectReason()
                }
                // API가 정상 응답한 경우(품질 reject 포함)는 서킷 브레이커에 성공으로 기록한다.
                // null 반환은 "API 성공 + 품질 미달"이므로 API 장애가 아니다.
                // 실제 API 장애(timeout, 429 등)는 catch 블록에서 failure로 기록된다.
                geminiCircuitBreaker.recordSuccess()
                // ChatResponse 메타데이터에서 실제 토큰 사용량을 추출한다
                val tokenUsage = summarizer.getLastTokenUsage()
                tokensIn = tokenUsage?.promptTokens
                tokensOut = tokenUsage?.completionTokens
            }
        } catch (e: Exception) {
            status = "FAILED"
            errorMessage = e.message?.take(500)
            geminiCircuitBreaker.recordFailure()
            // TRANSIENT/UNKNOWN 경로는 handleGeminiFailure 에서 로그를 남기지 않으므로
            // stacktrace 를 잃지 않도록 여기서 먼저 warn 으로 기록한다.
            log.warn(e) { "AI summarization failed for '${item.title}': ${e.message}" }
            // Gemini 에러를 분류하여 토큰 헬스 상태를 갱신하고, EXPIRED/QUOTA면 CRITICAL 알림을 보낸다.
            handleGeminiFailure(e, item.title)
            // 서킷 브레이커 OPEN 전환 시 즉시 운영 알림을 발송한다
            notifyIfCircuitBreakerOpen()
            null
        } finally {
            aiSemaphore.release()
        }

        // AI 호출 실패(aiResult == null) 시 fallback 생성 — CB CLOSED 상태에서도 적용
        if (aiResult == null) {
            val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
            // 실패 기록은 llm_runs에 남긴다
            runCatching {
                llmRunStore.save(
                    LlmRun(
                        id = "", categoryId = category.id, rssItemId = item.id,
                        model = aiModelName, promptVersion = articlePromptVersion,
                        inputHash = sha256Hex(inputPayload), inputChars = inputPayload.length,
                        outputChars = 0, status = status, errorMessage = errorMessage,
                        durationMs = durationMs, tokensIn = tokensIn, tokensOut = tokensOut
                    )
                )
            }.onFailure { e -> log.warn(e) { "Failed to save llm_run for failed item '${item.id}'" } }
            return saveFallbackSummary(category, item)
        }

        // aiResult != null — 정상 경로
        val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
        return saveSummarizationResult(
            category, item, inputPayload, aiResult, status, errorMessage, durationMs, tokensIn, tokensOut, cacheKey
        )
    }

    /** DB 저장만 트랜잭션으로 묶는다. AI 호출은 이미 완료된 상태. TransactionTemplate으로 self-invocation 문제 회피. */
    private fun saveSummarizationResult(
        category: Category,
        item: RssItem,
        inputPayload: String,
        aiResult: LlmArticleSummaryResult?,
        status: String,
        errorMessage: String?,
        durationMs: Long,
        tokensIn: Int? = null,
        tokensOut: Int? = null,
        cacheKey: String,
    ): ItemSummarizationResult {
        return transactionTemplate.execute { _ ->
            llmRunStore.save(
                LlmRun(
                    id = "",
                    categoryId = category.id,
                    rssItemId = item.id,
                    model = aiModelName,
                    promptVersion = articlePromptVersion,
                    inputHash = sha256Hex(inputPayload),
                    inputChars = inputPayload.length,
                    outputChars = aiResult?.summary?.length ?: 0,
                    status = status,
                    errorMessage = errorMessage,
                    durationMs = durationMs,
                    tokensIn = tokensIn,
                    tokensOut = tokensOut
                )
            )

            if (aiResult == null) {
                return@execute ItemSummarizationResult(success = false)
            }

            summaryStore.save(
                BatchSummary(
                    id = "",
                    originalTitle = item.title,
                    translatedTitle = aiResult.translatedTitle,
                    summary = aiResult.summary,
                    keywords = aiResult.keywords,
                    importanceScore = aiResult.importanceScore,
                    sourceLink = item.link,
                    categoryId = category.id,
                    rssItemId = item.id,
                    sentiment = aiResult.sentiment,
                    eventType = aiResult.eventType
                )
            )

            itemStore.markProcessed(item.id)

            // 요약 성공 시 캐시에 저장 — 다음 동일 기사+페르소나 요청에서 Gemini 호출을 생략한다.
            runCatching {
                summaryCacheStore.save(
                    CachedSummary(
                        cacheKey = cacheKey,
                        summary = aiResult.summary,
                        keywords = aiResult.keywords
                            .takeIf { it.isNotEmpty() }
                            ?.let { jacksonObjectMapper().writeValueAsString(it) },
                        importanceScore = aiResult.importanceScore,
                        sentiment = aiResult.sentiment,
                        eventType = aiResult.eventType,
                        translatedTitle = aiResult.translatedTitle,
                    )
                )
            }.onFailure { e -> log.warn(e) { "summary_cache save failed for key $cacheKey" } }

            ItemSummarizationResult(
                success = true,
                keywords = aiResult.keywords,
                importanceScore = aiResult.importanceScore
            )
        } ?: ItemSummarizationResult(success = false)
    }

    /**
     * 캐시된 요약을 BatchSummary로 저장하고 결과를 반환한다.
     * Gemini 호출 없이 이전 요약 결과를 현재 카테고리에 연결한다.
     */
    private fun saveCachedSummary(
        category: Category,
        item: RssItem,
        cached: CachedSummary,
    ): ItemSummarizationResult {
        // keywords는 JSON 문자열로 저장되어 있으므로 파싱한다.
        val keywordsList = cached.keywords?.let {
            runCatching {
                jacksonObjectMapper().readValue(it, List::class.java)
                    .filterIsInstance<String>()
            }.getOrDefault(emptyList())
        } ?: emptyList()

        // 캐시 히트 시에도 BatchSummary를 저장하여 카테고리별 요약 집계에 반영한다.
        val saveResult = runCatching {
            transactionTemplate.execute { _ ->
                summaryStore.save(
                    BatchSummary(
                        id = "",
                        originalTitle = item.title,
                        translatedTitle = cached.translatedTitle,
                        summary = cached.summary,
                        keywords = keywordsList,
                        importanceScore = cached.importanceScore,
                        sourceLink = item.link,
                        categoryId = category.id,
                        rssItemId = item.id,
                        sentiment = cached.sentiment,
                        eventType = cached.eventType,
                    )
                )
                itemStore.markProcessed(item.id)
            }
        }.onFailure { e -> log.warn(e) { "saveCachedSummary failed for item '${item.id}'" } }

        // 저장에 실패한 경우 success=false로 반환하여 하위 호출자가 정확한 상태를 인지하게 한다.
        if (saveResult.isFailure) {
            return ItemSummarizationResult(success = false)
        }

        log.debug { "summary_cache hit for item '${item.title}' — Gemini call skipped" }
        return ItemSummarizationResult(
            success = true,
            keywords = keywordsList,
            importanceScore = cached.importanceScore,
            cachedHit = true,
        )
    }

    /**
     * 텍스트를 maxChars 이내에서 문장 단위로 자른다.
     * ASCII(. ! ?)와 한국어 마침표(\u3002 \uFF01 \uFF1F) 모두 인식.
     */
    internal fun truncateToSentence(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val sentenceEndPattern = Regex("[.!?\u3002\uFF01\uFF1F](?:\\s|$)")
        val truncated = text.take(maxChars)
        val lastEnd = sentenceEndPattern.findAll(truncated).lastOrNull()
        return if (lastEnd != null) {
            truncated.substring(0, lastEnd.range.first + 1)
        } else {
            truncated.trimEnd() + "\u2026"
        }
    }

    /**
     * AI 요약 실패 시 원문 발췌로 fallback summary를 생성한다.
     * Level 1: 본문 있음 → 제목 + 본문 첫 200자 (문장 단위)
     * Level 2: 본문 없음 → 제목만
     * TX 실패 시 Level 2로 재시도하여 데이터 유실을 방지한다.
     */
    private fun saveFallbackSummary(
        category: Category,
        item: RssItem,
    ): ItemSummarizationResult {
        val content = item.content
        val fallbackSummary = if (!content.isNullOrBlank()) {
            truncateToSentence(content, 200)
        } else {
            item.title
        }

        val batchSummary = BatchSummary(
            id = "",
            originalTitle = item.title,
            translatedTitle = null,
            summary = fallbackSummary,
            keywords = emptyList(),
            importanceScore = item.screenedScore ?: 0.5f,
            sourceLink = item.link,
            categoryId = category.id,
            rssItemId = item.id,
            sentiment = "NEUTRAL",
            eventType = null,
            isFallback = true,
        )

        // 1차 시도 실패 시 Level 2 (제목만)로 재시도
        runCatching {
            transactionTemplate.execute { _ ->
                summaryStore.save(batchSummary)
                itemStore.markProcessed(item.id)
            }
        }.getOrElse { e ->
            log.warn(e) { "Fallback save failed (Level 1), retrying with Level 2 (title only)" }
            runCatching {
                transactionTemplate.execute { _ ->
                    summaryStore.save(batchSummary.copy(summary = item.title))
                    itemStore.markProcessed(item.id)
                }
            }.getOrElse { e2 ->
                log.error(e2) { "Fallback save failed (Level 2) — item '${item.id}' could not be saved" }
                return ItemSummarizationResult(success = false)
            }
        }

        return ItemSummarizationResult(
            success = true,
            importanceScore = item.screenedScore ?: 0.5f,
            isFallback = true,
        )
    }

    /**
     * 실제 LLM 입력과 프롬프트 조건을 기준으로 SHA-256 캐시 키를 생성한다.
     * 본문 앞부분만 같거나 페르소나 설정이 바뀐 경우 잘못된 요약 재사용을 막는다.
     */
    private fun buildCacheKey(
        title: String,
        content: String,
        language: Language,
        persona: Persona?
    ): String {
        // 캐시 오염 방지를 위해 요약 결과에 영향을 주는 프롬프트 버전/언어/페르소나 필드를 모두 포함한다.
        val raw = listOf(
            articlePromptVersion,
            language.name,
            title.trim(),
            content,
            persona?.id ?: "default",
            persona?.currentVersion?.toString() ?: "none",
            persona?.systemPrompt.orEmpty(),
            persona?.summaryStyle.orEmpty(),
            persona?.targetAudience.orEmpty(),
            persona?.tone.orEmpty(),
            persona?.lengthPref.orEmpty()
        ).joinToString(separator = "\u001F")
        return sha256Hex(raw)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** llm_runs 제약에 맞는 상태값만 저장하도록 screening 상태를 정규화한다. */
    private fun normalizeLlmRunStatus(status: String): String {
        // 허용되지 않은 세부 실패 상태는 FAILED로 접어 DB 제약 위반을 방지한다.
        return when (status) {
            "SUCCEEDED", "EMPTY_RESULT", "FAILED" -> status
            else -> "FAILED"
        }
    }

    /** screening 원본 상태를 보존할 수 있도록 에러 메시지에 보조 정보를 합친다. */
    private fun buildScreeningErrorMessage(
        screeningResult: ImportanceScreeningResult
    ): String? {
        // 비정상 상태는 원본 상태를 남겨 운영자가 실패 유형을 추적할 수 있게 한다.
        val normalizedStatus = normalizeLlmRunStatus(screeningResult.status)
        val rawStatusMessage = if (normalizedStatus != screeningResult.status) {
            "screening_status=${screeningResult.status}"
        } else {
            null
        }

        return listOfNotNull(rawStatusMessage, screeningResult.errorMessage)
            .joinToString(" | ")
            .ifBlank { null }
    }

    /**
     * Gemini API 실패 원인을 분류하여 토큰 헬스 상태를 갱신한다.
     *
     * - [GeminiErrorCategory.EXPIRED]: 키 만료로 간주, Redis에 상태 기록 + CRITICAL 알림
     * - [GeminiErrorCategory.QUOTA_EXHAUSTED]: 쿼터 소진, 상태 기록 + CRITICAL 알림
     * - [GeminiErrorCategory.TRANSIENT] / [GeminiErrorCategory.UNKNOWN]: 상태 기록하지 않음
     *
     * 분류/알림 중 예외가 발생해도 원래 실패 흐름을 막지 않도록 runCatching으로 감싼다.
     */
    private fun handleGeminiFailure(exception: Exception, articleTitle: String) {
        runCatching {
            // cause 체인까지 따라가 HTTP 코드/키워드 기반으로 분류한다.
            val category = GeminiErrorClassifier.classify(exception)
            when (category) {
                GeminiErrorCategory.EXPIRED -> {
                    tokenHealthTracker?.recordGemini(TokenStatus.EXPIRED)
                    log.error(exception) { "Gemini API 키 만료 추정 (article=$articleTitle): ${exception.message?.take(120)}" }
                    schedulerErrorNotifier?.notifyBackgroundError(
                        context = "Gemini 키 만료",
                        exception = exception,
                        extra = "감지 근거: 401/403/invalid-auth 매칭",
                        severity = SlackFailureSeverity.CRITICAL
                    )
                }
                GeminiErrorCategory.QUOTA_EXHAUSTED -> {
                    tokenHealthTracker?.recordGemini(TokenStatus.QUOTA_EXHAUSTED)
                    log.error(exception) { "Gemini API 쿼터 소진 추정 (article=$articleTitle): ${exception.message?.take(120)}" }
                    schedulerErrorNotifier?.notifyBackgroundError(
                        context = "Gemini 쿼터 소진",
                        exception = exception,
                        extra = "감지 근거: 429/quota 매칭",
                        severity = SlackFailureSeverity.CRITICAL
                    )
                }
                GeminiErrorCategory.TRANSIENT, GeminiErrorCategory.UNKNOWN -> {
                    // 일시 장애 또는 미분류는 상태를 바꾸지 않는다 (서킷 브레이커가 별도 방어).
                }
            }
        }.onFailure { e ->
            log.warn(e) { "Gemini 실패 분류 중 예외 발생 (무시)" }
        }
    }

    /** 서킷 브레이커가 OPEN으로 전환되었으면 운영 채널에 알림을 보낸다. dedup으로 30분 내 중복 방지. */
    private fun notifyIfCircuitBreakerOpen() {
        if (geminiCircuitBreaker.state() == InMemoryCircuitBreaker.State.OPEN) {
            runCatching {
                operationsNotificationService.sendOps(
                    OpsNotificationEvent.CIRCUIT_BREAKER_OPEN,
                    "Gemini API 서킷 브레이커 OPEN — 연속 실패로 요약이 일시 중단됨",
                    mapOf("circuitBreaker" to "gemini_api")
                )
            }.onFailure { e ->
                log.warn(e) { "Failed to send circuit breaker OPEN notification" }
            }
        }
    }

    /**
     * 24시간 이내 fallback 요약을 실제 AI 요약으로 교체 시도한다.
     * AtomicBoolean으로 동시 실행을 방지한다.
     */
    fun resummarizeFallbacks(): Int {
        if (!resummarizing.compareAndSet(false, true)) {
            log.debug { "Re-summarization already in progress — skipping" }
            return 0
        }
        try {
            return doResummarizeFallbacks()
        } finally {
            resummarizing.set(false)
        }
    }

    /** 실제 재요약 로직. LIMIT 200으로 메모리 보호. */
    private fun doResummarizeFallbacks(): Int {
        if (!geminiCircuitBreaker.canCall()) {
            log.debug { "Circuit breaker still OPEN — skipping fallback re-summarization" }
            return 0
        }

        val fallbacks = summaryEnrichmentStore.findFallbacksWithin24h(limit = 200)
        if (fallbacks.isEmpty()) return 0

        log.info { "Found ${fallbacks.size} fallback summaries to re-summarize" }
        var successCount = 0

        for (summary in fallbacks) {
            if (!geminiCircuitBreaker.canCall()) break
            if (resummarizeSingleFallback(summary)) successCount++
        }

        log.info { "Fallback re-summarization complete: $successCount/${fallbacks.size} succeeded" }
        return successCount
    }

    /** 단일 fallback 기사를 재요약한다. 성공하면 true. */
    private fun resummarizeSingleFallback(summary: BatchSummary): Boolean {
        // rssItemId가 null이면 retention으로 원본 기사가 삭제된 것이므로 재요약 생략.
        val item = summary.rssItemId?.let { itemStore.findById(it) } ?: return false
        val content = item.content?.take(runtimeSettingService.current().summaryInputMaxChars) ?: return false
        val inputPayload = "${item.title}\n\n$content"
        val startedAt = Instant.now()
        var tokensIn: Int? = null
        var tokensOut: Int? = null

        val aiResult = try {
            aiSemaphore.acquire()
            try {
                geminiRateLimiter?.acquire()
                retryOnTransient { summarizer.summarizeArticle(item.title, content, item.language.toLlmArticleLanguage(), null) }
                    .also {
                        // 재요약 호출도 토큰 사용량을 기록해 비용 집계에서 누락되지 않게 한다.
                        val tokenUsage = summarizer.getLastTokenUsage()
                        tokensIn = tokenUsage?.promptTokens
                        tokensOut = tokenUsage?.completionTokens
                    }
            } finally {
                aiSemaphore.release()
            }
        } catch (e: Exception) {
            geminiCircuitBreaker.recordFailure()
            recordResummarizationRun(
                ResummarizationRunRecord(
                    categoryId = summary.categoryId,
                    item = item,
                    inputPayload = inputPayload,
                    outputChars = 0,
                    status = "FAILED",
                    errorMessage = e.message,
                    durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                    tokensIn = tokensIn,
                    tokensOut = tokensOut
                )
            )
            // 재요약 실패도 토큰 만료/쿼터 소진의 신호일 수 있으므로 동일하게 분류·기록한다.
            handleGeminiFailure(e, item.title)
            log.warn(e) { "Re-summarization failed for '${item.title}'" }
            return false
        }

        if (aiResult == null) {
            recordResummarizationRun(
                ResummarizationRunRecord(
                    categoryId = summary.categoryId,
                    item = item,
                    inputPayload = inputPayload,
                    outputChars = 0,
                    status = "EMPTY_RESULT",
                    errorMessage = summarizer.getLastRejectReason(),
                    durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                    tokensIn = tokensIn,
                    tokensOut = tokensOut
                )
            )
            return false
        }

        geminiCircuitBreaker.recordSuccess()
        val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
        val runRecord = ResummarizationRunRecord(
            categoryId = summary.categoryId,
            item = item,
            inputPayload = inputPayload,
            outputChars = aiResult.summary.length,
            status = "SUCCEEDED",
            errorMessage = null,
            durationMs = durationMs,
            tokensIn = tokensIn,
            tokensOut = tokensOut
        )
        transactionTemplate.execute { _ ->
            saveResummarizationRun(runRecord)
            summaryEnrichmentStore.updateSummaryContent(
                summaryId = summary.id,
                translatedTitle = aiResult.translatedTitle,
                summary = aiResult.summary,
                keywords = aiResult.keywords,
                importanceScore = aiResult.importanceScore,
                sentiment = aiResult.sentiment,
                eventType = aiResult.eventType,
            )
        }
        log.info { "Re-summarized fallback: '${item.title}'" }
        return true
    }

    private data class ResummarizationRunRecord(
        val categoryId: String,
        val item: RssItem,
        val inputPayload: String,
        val outputChars: Int,
        val status: String,
        val errorMessage: String?,
        val durationMs: Long,
        val tokensIn: Int?,
        val tokensOut: Int?
    )

    /** 재요약 실패/빈 응답 경로의 LLM 실행 이력을 best-effort로 저장한다. */
    private fun recordResummarizationRun(record: ResummarizationRunRecord) {
        // 복구 시도 자체가 실패하지 않도록 이력 저장 실패는 경고만 남긴다.
        runCatching {
            saveResummarizationRun(record)
        }.onFailure { e ->
            log.warn(e) { "Failed to save llm_run for fallback re-summarization item '${record.item.id}'" }
        }
    }

    /** fallback 재요약 LLM 호출 이력을 저장해 비용/실패율 집계에 포함한다. */
    private fun saveResummarizationRun(record: ResummarizationRunRecord) {
        // article 요약 프롬프트와 동일한 입력 계약이므로 같은 promptVersion으로 기록한다.
        llmRunStore.save(
            LlmRun(
                id = "",
                categoryId = record.categoryId,
                rssItemId = record.item.id,
                model = aiModelName,
                promptVersion = articlePromptVersion,
                inputHash = sha256Hex(record.inputPayload),
                inputChars = record.inputPayload.length,
                outputChars = record.outputChars,
                status = record.status,
                errorMessage = record.errorMessage?.take(500),
                durationMs = record.durationMs,
                tokensIn = record.tokensIn,
                tokensOut = record.tokensOut
            )
        )
    }

    /**
     * 일시적 실패(timeout, 429, 503 등)에 대해 단순 재시도를 수행한다.
     * 서킷 브레이커가 OPEN이면 재시도하지 않고 즉시 예외를 전파한다.
     * 스케줄러 경로에서도 호출되므로 인터럽트 플래그를 복구하는 helper로 대기한다.
     */
    internal fun <T> retryOnTransient(
        maxRetries: Int = 2,
        delayMs: Long = 2000,
        sleeper: (Long) -> Unit = { InterruptibleSleep.sleep(it, "LLM 일시 오류 재시도 대기") },
        block: () -> T
    ): T {
        val safeMaxRetries = maxRetries.coerceAtLeast(0)
        repeat(safeMaxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                // 일시적 오류가 아니거나 마지막 시도이면 즉시 예외를 전파한다.
                if (!isTransient(e) || attempt == safeMaxRetries) throw e
                log.warn(e) { "Transient failure (attempt ${attempt + 1}/$safeMaxRetries), retrying in ${delayMs}ms: ${e.message}" }
                // 재시도 전 서킷 브레이커가 OPEN으로 전환됐으면 추가 시도를 중단한다.
                if (!geminiCircuitBreaker.canCall()) {
                    log.warn { "Circuit breaker opened during retry — aborting" }
                    throw e
                }
                sleeper(delayMs)
            }
        }
        error("retryOnTransient exited unexpectedly")
    }

    /**
     * 예외가 일시적(재시도 가능) 오류인지 판별한다.
     * timeout, IO 오류, HTTP 429/503/504, gRPC UNAVAILABLE/DEADLINE_EXCEEDED가 해당된다.
     * JSON 파싱 오류(JsonProcessingException)와 Gemini 잘림 오류(GeminiTruncationException)는
     * 재시도해도 같은 결과이므로 재시도 대상이 아니다.
     */
    internal fun isTransient(e: Exception): Boolean {
        // JSON 파싱 오류는 응답 자체가 잘못된 것이므로 재시도 대상이 아니다.
        if (e is JsonProcessingException) return false
        // Gemini 응답 잘림은 같은 입력으로 재시도해도 해결되지 않는다.
        if (e is com.clipping.mcpserver.ai.GeminiTruncationException) return false
        if (e is SocketTimeoutException) return true
        if (e is IOException) return true
        val msg = (e.cause?.message ?: e.message ?: "").lowercase()
        return msg.contains("429") || msg.contains("503") || msg.contains("504") ||
            msg.contains("unavailable") || msg.contains("deadline_exceeded")
    }
}
