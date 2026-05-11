package com.clipping.mcpserver.service.competitor

import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.service.toLlmArticleLanguage
import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.SummaryEnrichmentStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

/**
 * 경쟁사 기사에 대해 AI 요약을 수행하는 서비스.
 * 수집 시 RSS 원문(제목+설명)만 저장된 경쟁사 기사를
 * Gemini API로 요약하여 summary, keywords, insights, sentiment, eventType을 채운다.
 */
@Service
class CompetitorArticleSummarizationService(
    private val summaryEnrichmentStore: SummaryEnrichmentStore,
    private val rssItemStore: RssItemStore,
    private val clippingSummarizer: LlmSummarizationPort,
    private val metrics: ClippingMetrics
) {
    private val mapper = jacksonObjectMapper()

    companion object {
        /** AI 미요약 판정 기준: summary 길이가 이 값 이하이면 미요약으로 판단한다 */
        private const val MAX_UNSUMMARIZED_LENGTH = 200

        /** 한 번 실행에서 처리할 최대 기사 수 */
        private const val BATCH_LIMIT = 30
    }

    /**
     * AI 미요약 상태인 경쟁사 기사를 찾아 Gemini로 요약한다.
     *
     * @return 요약 성공 건수
     */
    fun summarizeUnsummarized(): Int {
        val unsummarized = summaryEnrichmentStore.findUnsummarizedCompetitorArticles(
            maxSummaryLength = MAX_UNSUMMARIZED_LENGTH,
            limit = BATCH_LIMIT
        )
        if (unsummarized.isEmpty()) {
            log.debug { "경쟁사 미요약 기사 없음 — 스킵" }
            return 0
        }

        log.info { "경쟁사 기사 요약 시작: ${unsummarized.size}건" }
        var successCount = 0

        for (article in unsummarized) {
            // 원본 RSS 콘텐츠를 가져온다
            val rssItem = rssItemStore.findById(article.rssItemId ?: continue)
            val content = rssItem?.content?.takeIf { it.isNotBlank() }
                ?: article.summary.takeIf { it.isNotBlank() }
                ?: continue

            runCatching {
                val aiResult = clippingSummarizer.summarizeArticle(
                    title = article.originalTitle,
                    content = content,
                    language = Language.KOREAN.toLlmArticleLanguage()
                ) ?: return@runCatching

                // 키워드를 JSON 배열로 직렬화한다
                val keywordsJson = if (aiResult.keywords.isNotEmpty()) {
                    mapper.writeValueAsString(aiResult.keywords)
                } else null

                // AI 요약 결과로 BatchSummary를 업데이트한다
                summaryEnrichmentStore.updateAiSummary(
                    id = article.id,
                    summary = aiResult.summary,
                    keywords = keywordsJson,
                    insights = null,
                    importanceScore = aiResult.importanceScore,
                    sentiment = aiResult.sentiment,
                    eventType = aiResult.eventType
                )
                successCount++
                log.debug { "경쟁사 기사 요약 완료: ${article.originalTitle}" }
            }.onFailure { e ->
                log.warn(e) { "경쟁사 기사 요약 실패: ${article.originalTitle}" }
            }
        }

        log.info { "경쟁사 기사 요약 완료: ${successCount}/${unsummarized.size}건 성공" }
        return successCount
    }
}
