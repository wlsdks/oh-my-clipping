package com.ohmyclipping.ai

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.service.dto.clipping.AiDailySummaryResponse
import com.ohmyclipping.service.dto.clipping.AiSummaryResponse
import com.ohmyclipping.model.Language
import com.ohmyclipping.model.Persona
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.port.CompetitorHighlight
import com.ohmyclipping.service.port.CompetitorWeeklyInsight
import com.ohmyclipping.service.port.ImportanceScreeningResult
import com.ohmyclipping.service.port.LlmTokenUsage
import com.ohmyclipping.service.dto.CompetitorTimelineItem
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Gemini 응답이 max-output-tokens에 도달하여 잘렸을 때 발생하는 예외.
 * 재시도 대상이 아니며 서킷 브레이커에 실패로 기록된다.
 */
class GeminiTruncationException(message: String) : RuntimeException(message)

private val log = KotlinLogging.logger {}

@Component
class ClippingSummarizer(
    @param:Lazy private val chatClient: ChatClient,
    private val metrics: ClippingMetrics,
    private val summaryQualityValidator: SummaryQualityValidator,
    @param:Value("\${spring.ai.google.genai.api-key:}") private val googleApiKey: String = "",
) {

    /** 마지막 summarizeArticle/screenImportance 호출에서 추출한 토큰 사용량. 스레드 안전하게 ThreadLocal로 관리. */
    private val lastTokenUsage = ThreadLocal<LlmTokenUsage?>()

    /** 마지막 LLM 호출의 실제 토큰 사용량을 반환한다. 호출 직후에만 유효하다. */
    fun getLastTokenUsage(): LlmTokenUsage? = lastTokenUsage.get()

    /** 마지막 summarizeArticle 호출에서 품질 검증 실패 시 거부 사유. 스레드 안전하게 ThreadLocal로 관리. */
    private val lastRejectReason = ThreadLocal<String?>()

    /** 마지막 요약 호출의 품질 거부 사유를 반환한다. 품질 통과 시 null. 호출 직후에만 유효하다. */
    fun getLastRejectReason(): String? = lastRejectReason.get()

    private val mapper = jacksonObjectMapper()

    fun summarizeArticle(title: String, content: String, language: Language, persona: Persona?): AiSummaryResponse? {
        val startedAt = Instant.now()
        var inputChars = 0
        return try {
            if (isExternalAiDisabledForTest()) {
                lastRejectReason.set("TEST_AI_DISABLED")
                return null
            }
            val prompt = SummarizationPrompts.articlePrompt(
                title = title,
                content = content,
                isKorean = language == Language.KOREAN,
                summaryStyle = persona?.summaryStyle,
                targetAudience = persona?.targetAudience
            )
            inputChars = prompt.length
            // 페르소나 유무에 관계없이 system prompt를 항상 설정한다.
            val systemPrompt = if (persona != null) {
                effectiveSystemPrompt(persona)
            } else {
                "당신은 뉴스 기사를 요약하는 전문 에디터입니다. 반드시 한국어로 작성하세요. 기사 본문에 없는 사실을 추가하지 마세요. 원문의 직접 인용을 최소화하고, 핵심 사실만 요약하세요."
            }
            val builder = chatClient.prompt().user(prompt).system(systemPrompt)
            // ChatResponse를 통해 실제 토큰 사용량을 추출한다
            val chatResponse = builder.call().chatResponse()
            val response = chatResponse?.result?.output?.text
            lastTokenUsage.set(extractTokenUsage(chatResponse))
            // Gemini finish_reason이 LENGTH이면 응답이 잘린 것이므로 즉시 실패 처리한다
            detectTruncation(chatResponse, "article: $title")
            if (response == null) {
                metrics.recordSummarizationCall(
                    mode = "article",
                    success = false,
                    durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                    inputChars = inputChars,
                    outputChars = 0
                )
                // 모델이 빈 응답을 돌려준 경우 — API 이상으로 분류하여 llm_runs.error_message 에 기록한다.
                lastRejectReason.set("API_NULL_RESPONSE")
                return null
            }

            // JSON 추출 실패 — 모델 출력이 기대 포맷(JSON 블록)을 포함하지 않음.
            val json = extractJson(response) ?: run {
                lastRejectReason.set("JSON_EXTRACT_FAIL")
                return null
            }
            val normalizedJson = JsonContentNormalizer.escapeControlCharsInStrings(json)
            val parsed = parseJsonWithEmojiRetry<AiSummaryResponse>(normalizedJson)
            val qualityResult = summaryQualityValidator.validate(title, parsed)
            if (!qualityResult.accepted) {
                metrics.recordSummarizationCall(
                    mode = "article",
                    success = false,
                    durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                    inputChars = inputChars,
                    outputChars = response.length
                )
                log.warn { "Rejected low-quality summary for article: $title [reason=${qualityResult.rejectReason}]" }
                // 품질 거부 사유를 ThreadLocal에 저장하여 호출자가 llm_runs.error_message에 기록할 수 있게 한다.
                lastRejectReason.set(qualityResult.rejectReason)
                return null
            }

            // validator.accepted=true 이지만 normalized=null 인 엣지 케이스 — 논리 불일치.
            val normalized = qualityResult.normalized ?: run {
                lastRejectReason.set("NORMALIZED_NULL")
                return null
            }
            // 품질 검증 통과 시 거부 사유를 초기화한다.
            lastRejectReason.set(null)
            metrics.recordSummarizationCall(
                mode = "article",
                success = true,
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                inputChars = inputChars,
                outputChars = response.length
            )
            normalized
        } catch (e: Exception) {
            metrics.recordSummarizationCall(
                mode = "article",
                success = false,
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                inputChars = inputChars,
                outputChars = 0
            )
            log.error(e) { "Failed to summarize article: $title" }
            // 예외 경로도 진단 태그를 남겨 NULL error_message 로 끝나지 않도록 한다.
            lastRejectReason.set("EXCEPTION:${e.javaClass.simpleName}")
            null
        }
    }

    fun summarizeArticle(title: String, content: String, language: Language): AiSummaryResponse? =
        summarizeArticle(title, content, language, persona = null)

    fun generateDailySummary(categoryName: String, summariesText: String, totalItems: Int): AiDailySummaryResponse? {
        val startedAt = Instant.now()
        var inputChars = 0
        return try {
            val prompt = SummarizationPrompts.dailySummaryPrompt(categoryName, summariesText, totalItems)
            inputChars = prompt.length
            // ChatResponse를 통해 실제 토큰 사용량을 추출한다
            val chatResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .chatResponse()
            val response = chatResponse?.result?.output?.text
            lastTokenUsage.set(extractTokenUsage(chatResponse))
            if (response == null) {
                metrics.recordSummarizationCall(
                    mode = "daily",
                    success = false,
                    durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                    inputChars = inputChars,
                    outputChars = 0
                )
                return null
            }

            val json = extractJson(response) ?: return null
            val normalizedJson = JsonContentNormalizer.escapeControlCharsInStrings(json)
            val parsed = parseJsonWithEmojiRetry<AiDailySummaryResponse>(normalizedJson)
            metrics.recordSummarizationCall(
                mode = "daily",
                success = true,
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                inputChars = inputChars,
                outputChars = response.length
            )
            parsed
        } catch (e: Exception) {
            metrics.recordSummarizationCall(
                mode = "daily",
                success = false,
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                inputChars = inputChars,
                outputChars = 0
            )
            log.warn(e) { "Failed to generate daily summary for $categoryName" }
            null
        }
    }

    /**
     * 경쟁사별 주간 기사를 입력받아 경쟁사별 동향 요약과 전체 인사이트를 생성한다.
     *
     * @param articlesByCompetitor 경쟁사명 → 해당 주간 기사 목록 매핑. 빈 맵이면 null 반환.
     * @param periodLabel 프롬프트에 표시할 기간 레이블 (예: "2026-04-05 ~ 2026-04-11")
     * @return 경쟁사 하이라이트와 전체 인사이트를 포함한 결과, 실패 시 null
     */
    fun summarizeCompetitorWeekly(
        articlesByCompetitor: Map<String, List<CompetitorTimelineItem>>,
        periodLabel: String
    ): CompetitorWeeklyInsight? {
        // 입력 기사가 없으면 API 호출 없이 즉시 반환한다.
        if (articlesByCompetitor.isEmpty()) return null

        val startedAt = Instant.now()
        var inputChars = 0
        return try {
            // 경쟁사별로 상위 5개 기사만 선택하여 토큰 수를 제한한다.
            val competitorSections = articlesByCompetitor.entries.joinToString("\n\n") { (name, items) ->
                val topItems = items.take(5)
                val articleLines = topItems.joinToString("\n") { item ->
                    val summaryPreview = item.summary.take(200)
                    "- 제목: ${item.title}\n  요약: $summaryPreview"
                }
                "## $name\n$articleLines"
            }
            val prompt = """
                다음은 지난 1주일($periodLabel) 동안 경쟁사별 주요 기사 목록이다.
                경쟁사별 주요 동향을 1문장으로 요약하고,
                전체 경쟁 환경에서 주목할 인사이트 1문단(3~5문장)을 한국어로 작성해라.

                [경쟁사별 기사]
                $competitorSections

                반드시 아래 JSON 형식으로만 응답해라:
                {"competitorHighlights":[{"name":"경쟁사명","highlight":"동향 1문장"}],"weeklyInsight":"전체 인사이트 문단"}
            """.trimIndent()
            inputChars = prompt.length

            // Gemini API를 호출하여 주간 경쟁사 인사이트를 생성한다.
            val chatResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .chatResponse()
            val response = chatResponse?.result?.output?.text

            if (response == null) {
                log.warn { "Weekly competitor summary returned empty response for period: $periodLabel" }
                metrics.recordSummarizationCall(
                    mode = "competitor_weekly",
                    success = false,
                    durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                    inputChars = inputChars,
                    outputChars = 0
                )
                return null
            }

            // 응답 JSON을 파싱하여 구조체로 변환한다.
            val json = extractJson(response)
            if (json == null) {
                metrics.recordSummarizationCall(
                    mode = "competitor_weekly",
                    success = false,
                    durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                    inputChars = inputChars,
                    outputChars = response.length
                )
                return null
            }
            val normalizedJson = JsonContentNormalizer.escapeControlCharsInStrings(json)
            val parsed = parseJsonWithEmojiRetry<CompetitorWeeklyInsight>(normalizedJson)
            metrics.recordSummarizationCall(
                mode = "competitor_weekly",
                success = true,
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                inputChars = inputChars,
                outputChars = response.length
            )
            parsed
        } catch (e: Exception) {
            metrics.recordSummarizationCall(
                mode = "competitor_weekly",
                success = false,
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                inputChars = inputChars,
                outputChars = 0
            )
            log.warn(e) { "Failed to generate weekly competitor summary for period: $periodLabel" }
            null
        }
    }

    fun translateToKorean(text: String, context: String): String? {
        if (text.isBlank()) return null
        if (isExternalAiDisabledForTest()) return null

        val startedAt = Instant.now()
        return try {
            val prompt = SummarizationPrompts.koreanTranslationPrompt(text = text, context = context)
            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
            if (response == null) {
                log.warn { "Failed to translate $context to Korean (empty response)" }
                null
            } else {
                log.debug { "Translated $context to Korean in ${Duration.between(startedAt, Instant.now()).toMillis()}ms" }
                stripTranslationNoise(response)
            }
        } catch (e: Exception) {
            log.warn(e) { "Failed to translate $context to Korean" }
            null
        }
    }

    /** 경량 중요도 스크리닝. 제목 + 본문 미리보기만으로 중요도 점수를 평가하고 계측 결과를 반환한다. */
    fun screenImportance(title: String, contentPreview: String): ImportanceScreeningResult {
        val startedAt = Instant.now()
        val prompt = SummarizationPrompts.screeningPrompt(title, contentPreview)
        val inputChars = prompt.length
        if (isExternalAiDisabledForTest()) {
            return finalizeScreeningResult(startedAt, inputChars, 0, 0.5f, "TEST_AI_DISABLED")
        }
        return try {
            // screening 호출도 summary와 같은 메트릭 축으로 기록해 비용/운영 관측치를 맞춘다.
            val chatResponse = chatClient.prompt().user(prompt).call().chatResponse()
            val response = chatResponse?.result?.output?.text
            lastTokenUsage.set(extractTokenUsage(chatResponse))
            if (response == null) {
                return finalizeScreeningResult(startedAt, inputChars, 0, 0.5f, "EMPTY_RESULT", "empty response")
            }

            try {
                val json = extractJson(response)
                    ?: return finalizeScreeningResult(startedAt, inputChars, response.length, 0.5f, "NO_JSON", "response has no JSON object")
                // 이모지가 포함된 JSON 파싱 실패를 방어한다
                val tree = try {
                    mapper.readTree(json)
                } catch (_: com.fasterxml.jackson.core.JsonParseException) {
                    val cleaned = json.replace(Regex("[\\ud800-\\udfff]"), "").replace(Regex("[^\\x00-\\xFFFF]"), "")
                    mapper.readTree(cleaned)
                }
                // LLM 응답에서 importanceScore 필드 누락은 도메인 입력 검증 실패로 분류한다
                val scoreNode = tree.get("importanceScore")
                    ?: throw InvalidInputException("importanceScore missing")
                val score = scoreNode.floatValue()
                // NaN/Infinity는 유효하지 않은 스코어이므로 입력 검증 예외로 전파
                if (!score.isFinite()) {
                    throw InvalidInputException("importanceScore is not finite")
                }
                finalizeScreeningResult(startedAt, inputChars, response.length, score.coerceIn(0f, 1f), "SUCCEEDED")
            } catch (e: Exception) {
                log.warn(e) { "Failed to parse screening response for article: $title" }
                finalizeScreeningResult(
                    startedAt = startedAt,
                    inputChars = inputChars,
                    outputChars = response.length,
                    score = 0.5f,
                    status = "FAILED_PARSE",
                    errorMessage = e.message?.take(500)
                )
            }
        } catch (e: Exception) {
            log.warn(e) { "Failed to screen importance for article: $title" }
            finalizeScreeningResult(
                startedAt = startedAt,
                inputChars = inputChars,
                outputChars = 0,
                score = 0.5f,
                status = "FAILED",
                errorMessage = e.message?.take(500)
            )
        }
    }

    /**
     * 기본 회귀 테스트에서 외부 Gemini API 호출을 차단한다.
     * 실제 API 검증은 Gradle `stressTest` 태스크가 real key를 주입할 때만 수행한다.
     */
    private fun isExternalAiDisabledForTest(): Boolean = googleApiKey.trim() == "test-key"

    /**
     * 스크리닝 결과를 표준 포맷으로 정리하고 공통 메트릭을 기록한다.
     */
    private fun finalizeScreeningResult(
        startedAt: Instant,
        inputChars: Int,
        outputChars: Int,
        score: Float,
        status: String,
        errorMessage: String? = null
    ): ImportanceScreeningResult {
        val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
        metrics.recordSummarizationCall(
            mode = "screening",
            success = status == "SUCCEEDED",
            durationMs = durationMs,
            inputChars = inputChars,
            outputChars = outputChars
        )
        return ImportanceScreeningResult(
            score = score,
            status = status,
            inputChars = inputChars,
            outputChars = outputChars,
            durationMs = durationMs,
            errorMessage = errorMessage
        )
    }

    /** ChatResponse 메타데이터에서 실제 토큰 사용량을 추출한다. 메타데이터가 없으면 null을 반환한다. */
    private fun extractTokenUsage(chatResponse: ChatResponse?): LlmTokenUsage? {
        val usage = chatResponse?.metadata?.usage ?: return null
        val promptTokens = usage.promptTokens.takeIf { it > 0 }
        val completionTokens = usage.completionTokens.takeIf { it > 0 }
        if (promptTokens == null && completionTokens == null) return null
        return LlmTokenUsage(promptTokens = promptTokens, completionTokens = completionTokens)
    }

    /**
     * Gemini 응답의 finish_reason을 확인하여 토큰 제한으로 잘렸는지 감지한다.
     * Spring AI의 ChatResponse 메타데이터에서 finishReason을 추출하고,
     * LENGTH/MAX_TOKENS이면 GeminiTruncationException을 던진다.
     */
    private fun detectTruncation(chatResponse: ChatResponse?, context: String) {
        val result = chatResponse?.result ?: return
        val finishReason = result.metadata.finishReason?.uppercase() ?: return
        if (finishReason == "LENGTH" || finishReason == "MAX_TOKENS") {
            throw GeminiTruncationException(
                "Gemini 응답이 토큰 제한으로 잘림 (finish_reason=$finishReason, context=$context)"
            )
        }
    }

    private fun extractJson(text: String): String? {
        // Gemini가 ```json ... ``` 블록으로 감싸서 응답하는 경우를 처리한다.
        val stripped = text.trim()
            .removePrefix("```json").removePrefix("```JSON")
            .removePrefix("```").removeSuffix("```")
            .trim()

        // { 부터 마지막 } 까지 추출한다.
        val start = stripped.indexOf('{')
        if (start < 0) {
            log.warn { "Gemini 응답에 JSON 객체가 없음 (length=${stripped.length}): ${stripped.take(100)}" }
            return null
        }
        val end = stripped.lastIndexOf('}')
        val candidate = if (end > start) {
            stripped.substring(start, end + 1)
        } else {
            // 닫는 }가 없으면 잘린 JSON — 복구를 시도한다
            stripped.substring(start)
        }

        // JSON 완결성을 검증한다: 중괄호/따옴표 짝이 맞는지 확인
        if (!isJsonStructurallyComplete(candidate)) {
            log.warn { "Gemini 응답 JSON이 불완전함 — 복구를 시도한다 (length=${candidate.length})" }
            val repaired = repairTruncatedJson(candidate)
            if (repaired != null) {
                log.info { "잘린 JSON 복구 성공 (원본=${candidate.length}, 복구=${repaired.length})" }
                return repaired
            }
            log.warn { "잘린 JSON 복구 실패: ${candidate.takeLast(80)}" }
            return null
        }

        return candidate
    }

    /**
     * JSON 문자열이 구조적으로 완결되었는지 간이 검증한다.
     * 중괄호/대괄호 짝과 문자열 따옴표 닫힘을 확인한다.
     */
    private fun isJsonStructurallyComplete(json: String): Boolean {
        var braceDepth = 0
        var bracketDepth = 0
        var inString = false
        var escape = false
        for (ch in json) {
            if (escape) { escape = false; continue }
            if (ch == '\\' && inString) { escape = true; continue }
            if (ch == '"') { inString = !inString; continue }
            if (inString) continue
            when (ch) {
                '{' -> braceDepth++
                '}' -> braceDepth--
                '[' -> bracketDepth++
                ']' -> bracketDepth--
            }
        }
        return !inString && braceDepth == 0 && bracketDepth == 0
    }

    /**
     * Gemini가 잘라서 보낸 불완전한 JSON을 복구한다.
     * 열린 문자열을 닫고, 열린 중괄호/대괄호를 닫아 유효한 JSON으로 만든다.
     * 복구 후에도 파싱이 안 되면 null을 반환한다.
     */
    private fun repairTruncatedJson(json: String): String? {
        val sb = StringBuilder(json)

        // 1) 열린 문자열을 닫는다 — 마지막 이스케이프되지 않은 따옴표를 추적
        var inString = false
        var escape = false
        for (ch in sb) {
            if (escape) { escape = false; continue }
            if (ch == '\\' && inString) { escape = true; continue }
            if (ch == '"') inString = !inString
        }
        if (inString) sb.append('"')

        // 2) 열린 중괄호/대괄호를 역순으로 닫는다
        val stack = mutableListOf<Char>()
        inString = false
        escape = false
        for (ch in sb) {
            if (escape) { escape = false; continue }
            if (ch == '\\' && inString) { escape = true; continue }
            if (ch == '"') { inString = !inString; continue }
            if (inString) continue
            when (ch) {
                '{' -> stack.add('}')
                '[' -> stack.add(']')
                '}', ']' -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            }
        }
        for (closer in stack.reversed()) sb.append(closer)

        // 3) 복구된 JSON이 파싱 가능한지 확인한다
        return try {
            mapper.readTree(sb.toString())
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * JSON 파싱을 시도하고, 이모지/surrogate 문자로 실패하면 해당 문자를 제거 후 재시도한다.
     * Gemini가 JSON 문자열 값 내부에 이모지(🔍, 📊 등)를 포함시켜 Jackson 파서가
     * surrogate pair를 처리하지 못하는 문제를 방어한다.
     *
     * JsonEOFException(잘린 JSON)은 이모지 문제와 다르므로 별도 GeminiTruncationException으로 래핑한다.
     */
    private inline fun <reified T> parseJsonWithEmojiRetry(json: String): T {
        return try {
            mapper.readValue<T>(json)
        } catch (e: com.fasterxml.jackson.core.io.JsonEOFException) {
            // JSON이 중간에 잘린 경우 — 이모지 제거로는 해결 안 됨
            throw GeminiTruncationException("Gemini 응답 JSON이 잘림 (EOF): ${e.message?.take(200)}")
        } catch (e: com.fasterxml.jackson.core.JsonParseException) {
            // surrogate/이모지 관련 파싱 에러면 이모지를 유니코드 이스케이프로 치환 후 재시도
            val cleaned = json.replace(Regex("[\\ud800-\\udfff]"), "")
                .replace(Regex("[^\\x00-\\xFFFF]"), "")
            log.debug { "JSON 파싱 실패 — 이모지 제거 후 재시도 (원본 길이=${json.length}, 정리 후=${cleaned.length})" }
            mapper.readValue<T>(cleaned)
        }
    }

    /**
     * 커스텀 페르소나의 system prompt에 역할 정의·한국어 강제·할루시네이션 방지를 추가한다.
     * 프리셋 페르소나는 이미 완전한 프롬프트를 갖고 있으므로 그대로 사용한다.
     */
    private fun effectiveSystemPrompt(persona: Persona): String {
        if (persona.isPreset) return persona.systemPrompt

        return """
            당신은 뉴스 기사를 요약하는 전문 에디터입니다.
            아래 지시사항에 따라 기사를 요약하세요. 반드시 한국어로 작성하세요.
            기사 본문에 없는 사실을 추가하지 마세요.
            원문의 직접 인용을 최소화하고, 핵심 사실만 요약하세요. 원문의 독자적 분석이나 논조를 재현하지 마세요.

            ${persona.systemPrompt}
        """.trimIndent()
    }

    private fun stripTranslationNoise(text: String): String {
        val trimmed = text
            .replace(Regex("(?s)^```[a-zA-Z0-9_-]*\\s*|\\s*```$"), "")
            .trim()

        // Gemini가 "번역문만 출력" 지시를 무시하고 JSON 객체로 감싸 반환한 경우를 복구한다.
        // 예: {"translation": "..."}, {"translatedText": "..."}, {"result": "..."}
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            unwrapJsonTranslation(trimmed)?.let { return it }
        }

        return if (trimmed.length >= 2 &&
            ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                    (trimmed.startsWith("'") && trimmed.endsWith("'"))
            )
        ) {
            trimmed.substring(1, trimmed.lastIndex)
        } else {
            trimmed
        }
    }

    /**
     * 번역 JSON 래퍼를 언래핑한다.
     *
     * 선택 우선순위:
     * 1) 번역 결과를 담을 가능성이 높은 키(translation, translatedText 등)가 있으면 그 값.
     * 2) 문자열 필드가 정확히 하나뿐이면 그 값.
     * 3) 그 외에는 null을 반환해 원문 그대로 두게 한다.
     */
    private fun unwrapJsonTranslation(json: String): String? = try {
        val tree = mapper.readTree(json)
        if (!tree.isObject) {
            null
        } else {
            // 문자열 필드만 추려 번역 결과 후보로 본다.
            val stringFields = tree.properties().asSequence()
                .filter { it.value.isTextual }
                .toList()
            val preferred = stringFields.firstOrNull { it.key.lowercase() in TRANSLATION_KEYS }
            val selected = preferred ?: stringFields.singleOrNull()
            selected?.value?.asText()?.trim()?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        private val TRANSLATION_KEYS = setOf(
            "translation", "translated", "translatedtext", "translatedtitle",
            "result", "output", "text", "answer", "ko", "korean"
        )
    }
}
