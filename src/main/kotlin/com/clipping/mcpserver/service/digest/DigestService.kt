package com.clipping.mcpserver.service.digest

import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.error.DependencyFailureException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.InvalidStateException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.service.AdminReviewQueueService
import com.clipping.mcpserver.service.CategoryDigestStateService
import com.clipping.mcpserver.service.FeatureFlagsService
import com.clipping.mcpserver.service.RuntimeSettingService
import com.clipping.mcpserver.service.SlackBlockKitTemplateService
import com.clipping.mcpserver.service.StatsService
import com.clipping.mcpserver.service.dto.clipping.DigestItemResult
import com.clipping.mcpserver.service.dto.clipping.DigestResult
import com.clipping.mcpserver.service.competitor.CompetitorCollectionService
import com.clipping.mcpserver.service.event.DigestDeliveryFinalizationRequestedEvent
import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.service.port.SlackDeliveryPort
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.DigestCandidateStore
import com.clipping.mcpserver.store.DigestDiffLogStore
import com.clipping.mcpserver.store.SlackChannelDailySendCountStore
import com.clipping.mcpserver.store.SummaryDeliveryStore
import com.clipping.mcpserver.store.SummaryFeedbackStore
import com.clipping.mcpserver.support.GraphemeTruncator
import com.clipping.mcpserver.support.SlackChannelIdNormalizer
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

private val log = KotlinLogging.logger {}

/**
 * 다이제스트 생성 및 Slack 전송 오케스트레이션 서비스.
 *
 * 이 클래스는 세 가지 책임을 조합한다:
 * - **선정**: `DigestSelectionService` 에 위임 (후보 조회/필터링/랭킹/중복제거)
 * - **렌더링**: `DigestRenderer` 에 위임 (Block Kit / fallback 텍스트 / 이모지 정규화)
 * - **전송 & 후처리**: Slack 전송, 쿼터 예약/반환, sent 마킹 재시도, 통계 적재, 이벤트 퍼블리시
 *
 * 공개 API (`digest` / `sendPreparedDigest` / `finalizePreparedDigest`) 와 테스트에서 직접 호출하는
 * 렌더링/선정 유틸(`buildDigestText`, `buildTrackingUrl`, `sanitizeSummaryForDisplay`,
 * `summarizeForSlackText`, `stripLeadingDecoration`, `selectWithSoftPenalty`) 은 하위 호환을 위해
 * 이 클래스에 delegation 메서드로 유지된다.
 */
@Service
class DigestService(
    private val categoryStore: CategoryStore,
    private val summaryStore: BatchSummaryStore,
    private val digestCandidateStore: DigestCandidateStore,
    private val summaryDeliveryStore: SummaryDeliveryStore = summaryStore,
    private val runtimeSettingService: RuntimeSettingService,
    private val appProperties: AppProperties,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val slackMessageSender: SlackDeliveryPort,
    private val slackChannelDailySendCountStore: SlackChannelDailySendCountStore,
    private val adminReviewQueueService: AdminReviewQueueService,
    private val summaryFeedbackStore: SummaryFeedbackStore,
    private val slackBlockKitTemplateService: SlackBlockKitTemplateService,
    private val digestDeliveryFinalizationService: DigestDeliveryFinalizationService,
    private val statsService: StatsService,
    private val summarizer: LlmSummarizationPort,
    private val environment: Environment,
    private val featureFlagsService: FeatureFlagsService,
    private val digestPreviewService: DigestPreviewService,
    private val categoryDigestStateService: CategoryDigestStateService,
    private val digestDiffLogStore: DigestDiffLogStore,
) {

    /**
     * 출력 경계의 Slack 렌더링(블록/텍스트/이모지 정규화/URL tracking) 을 담당한다.
     * Spring Bean 이 아닌 이유는 DigestService 단위 테스트가 기존 생성자 시그니처를 유지해야 하기 때문.
     */
    private val renderer: DigestRenderer = DigestRenderer(appProperties)

    /**
     * 다이제스트 후보 선정·필터링을 담당한다. 렌더러를 주입해 카드 번역 후 이모지 중복 제거에 사용한다.
     */
    private val selectionService: DigestSelectionService = DigestSelectionService(
        digestCandidateStore = digestCandidateStore,
        adminReviewQueueService = adminReviewQueueService,
        summaryFeedbackStore = summaryFeedbackStore,
        renderer = renderer,
        summarizer = summarizer,
        environment = environment
    )

    /**
     * 카테고리별 다이제스트를 생성하고, 필요 시 Slack으로 전송한다.
     *
     * @param categoryId 대상 카테고리 ID
     * @param maxItems 최대 항목 수 (null이면 카테고리/런타임 기본값)
     * @param unsentOnly 미발송 항목만 대상으로 할지 여부 (기본: true)
     * @param sendToSlack Slack 전송 여부 (기본: true)
     * @param slackChannelId 전송 대상 Slack 채널 (null이면 카테고리 기본 채널)
     * @return 다이제스트 결과
     */
    fun digest(
        categoryId: String,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?
    ): DigestResult {
        // account-based 플래그가 켜져 있으면 신규 경로로 분기한다. 실패 시 legacy 로 폴백.
        val isAccountBased = featureFlagsService.isAccountBasedDigestEnabled(categoryId)
        if (isAccountBased) {
            val accountResult = runCatching {
                generateAccountBasedDigest(categoryId, sendToSlack, slackChannelId)
            }.onFailure { e ->
                log.error(e) { "[account-based] category=$categoryId — account-based digest failed; falling back to legacy" }
            }.getOrNull()
            if (accountResult != null) return accountResult
            // account-based 경로가 null 을 반환했거나 예외 폴백 — legacy 로 계속 진행
        }

        // 경쟁사 전용 카테고리는 다이제스트 대상이 아니다.
        if (categoryId == CompetitorCollectionService.COMPETITOR_CATEGORY_ID) {
            throw InvalidInputException("경쟁사 카테고리는 다이제스트 대상이 아닙니다")
        }
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")
        val runtime = runtimeSettingService.current()
        val onlyUnsent = unsentOnly ?: true
        val shouldSendToSlack = sendToSlack ?: true

        // 후보 요약을 조회하고 리뷰 결정을 적용한다
        val (candidates, sourceMap) = selectionService.fetchAndFilterCandidates(categoryId, onlyUnsent)

        // 사용자가 설정한 maxItems를 클램핑 전에 보존한다 (thin-day 푸터 비교 기준)
        val userRequestedMaxItems = maxItems
            ?: if (category.maxItems > 0) category.maxItems
            else runtime.digestDefaultMaxItems

        // 중요도 기준으로 다이제스트 항목을 선정한다
        val items = selectionService.selectDigestItems(category, candidates, sourceMap, maxItems, runtime)

        // 다이제스트 텍스트를 생성한다
        val digestText = renderer.buildDigestText(
            categoryName = category.name,
            totalCandidates = candidates.size,
            items = items,
            maxMessageChars = runtime.digestMaxMessageChars,
            itemSummaryMaxChars = runtime.digestItemSummaryMaxChars,
            keywordMaxCount = runtime.digestKeywordMaxCount,
            userRequestedMaxItems = userRequestedMaxItems
        )

        // Slack 전송이 불필요하거나 항목이 없으면 조기 반환한다
        if (!shouldSendToSlack || items.isEmpty()) {
            return buildDigestResultWithoutSlack(
                category, onlyUnsent, candidates.size, items, digestText
            )
        }

        // Slack 채널로 목적지당 1개의 다이제스트 메시지를 전송한다
        val channelId = resolveSlackChannel(category, slackChannelId)
            ?: throw InvalidStateException(
                "Slack channel is not configured for category: ${category.name}"
            )
        return try {
            sendDigestToSlack(
                category, channelId, onlyUnsent, candidates.size,
                items, digestText, runtime, userRequestedMaxItems
            )
        } catch (e: DigestDeliveryFinalizationException) {
            // 수동 다이제스트는 Slack 성공 후 후처리만 실패해도 성공 응답을 유지한다
            requestDigestFinalizationRecovery(
                summaryIds = e.summaryIds,
                categoryId = e.categoryId,
                sendAttempts = 1,
                sendSuccesses = 1,
                slackMessageTs = e.slackMessageTs
            )
            DigestResult(
                categoryId = category.id,
                categoryName = category.name,
                unsentOnly = onlyUnsent,
                totalCandidates = candidates.size,
                selectedCount = items.size,
                postedToSlack = true,
                slackChannelId = channelId,
                slackMessageTs = e.slackMessageTs,
                markedSentCount = e.itemCount,
                digestText = digestText,
                items = items
            )
        }
    }

    /**
     * 이미 선정된 다이제스트 스냅샷을 특정 Slack 목적지로 전송한다.
     * 자동 워커가 채널/DM fan-out 시 동일한 후보 집합을 재사용하도록 돕는다.
     */
    fun sendPreparedDigest(
        categoryId: String,
        preparedDigest: DigestResult,
        slackChannelId: String,
        categoryNameOverride: String? = null
    ): DigestResult {
        ensureValid(preparedDigest.categoryId == categoryId) {
            "Prepared digest category mismatch: $categoryId"
        }
        ensureValid(slackChannelId.isNotBlank()) { "Slack channel is required" }

        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")
        if (preparedDigest.items.isEmpty()) {
            return preparedDigest.copy(
                postedToSlack = false,
                slackChannelId = slackChannelId,
                slackMessageTs = null,
                markedSentCount = 0
            )
        }

        // 미리 선정된 항목을 그대로 보내 재조회 없이 동일 스냅샷을 재사용한다
        // sendPreparedDigest 경로에서는 원본 maxItems 를 알 수 없으므로 선정 수를 기준으로 하여 푸터를 생략한다
        // DM 발송 시 사용자가 설정한 구독 별칭을 카테고리 이름 대신 사용한다
        val effectiveCategory = if (categoryNameOverride != null) {
            category.copy(name = categoryNameOverride)
        } else {
            category
        }
        return sendDigestToSlack(
            category = effectiveCategory,
            channelId = slackChannelId,
            onlyUnsent = preparedDigest.unsentOnly,
            totalCandidates = preparedDigest.totalCandidates,
            items = preparedDigest.items,
            digestText = preparedDigest.digestText,
            runtime = runtimeSettingService.current(),
            userRequestedMaxItems = preparedDigest.items.size
        )
    }

    /**
     * 이미 Slack 전송이 끝난 prepared digest의 후처리만 다시 수행한다.
     * FINALIZATION_FAILED 재시도에서 중복 전송 없이 sent 마킹과 통계만 복구한다.
     */
    fun finalizePreparedDigest(categoryId: String, preparedDigest: DigestResult): Int {
        ensureValid(preparedDigest.categoryId == categoryId) {
            "Prepared digest category mismatch: $categoryId"
        }

        // 전송에 포함됐던 요약 ID만 다시 꺼내 후처리 복구 범위를 고정한다
        val sentSummaryIds = preparedDigest.items.map { it.summaryId }
        digestDeliveryFinalizationService.finalizeDelivery(
            summaryIds = sentSummaryIds,
            categoryId = categoryId,
            sendAttempts = if (sentSummaryIds.isEmpty()) 0 else 1,
            sendSuccesses = if (sentSummaryIds.isEmpty()) 0 else 1
        )
        return sentSummaryIds.size
    }

    // -- Slack sending --

    /** Slack 전송 없이 다이제스트 결과만 반환한다 */
    private fun buildDigestResultWithoutSlack(
        category: Category,
        onlyUnsent: Boolean,
        totalCandidates: Int,
        items: List<DigestItemResult>,
        digestText: String
    ): DigestResult = DigestResult(
        categoryId = category.id,
        categoryName = category.name,
        unsentOnly = onlyUnsent,
        totalCandidates = totalCandidates,
        selectedCount = items.size,
        postedToSlack = false,
        slackChannelId = category.slackChannelId,
        slackMessageTs = null,
        markedSentCount = 0,
        digestText = digestText,
        items = items
    )

    /** 다이제스트 항목을 목적지당 1개의 Slack 메시지로 묶어 전송한다 */
    private fun sendDigestToSlack(
        category: Category,
        channelId: String,
        onlyUnsent: Boolean,
        totalCandidates: Int,
        items: List<DigestItemResult>,
        digestText: String,
        runtime: RuntimeSettingService.RuntimeSettings,
        userRequestedMaxItems: Int
    ): DigestResult {
        // 일별 발송 카운트는 KST 자정 경계를 기준으로 한다.
        val sendDate = ZonedDateTime.now(KST).toLocalDate()
        val reservation = slackChannelDailySendCountStore.reserveSlot(
            channelId = channelId,
            sendDate = sendDate,
            dailyLimit = runtime.slackDailyChannelMessageLimit
        )
        if (!reservation.allowed) {
            return DigestResult(
                categoryId = category.id,
                categoryName = category.name,
                unsentOnly = onlyUnsent,
                totalCandidates = totalCandidates,
                selectedCount = items.size,
                postedToSlack = false,
                slackChannelId = channelId,
                slackMessageTs = null,
                markedSentCount = 0,
                digestText = digestText,
                items = items
            )
        }
        // 한 번의 다이제스트 메시지에 포함된 요약 ID를 기록해 성공 시 일괄 sent 마킹한다
        val sentSummaryIds = items.map { it.summaryId }
        val (resolvedBlocks, resolvedText) = resolveSlackMessageContent(
            category = category,
            channelId = channelId,
            totalCandidates = totalCandidates,
            items = items,
            runtime = runtime,
            userRequestedMaxItems = userRequestedMaxItems
        )

        val sendResult = try {
            // 선택된 모든 항목을 목적지당 1개의 Block Kit 메시지로 전송한다.
            // payload 에러 발생 시 어댑터가 자동으로 grapheme truncate 재시도 → text-only fallback 순으로 복구한다.
            slackMessageSender.sendMessage(
                channelId = channelId,
                text = resolvedText,
                blocks = resolvedBlocks,
                botToken = runtime.slackBotToken
            )
        } catch (e: Exception) {
            handleSlackSendFailure(
                channelId = channelId,
                sendDate = sendDate,
                sentSummaryIds = emptyList(),
                categoryId = category.id,
                sendAttempts = 1,
                sendSuccesses = 0
            )
            throw e
        }
        val slackTs = sendResult.ts.ifEmpty { null }

        try {
            // 목적지당 1회 전송이 성공하면 포함된 모든 요약을 함께 sent 처리한다
            finalizeDigestDelivery(
                sentSummaryIds = sentSummaryIds,
                categoryId = category.id,
                sendAttempts = 1,
                sendSuccesses = 1
            )
        } catch (e: Exception) {
            // Slack 전송은 이미 끝났으므로 워커가 재전송이 아닌 후처리 복구로 분기할 수 있게 감싼다
            throw DigestDeliveryFinalizationException(
                categoryId = category.id,
                channelId = channelId,
                slackMessageTs = slackTs,
                summaryIds = sentSummaryIds,
                itemCount = sentSummaryIds.size,
                cause = e
            )
        }
        return DigestResult(
            categoryId = category.id,
            categoryName = category.name,
            unsentOnly = onlyUnsent,
            totalCandidates = totalCandidates,
            selectedCount = items.size,
            postedToSlack = true,
            slackChannelId = channelId,
            slackMessageTs = slackTs,
            markedSentCount = sentSummaryIds.size,
            digestText = digestText,
            items = items,
            fallbackUsed = sendResult.fallbackUsed
        )
    }

    /** 다이제스트 메시지 블록과 텍스트를 결정한다 */
    private fun resolveSlackMessageContent(
        category: Category,
        channelId: String,
        totalCandidates: Int,
        items: List<DigestItemResult>,
        runtime: RuntimeSettingService.RuntimeSettings,
        userRequestedMaxItems: Int
    ): Pair<List<Map<String, Any?>>, String> {
        val digestBlocks = renderer.buildDigestBlocks(
            categoryName = category.name,
            categoryId = category.id,
            totalCandidates = totalCandidates,
            items = items,
            itemSummaryMaxChars = runtime.digestItemSummaryMaxChars,
            keywordMaxCount = runtime.digestKeywordMaxCount,
            userRequestedMaxItems = userRequestedMaxItems
        )
        val fallbackText = renderer.buildSlackDigestFallbackText(
            categoryName = category.name,
            totalCandidates = totalCandidates,
            items = items,
            itemSummaryMaxChars = runtime.digestItemSummaryMaxChars,
            keywordMaxCount = runtime.digestKeywordMaxCount,
            userRequestedMaxItems = userRequestedMaxItems
        )
        val customTemplate = runtime.slackDigestBlockKitTemplate.trim()
        if (customTemplate.isBlank()) return digestBlocks to fallbackText
        if (items.size != 1) return digestBlocks to fallbackText

        val item = items.first()

        return runCatching {
            val templateContext = buildDigestTemplateContext(
                category = category,
                channelId = channelId,
                totalCandidates = totalCandidates,
                item = item,
                runtime = runtime
            )
            slackBlockKitTemplateService
                .renderTemplate(customTemplate, templateContext)
                .let { it.blocks to it.renderedText }
        }.getOrElse { error ->
            log.warn {
                "Invalid custom Slack Block Kit template. " +
                    "Fallback to default blocks: ${error.message}"
            }
            digestBlocks to fallbackText
        }
    }

    /** 커스텀 Block Kit 템플릿 렌더링에 필요한 단건 다이제스트 컨텍스트를 만든다 */
    private fun buildDigestTemplateContext(
        category: Category,
        channelId: String,
        totalCandidates: Int,
        item: DigestItemResult,
        runtime: RuntimeSettingService.RuntimeSettings
    ): SlackBlockKitTemplateService.DigestTemplateContext {
        val topKeywords = renderer.computeTopKeywordsForTemplate(
            items = listOf(item),
            max = runtime.digestKeywordMaxCount.coerceIn(1, 10)
        ).joinToString(", ")
        // 템플릿에 주입되는 사용자 입력은 출력 경계에서 escape하고, 링크는 scheme 화이트리스트로 보호
        val safeSourceLink = renderer.safeSourceLinkOrNullOf(item.sourceLink)
        val renderedSourceLink = if (safeSourceLink != null) {
            renderer.buildTrackingUrl(item.summaryId, safeSourceLink)
        } else {
            // 템플릿이 button url에 값을 넣으면 Slack이 거부하므로 baseUrl로 폴백해 유효한 URL을 유지
            appProperties.baseUrl.trimEnd('/')
        }
        return SlackBlockKitTemplateService.DigestTemplateContext(
            categoryName = renderer.escapeForSectionOf(category.name),
            totalCandidates = totalCandidates,
            selectedCount = 1,
            topKeywords = renderer.escapeForSectionOf(topKeywords),
            generatedAtKst = renderer.currentKoreanDateLabel(),
            channelId = channelId,
            itemTitle = renderer.escapeForSectionOf(
                GraphemeTruncator.truncateByGrapheme(item.title, renderer.itemTitleMaxGraphemes)
            ),
            itemSummary = renderer.summarizeForSlackText(
                item.summary,
                runtime.digestItemSummaryMaxChars
            ),
            itemSourceLabel = renderer.escapeForSectionOf(renderer.sourceLabelOf(item.sourceLink)),
            itemSourceLink = renderedSourceLink,
            itemKeywords = renderer.escapeForSectionOf(item.keywords.joinToString(", ")),
            itemImportance = "%.2f".format(item.importanceScore)
        )
    }

    // -- post-send helpers --

    /** Slack 전송 실패 시 쿼터 반환과 부분 성공 항목의 sent 마킹을 처리한다 */
    private fun handleSlackSendFailure(
        channelId: String,
        sendDate: LocalDate,
        sentSummaryIds: List<String>,
        categoryId: String,
        sendAttempts: Int,
        sendSuccesses: Int
    ) {
        // 실패한 다이제스트 메시지의 일일 쿼터 1건을 반환한다
        slackChannelDailySendCountStore.releaseSlot(channelId, sendDate)
        if (sentSummaryIds.isNotEmpty()) {
            // 이미 전송 성공한 항목은 중복 재발송 방지를 위해 sent 마킹을 재시도한다
            markSummariesSentWithRetry(sentSummaryIds)
        }
        // 통계 적재 실패는 원래 전송 실패를 가리지 않도록 안전하게 흡수한다
        recordDigestDeliveryStatsSafely(categoryId, sendAttempts, sendSuccesses)
    }

    /** 다이제스트 전송 완료 후 sent 마킹과 통계 기록을 수행한다 */
    private fun finalizeDigestDelivery(
        sentSummaryIds: List<String>,
        categoryId: String,
        sendAttempts: Int,
        sendSuccesses: Int
    ) {
        // Slack 성공 직후 sent 마킹은 한 번 더 재시도해 일시 DB 오류에 덜 취약하게 만든다
        markSummariesSentWithRetry(sentSummaryIds)
        // 통계 적재 실패는 원래 전송 성공을 뒤집지 않도록 안전하게 흡수한다
        recordDigestDeliveryStatsSafely(categoryId, sendAttempts, sendSuccesses)
    }

    /** sent 마킹을 짧게 재시도해 일시적 JDBC 오류가 곧바로 중복 발송으로 이어지지 않게 한다 */
    private fun markSummariesSentWithRetry(summaryIds: List<String>) {
        if (summaryIds.isEmpty()) return

        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                // 전송 직후 sent 플래그를 남겨 다음 다이제스트 후보에서 빠지게 만든다
                summaryDeliveryStore.markSent(summaryIds)
                return
            } catch (error: Exception) {
                lastError = error
                log.warn(error) {
                    "Failed to mark summaries sent after Slack delivery " +
                        "(attempt=${attempt + 1}, summaryCount=${summaryIds.size})"
                }
            }
        }

        // 재시도 후에도 실패하면 외부 의존성 실패로 전파한다
        throw lastError ?: DependencyFailureException("Failed to mark summaries sent after Slack delivery")
    }

    /** 비핵심 통계 적재는 예외를 삼켜 원래 전송 결과를 보존한다 */
    private fun recordDigestDeliveryStatsSafely(
        categoryId: String,
        sendAttempts: Int,
        sendSuccesses: Int
    ) {
        if (sendAttempts <= 0) return

        // 통계 적재는 운영 관측용이므로 실패해도 실제 전송 결과는 유지한다
        runCatching {
            statsService.recordDigestDelivery(categoryId, sendAttempts, sendSuccesses)
        }.onFailure { error ->
            log.warn(error) {
                "Failed to record digest delivery stats safely: categoryId=$categoryId"
            }
        }
    }

    /** Slack 성공 이후 후처리 실패를 Spring Event로 다시 요청한다 */
    private fun requestDigestFinalizationRecovery(
        summaryIds: List<String>,
        categoryId: String,
        sendAttempts: Int,
        sendSuccesses: Int,
        deliveryLogId: String? = null,
        slackMessageTs: String? = null
    ) {
        // 비동기 후처리로 넘겨 Slack 성공 응답이 재시도로 뒤집히지 않게 만든다
        runCatching {
            applicationEventPublisher.publishEvent(
                DigestDeliveryFinalizationRequestedEvent(
                    summaryIds = summaryIds,
                    categoryId = categoryId,
                    sendAttempts = sendAttempts,
                    sendSuccesses = sendSuccesses,
                    deliveryLogId = deliveryLogId,
                    slackMessageTs = slackMessageTs
                )
            )
        }.onFailure { error ->
            log.error(error) {
                "Failed to request digest finalization recovery: " +
                    "categoryId=$categoryId, summaryCount=${summaryIds.size}"
            }
        }
    }

    // -- account-based digest path --

    /**
     * Account-based 다이제스트를 생성한다. shadow 모드면 Slack 미발송 후 diff 기록, 아니면 실제 전송.
     *
     * dry-run 결과가 EMPTY 거나 채널이 없는 경우 null 을 반환해 호출자가 legacy 로 폴백하게 한다.
     *
     * @param categoryId 대상 카테고리 ID
     * @param sendToSlack 호출자가 넘긴 전송 여부 파라미터 (false 면 preview-only 반환)
     * @param slackChannelId 채널 오버라이드 (null 이면 카테고리 기본 채널)
     * @return [DigestResult] 또는 null (legacy 폴백 신호)
     */
    private fun generateAccountBasedDigest(
        categoryId: String,
        sendToSlack: Boolean?,
        slackChannelId: String?,
    ): DigestResult? {
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        // dry-run 으로 account-based 렌더링 결과를 먼저 확인한다
        val preview = digestPreviewService.dryRunForCategory(categoryId)
        if (preview.mode == "EMPTY") {
            log.info { "[account-based] $categoryId — EMPTY dry-run result; falling through to legacy" }
            return null
        }

        val isShadow = featureFlagsService.isShadowModeEnabled(categoryId)
        val channelId = slackChannelId ?: category.slackChannelId
        if (channelId.isNullOrBlank()) {
            log.warn { "[account-based] $categoryId — no slack channel configured; skipping" }
            return null
        }

        // Block Kit JSON 문자열을 List<Map> 으로 파싱한다
        val blocks: List<Map<String, Any?>> = try {
            jacksonObjectMapper().readValue(
                preview.blocks,
                object : TypeReference<List<Map<String, Any?>>>() {}
            )
        } catch (e: Exception) {
            log.warn(e) { "[account-based] $categoryId — blocks JSON parse failed; falling back to legacy" }
            return null
        }

        if (isShadow) {
            // shadow 모드: Slack 전송 없이 diff row 를 기록한다
            digestDiffLogStore.insertIfAbsent(
                categoryId = categoryId,
                digestDate = ZonedDateTime.now(KST).toLocalDate(),
                legacySummary = null,
                newSummary = preview.blocks,
                newMode = preview.mode,
                sectionsCount = preview.sectionState.size,
                articlesCount = preview.sectionState.sumOf { it.articlesCount },
                crossMatchCount = preview.sectionState.sumOf { it.badgedCount },
            )
            log.info { "[account-based] $categoryId — shadow mode; Slack skipped, diff row recorded" }
            return DigestResult(
                categoryId = category.id,
                categoryName = category.name,
                unsentOnly = true,
                totalCandidates = 0,
                selectedCount = 0,
                postedToSlack = false,
                slackChannelId = channelId,
                slackMessageTs = null,
                markedSentCount = 0,
                digestText = preview.blocks,
                items = emptyList(),
            )
        }

        // sendToSlack=false 이면 preview-only 반환 (no Slack send)
        if (sendToSlack == false) {
            log.info { "[account-based] $categoryId — sendToSlack=false; preview-only" }
            return DigestResult(
                categoryId = category.id,
                categoryName = category.name,
                unsentOnly = true,
                totalCandidates = 0,
                selectedCount = 0,
                postedToSlack = false,
                slackChannelId = channelId,
                slackMessageTs = null,
                markedSentCount = 0,
                digestText = preview.blocks,
                items = emptyList(),
            )
        }

        // 일별 발송 쿼터를 예약한다 — legacy 경로와 동일한 KST 자정 경계 기준
        val sendDate = ZonedDateTime.now(KST).toLocalDate()
        val runtime = runtimeSettingService.current()
        val reservation = slackChannelDailySendCountStore.reserveSlot(
            channelId = channelId,
            sendDate = sendDate,
            dailyLimit = runtime.slackDailyChannelMessageLimit
        )
        if (!reservation.allowed) {
            log.info { "[account-based] $categoryId — daily Slack quota exhausted for $channelId; skipping" }
            return DigestResult(
                categoryId = category.id,
                categoryName = category.name,
                unsentOnly = true,
                totalCandidates = 0,
                selectedCount = 0,
                postedToSlack = false,
                slackChannelId = channelId,
                slackMessageTs = null,
                markedSentCount = 0,
                digestText = preview.blocks,
                items = emptyList(),
            )
        }

        // 실제 Slack 전송
        val fallbackText = "${category.name} account-based digest"
        val sendResult = try {
            slackMessageSender.sendMessage(
                channelId = channelId,
                text = fallbackText,
                blocks = blocks,
                botToken = runtime.slackBotToken,
            )
        } catch (e: Exception) {
            // 전송 예외 시 쿼터를 반환하고 예외를 다시 던진다
            slackChannelDailySendCountStore.releaseSlot(channelId, sendDate)
            throw e
        }

        // Slack 전송이 실패(ok=false)면 쿼터를 반환한다
        if (!sendResult.ok) {
            slackChannelDailySendCountStore.releaseSlot(channelId, sendDate)
        }

        // DUAL_SECTION 인 경우 실제 전송 성공 시에만 legend 노출 카운트를 증가시킨다
        if (preview.mode == "DUAL_SECTION" && sendResult.ok) {
            categoryDigestStateService.incrementLegendDisplayCount(categoryId)
        }

        val result = DigestResult(
            categoryId = category.id,
            categoryName = category.name,
            unsentOnly = true,
            totalCandidates = 0,
            selectedCount = 0,
            postedToSlack = sendResult.ok,
            slackChannelId = channelId,
            slackMessageTs = sendResult.ts.ifEmpty { null },
            markedSentCount = 0,
            digestText = preview.blocks,
            items = emptyList(),
            fallbackUsed = sendResult.fallbackUsed,
        )

        // 후처리 (sent 마킹/통계 등) — account-based 경로는 items 가 없으므로 idempotent
        finalizePreparedDigest(categoryId, result)

        return result
    }

    // -- Slack channel resolution --

    private fun resolveSlackChannel(
        category: Category,
        overrideChannelId: String?
    ): String? {
        val fromParam = normalizeSlackChannelId(overrideChannelId)
        if (fromParam != null) return fromParam
        return normalizeSlackChannelId(category.slackChannelId)
    }

    private fun normalizeSlackChannelId(channelId: String?): String? {
        return SlackChannelIdNormalizer.normalize(channelId)
    }

    // -- backward-compat delegation methods --
    // 기존 단위 테스트가 `service.buildDigestText(...)` 등을 직접 호출하므로 delegation 으로 노출한다.

    /** @see DigestRenderer.buildDigestText */
    internal fun buildDigestText(
        categoryName: String,
        totalCandidates: Int,
        items: List<DigestItemResult>,
        maxMessageChars: Int,
        itemSummaryMaxChars: Int,
        keywordMaxCount: Int,
        userRequestedMaxItems: Int
    ): String = renderer.buildDigestText(
        categoryName = categoryName,
        totalCandidates = totalCandidates,
        items = items,
        maxMessageChars = maxMessageChars,
        itemSummaryMaxChars = itemSummaryMaxChars,
        keywordMaxCount = keywordMaxCount,
        userRequestedMaxItems = userRequestedMaxItems
    )

    /** @see DigestRenderer.buildTrackingUrl */
    internal fun buildTrackingUrl(summaryId: String, originalUrl: String): String =
        renderer.buildTrackingUrl(summaryId, originalUrl)

    /** @see DigestRenderer.sanitizeSummaryForDisplay */
    internal fun sanitizeSummaryForDisplay(text: String): String =
        renderer.sanitizeSummaryForDisplay(text)

    /** @see DigestRenderer.summarizeForSlackText */
    internal fun summarizeForSlackText(summary: String, maxChars: Int): String =
        renderer.summarizeForSlackText(summary, maxChars)

    /** @see DigestRenderer.stripLeadingDecoration */
    internal fun stripLeadingDecoration(text: String): String =
        renderer.stripLeadingDecoration(text)

    /** @see DigestSelectionService.selectWithSoftPenalty */
    internal fun selectWithSoftPenalty(
        candidates: List<RankedCandidate>,
        maxItems: Int,
    ): List<BatchSummary> =
        selectionService.selectWithSoftPenalty(
            candidates = candidates.map {
                DigestSelectionService.RankedCandidate(
                    summary = it.summary,
                    rssSourceId = it.rssSourceId,
                    combinedScore = it.combinedScore,
                    importanceScore = it.importanceScore,
                    createdAt = it.createdAt,
                    id = it.id
                )
            },
            maxItems = maxItems
        )

    /**
     * 테스트 호환용 RankedCandidate 별칭.
     * 실제 구현은 `DigestSelectionService.RankedCandidate` 로 이동했으나,
     * 기존 `DigestServiceFairShareTest` 가 `DigestService.RankedCandidate(...)` 형태로
     * 생성하므로 동일 시그니처의 data class 를 여기서도 유지한다.
     */
    internal data class RankedCandidate(
        val summary: BatchSummary,
        val rssSourceId: String?,
        val combinedScore: Double,
        val importanceScore: Double,
        val createdAt: java.time.Instant,
        val id: String,
    )
}
