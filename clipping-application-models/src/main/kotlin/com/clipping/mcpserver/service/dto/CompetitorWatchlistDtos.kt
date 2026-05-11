package com.clipping.mcpserver.service.dto

/**
 * 경쟁사 워치리스트 CRUD 응답 DTO.
 */
data class CompetitorResponse(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val excludeKeywords: List<String> = emptyList(),
    val tier: String,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val rssFeeds: List<CompetitorRssFeedResponse> = emptyList(),
    val articleCount: Long = 0,
    val last24hCount: Long = 0
)

/**
 * 경쟁사에 등록된 RSS 피드 응답.
 */
data class CompetitorRssFeedResponse(
    val id: String,
    val feedUrl: String,
    val label: String?
)

/**
 * 경쟁사 타임라인 개별 항목.
 */
data class CompetitorTimelineItem(
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

/**
 * 경쟁사 타임라인 응답.
 */
data class CompetitorTimelineResponse(val items: List<CompetitorTimelineItem>)

/**
 * Share of Voice 개별 점유율 항목.
 * prevCount/prevShare는 이전 기간 수치, shareDelta는 퍼센트 포인트 차이(current - prev).
 */
data class SovShareItem(
    val competitorId: String?,
    val name: String,
    val count: Int,
    val share: Double,
    val prevCount: Int? = null,
    val prevShare: Double? = null,
    val shareDelta: Double? = null
)

/**
 * Share of Voice 조회 기간.
 */
data class SovPeriod(val from: String, val to: String)

/**
 * Share of Voice 응답.
 */
data class SovResponse(
    val period: SovPeriod,
    val totalArticles: Int,
    val shares: List<SovShareItem>
)

/**
 * 키워드 프리뷰 요청.
 */
data class KeywordPreviewRequest(val keywords: List<String>)

/**
 * 키워드 프리뷰 개별 항목.
 */
data class KeywordPreviewItem(
    val title: String,
    val link: String,
    val publishedAt: String?
)

/**
 * 키워드 프리뷰 응답.
 */
data class KeywordPreviewResponse(
    val items: List<KeywordPreviewItem>,
    val message: String
)

/**
 * RSS 피드 입력 DTO.
 */
data class RssFeedInput(
    val url: String,
    val label: String? = null
)
