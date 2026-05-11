package com.ohmyclipping.service.dto

/**
 * 소스 분석(analytics) 응답 DTO.
 * 지정 기간(days) 동안의 수집 통계와 일별 기사 수를 반환한다.
 */
data class SourceAnalyticsResponse(
    val sourceId: String,
    val sourceName: String,
    val days: Int,
    val totalArticles: Int,
    val avgArticlesPerDay: Double,
    val reliabilityScore: Int,
    val lastSuccessAt: String?,
    val crawlFailCount: Int,
    val dailyArticleCounts: List<DailyArticleCount>
)

/**
 * 일별 기사 수집 건수.
 */
data class DailyArticleCount(
    val date: String,
    val count: Int
)

/**
 * 소스 크롤 이력 응답 DTO.
 * 가동률, 평균 응답시간, 성공/실패 통계, 최근 로그를 포함한다.
 */
data class CrawlHistoryResponse(
    val sourceId: String,
    val uptimePercent: Double?,
    val avgResponseTimeMs: Int?,
    val totalCrawls: Int,
    val successCount: Int,
    val failCount: Int,
    val logs: List<CrawlLogEntry>
)

/**
 * 개별 크롤 로그 항목.
 */
data class CrawlLogEntry(
    val crawledAt: String,
    val success: Boolean,
    val articlesFound: Int,
    val responseTimeMs: Int?,
    val errorMessage: String? = null
)

/**
 * 소스별 AI 비용 통계 응답 DTO.
 */
data class SourceAiCostsResponse(
    val costs: Map<String, SourceAiCostEntry>,
    val days: Int
)

/**
 * 개별 소스의 AI 비용 항목.
 */
data class SourceAiCostEntry(
    val requestCount: Int,
    val tokensIn: Long,
    val tokensOut: Long,
    val estimatedUsd: Double
)

/**
 * 소스 벌크 액션 응답 DTO.
 * 각 소스별 처리 결과를 개별 리포트한다.
 */
data class BulkSourceActionResponse(
    val successCount: Int,
    val failCount: Int,
    val results: Map<String, String>
)
