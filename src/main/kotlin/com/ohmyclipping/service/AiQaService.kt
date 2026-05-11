package com.ohmyclipping.service

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.service.dto.AiQaRelatedArticle
import com.ohmyclipping.service.dto.AiQaRequest
import com.ohmyclipping.service.dto.AiQaResponse
import com.ohmyclipping.store.BatchSummaryStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger {}

/**
 * 뉴스 기사 기반 AI Q&A 서비스.
 * 수집된 기사를 컨텍스트로 제공하고 사용자 질문에 대해 AI 답변을 생성한다.
 */
@Service
class AiQaService(
    @param:Lazy private val chatClient: ChatClient,
    private val batchSummaryStore: BatchSummaryStore,
) {
    private val mapper = jacksonObjectMapper()

    companion object {
        private const val MAX_CONTEXT_ARTICLES = 30
        private const val MAX_CONTEXT_CHARS = 8000
    }

    /**
     * 사용자 질문에 대해 수집된 기사를 컨텍스트로 AI 답변을 생성한다.
     *
     * @param request 질문, 조회 기간(1~90일), 카테고리 필터
     * @return AI 답변과 관련 기사 목록
     * @throws IllegalArgumentException 질문이 빈 문자열이거나 기간이 범위 밖일 때
     */
    fun ask(request: AiQaRequest): AiQaResponse {
        // 입력 유효성 검증
        ensureValid(request.question.isNotBlank()) { "질문을 입력해 주세요." }
        ensureValid(request.days in 1..90) {
            "조회 기간은 1~90일 이내여야 합니다."
        }

        // 기간 내 기사 조회
        val articles = loadArticles(request)
        if (articles.isEmpty()) {
            return emptyArticlesResponse(request.question)
        }

        // 컨텍스트 구성 및 AI 호출
        val context = buildContext(articles)
        val rawResponse = callAi(request.question, context)
            ?: return fallbackResponse(request.question, articles.size)

        // 응답 파싱
        return parseResponse(rawResponse, request.question, articles)
    }

    /** 기간·카테고리 조건으로 기사를 조회하고 중요도 순으로 정렬한다. */
    private fun loadArticles(request: AiQaRequest): List<BatchSummary> {
        val to = Instant.now()
        val from = to.minus(request.days.toLong(), ChronoUnit.DAYS)
        val catIdStr = request.categoryId?.toString()
        return batchSummaryStore.findByDateRange(from, to, catIdStr)
            .sortedByDescending { it.importanceScore }
            .take(MAX_CONTEXT_ARTICLES)
    }

    /** 기사 목록을 텍스트 컨텍스트로 변환한다. 최대 글자수를 초과하면 절단한다. */
    private fun buildContext(articles: List<BatchSummary>): String {
        val sb = StringBuilder()
        for ((i, a) in articles.withIndex()) {
            val entry = buildString {
                append("[${i + 1}] ${a.translatedTitle ?: a.originalTitle}\n")
                append("요약: ${a.summary}\n")
                append("키워드: ${a.keywords.joinToString()}\n")
                append("논조: ${a.sentiment ?: "미분류"}\n---\n")
            }
            if (sb.length + entry.length > MAX_CONTEXT_CHARS) break
            sb.append(entry)
        }
        return sb.toString()
    }

    /** ChatClient를 호출하여 AI 응답 텍스트를 받는다. */
    private fun callAi(question: String, context: String): String? {
        return try {
            chatClient.prompt()
                .system(buildSystemPrompt())
                .user(buildUserPrompt(question, context))
                .call()
                .content()
        } catch (e: Exception) {
            log.warn(e) { "AI Q&A 호출 실패: $question" }
            null
        }
    }

    private fun buildSystemPrompt(): String = """
        당신은 뉴스 분석 AI 어시스턴트입니다.
        제공된 기사 데이터를 기반으로 사용자의 질문에 정확하고 통찰력 있게 답변합니다.
        답변은 반드시 제공된 기사 데이터에 근거해야 합니다.
        추측이나 외부 지식은 사용하지 마세요.

        응답은 반드시 아래 JSON 형식으로 작성하세요:
        {
          "answer": "답변 내용 (마크다운 지원)",
          "relatedArticleIndices": [1, 3, 5],
          "relevanceReasons": ["이유1", "이유2", "이유3"]
        }
    """.trimIndent()

    private fun buildUserPrompt(
        question: String,
        context: String,
    ): String = """
        ## 수집된 기사 데이터

        $context

        ## 질문
        $question
    """.trimIndent()

    /** AI 원본 응답을 파싱하여 구조화된 응답으로 변환한다. */
    private fun parseResponse(
        raw: String,
        question: String,
        articles: List<BatchSummary>,
    ): AiQaResponse {
        return try {
            val jsonStr = extractJson(raw)
            val parsed = mapper.readValue<AiQaParsedResponse>(jsonStr)

            // 인덱스 기반으로 관련 기사 매핑
            val relatedArticles = parsed.relatedArticleIndices
                .mapIndexedNotNull { i, idx ->
                    val article = articles.getOrNull(idx - 1)
                        ?: return@mapIndexedNotNull null
                    val reason = parsed.relevanceReasons
                        .getOrElse(i) { "관련 기사" }
                    AiQaRelatedArticle(
                        summaryId = article.id,
                        title = article.translatedTitle
                            ?: article.originalTitle,
                        sourceLink = article.sourceLink,
                        relevanceReason = reason,
                    )
                }

            AiQaResponse(
                question = question,
                answer = parsed.answer,
                relatedArticles = relatedArticles,
                contextArticleCount = articles.size,
            )
        } catch (_: Exception) {
            // JSON 파싱 실패 시 원본 텍스트를 답변으로 사용
            AiQaResponse(
                question = question,
                answer = raw.take(2000),
                relatedArticles = emptyList(),
                contextArticleCount = articles.size,
            )
        }
    }

    /** 텍스트에서 첫 JSON 객체를 추출한다. */
    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < 0 || end <= start) {
            throw InvalidInputException("AI 응답에서 JSON을 찾을 수 없습니다")
        }
        return text.substring(start, end + 1)
    }

    /** 기사가 없을 때 기본 응답을 반환한다. */
    private fun emptyArticlesResponse(question: String) = AiQaResponse(
        question = question,
        answer = "해당 기간에 수집된 기사가 없어 답변을 생성할 수 없습니다.",
        relatedArticles = emptyList(),
        contextArticleCount = 0,
    )

    /** AI 응답이 null일 때 폴백 응답을 반환한다. */
    private fun fallbackResponse(
        question: String,
        articleCount: Int,
    ) = AiQaResponse(
        question = question,
        answer = "AI 응답을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        relatedArticles = emptyList(),
        contextArticleCount = articleCount,
    )
}

/** AI 응답 파싱용 내부 DTO */
private data class AiQaParsedResponse(
    val answer: String = "",
    val relatedArticleIndices: List<Int> = emptyList(),
    val relevanceReasons: List<String> = emptyList(),
)
