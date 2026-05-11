package com.clipping.mcpserver.ai

import com.clipping.mcpserver.service.toCompetitorTimelineItem
import com.clipping.mcpserver.service.toLanguage
import com.clipping.mcpserver.service.toLlmArticleSummaryResult
import com.clipping.mcpserver.service.toLlmDailySummaryResult
import com.clipping.mcpserver.service.toPersona
import com.clipping.mcpserver.service.port.CompetitorWeeklyInsight
import com.clipping.mcpserver.service.port.ImportanceScreeningResult
import com.clipping.mcpserver.service.port.LlmArticleLanguage
import com.clipping.mcpserver.service.port.LlmArticleSummaryResult
import com.clipping.mcpserver.service.port.LlmCompetitorTimelineItem
import com.clipping.mcpserver.service.port.LlmDailySummaryResult
import com.clipping.mcpserver.service.port.LlmPersona
import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.service.port.LlmTokenUsage
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
