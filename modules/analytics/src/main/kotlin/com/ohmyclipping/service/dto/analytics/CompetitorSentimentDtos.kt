package com.ohmyclipping.service.dto.analytics

/**
 * 경쟁사별 논조(긍정/중립/부정) 집계 항목.
 */
data class CompetitorSentimentItem(
    val competitorId: String,
    val competitorName: String,
    val positive: Int,
    val neutral: Int,
    val negative: Int,
    val total: Int,
    val positiveRate: Double,
)

/**
 * 경쟁사 논조 비교 API 응답.
 */
data class CompetitorSentimentResponse(
    val competitors: List<CompetitorSentimentItem>,
)
