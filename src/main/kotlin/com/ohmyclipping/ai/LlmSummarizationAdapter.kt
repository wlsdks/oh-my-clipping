package com.ohmyclipping.ai

import com.ohmyclipping.service.toCompetitorTimelineItem
import com.ohmyclipping.service.toLanguage
import com.ohmyclipping.service.toLlmArticleSummaryResult
import com.ohmyclipping.service.toLlmDailySummaryResult
import com.ohmyclipping.service.toPersona
import com.ohmyclipping.service.port.CompetitorWeeklyInsight
import com.ohmyclipping.service.port.ImportanceScreeningResult
import com.ohmyclipping.service.port.LlmArticleLanguage
import com.ohmyclipping.service.port.LlmArticleSummaryResult
import com.ohmyclipping.service.port.LlmCompetitorTimelineItem
import com.ohmyclipping.service.port.LlmDailySummaryResult
import com.ohmyclipping.service.port.LlmPersona
import com.ohmyclipping.service.port.LlmSummarizationPort
import com.ohmyclipping.service.port.LlmTokenUsage
import org.springframework.stereotype.Component

@Component
class LlmSummarizationAdapter(
    private val clippingSummarizer: ClippingSummarizer
) : LlmSummarizationPort {

    override fun getLastTokenUsage(): LlmTokenUsage? =
        clippingSummarizer.getLastTokenUsage()

    override fun getLastRejectReason(): String? =
        clippingSummarizer.getLastRejectReason()

    override fun summarizeArticle(
        title: String,
        content: String,
        language: LlmArticleLanguage,
        persona: LlmPersona?
    ): LlmArticleSummaryResult? =
        clippingSummarizer
            .summarizeArticle(title, content, language.toLanguage(), persona?.toPersona())
            ?.toLlmArticleSummaryResult()

    override fun generateDailySummary(
        categoryName: String,
        summariesText: String,
        totalItems: Int
    ): LlmDailySummaryResult? =
        clippingSummarizer
            .generateDailySummary(categoryName, summariesText, totalItems)
            ?.toLlmDailySummaryResult()

    override fun summarizeCompetitorWeekly(
        articlesByCompetitor: Map<String, List<LlmCompetitorTimelineItem>>,
        periodLabel: String
    ): CompetitorWeeklyInsight? =
        clippingSummarizer.summarizeCompetitorWeekly(
            articlesByCompetitor = articlesByCompetitor.mapValues { (_, items) ->
                items.map { it.toCompetitorTimelineItem() }
            },
            periodLabel = periodLabel
        )

    override fun translateToKorean(text: String, context: String): String? =
        clippingSummarizer.translateToKorean(text, context)

    override fun screenImportance(title: String, contentPreview: String): ImportanceScreeningResult =
        clippingSummarizer.screenImportance(title, contentPreview)
}
