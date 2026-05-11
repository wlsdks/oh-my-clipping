package com.ohmyclipping.service.dto.analytics

/**
 * 중요도 기준 상위 기사 개별 항목.
 */
data class TopArticleItem(
    val summaryId: String,
    val title: String,
    val sourceLink: String,
    val importanceScore: Float,
    val keywords: List<String>,
    val sentiment: String?,
    val eventType: String?,
    val createdAt: String
)

/**
 * 중요도 기준 상위 기사 응답.
 */
data class TopArticlesResponse(
    val items: List<TopArticleItem>
)
