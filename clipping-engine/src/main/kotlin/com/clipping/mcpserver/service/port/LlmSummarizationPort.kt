package com.clipping.mcpserver.service.port

data class LlmTokenUsage(
    val promptTokens: Int?,
    val completionTokens: Int?
)

data class ImportanceScreeningResult(
    val score: Float,
    val status: String,
    val inputChars: Int,
    val outputChars: Int,
    val durationMs: Long,
    val errorMessage: String? = null
) {
    val success: Boolean
        get() = status == "SUCCEEDED"
}

data class CompetitorWeeklyInsight(
    val competitorHighlights: List<CompetitorHighlight>,
    val weeklyInsight: String
)

data class CompetitorHighlight(
    val name: String,
    val highlight: String
)

enum class LlmArticleLanguage {
    KOREAN,
    FOREIGN
}

data class LlmPersona(
    val id: String,
    val name: String,
    val description: String? = null,
    val systemPrompt: String,
    val summaryStyle: String? = null,
    val targetAudience: String? = null,
    val maxItems: Int = 5,
    val language: String = "ko",
    val isActive: Boolean = true,
    val isPreset: Boolean = false,
    val currentVersion: Int = 1,
    val tone: String? = null,
    val lengthPref: String? = null
)

data class LlmArticleSummaryResult(
    val translatedTitle: String?,
    val summary: String,
    val keywords: List<String>,
    val importanceScore: Float = 0f,
    val sentiment: String? = null,
    val eventType: String? = null
)

data class LlmDailySummaryResult(
    val title: String,
    val topicKeywords: List<String> = emptyList()
)

data class LlmCompetitorTimelineItem(
    val summaryId: String,
    val competitorId: String,
    val competitorName: String,
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val sourceLink: String,
    val importanceScore: Float,
    val eventType: String?,
    val sentiment: String?,
    val createdAt: String
)

interface LlmSummarizationPort {
    fun getLastTokenUsage(): LlmTokenUsage?

    fun getLastRejectReason(): String?

    fun summarizeArticle(
        title: String,
        content: String,
        language: LlmArticleLanguage,
        persona: LlmPersona? = null
    ): LlmArticleSummaryResult?

    fun generateDailySummary(
        categoryName: String,
        summariesText: String,
        totalItems: Int
    ): LlmDailySummaryResult?

    fun summarizeCompetitorWeekly(
        articlesByCompetitor: Map<String, List<LlmCompetitorTimelineItem>>,
        periodLabel: String
    ): CompetitorWeeklyInsight?

    fun translateToKorean(text: String, context: String): String?

    fun screenImportance(title: String, contentPreview: String): ImportanceScreeningResult
}
