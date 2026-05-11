package com.ohmyclipping.service.dto.analytics

/**
 * 논조(감성) 추이 조회 응답 DTO.
 * 일별 긍정/중립/부정 기사 건수와 요약 통계를 제공한다.
 */
data class SentimentTrendResponse(
    val period: KeywordTrendPeriod,
    val daily: List<SentimentDailyCount>,
    val summary: SentimentSummary,
)

/** 특정 날짜의 논조별 기사 건수. */
data class SentimentDailyCount(
    val date: String,
    val positive: Int,
    val neutral: Int,
    val negative: Int,
    val total: Int,
)

/** 조회 기간 전체의 논조 비율 요약. */
data class SentimentSummary(
    val positiveRate: Double,
    val neutralRate: Double,
    val negativeRate: Double,
    val dominantSentiment: String?,
    val changeFromPrevious: Double,
)
