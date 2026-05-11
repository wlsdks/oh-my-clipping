package com.ohmyclipping.service.dto.user

/**
 * 히스토리 기사 조회 결과 뷰.
 */
data class ArticleHistoryItemView(
    val id: String,
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val importanceScore: Float,
    val sourceLink: String,
    val categoryId: String,
    val categoryName: String,
    val isBookmarked: Boolean,
    val createdAt: String
)

/**
 * 히스토리 기사 페이지 뷰.
 */
data class ArticleHistoryPageView(
    val items: List<ArticleHistoryItemView>,
    val page: Int,
    val size: Int,
    val totalCount: Int,
    val totalPages: Int
)

/**
 * 기사 상세 뷰 — 원문 마크다운과 연관 기사를 포함한다.
 * competitorName, eventType은 경쟁사 기사 전용 필드이며 구독뉴스에서는 null이다.
 */
data class ArticleDetailView(
    val id: String,
    val title: String,
    val summary: String,
    val insights: String?,
    val originalContent: String?,
    val keywords: List<String>,
    val importanceScore: Float,
    val sourceLink: String,
    val categoryId: String,
    val categoryName: String,
    val isBookmarked: Boolean,
    val createdAt: String,
    val relatedArticles: List<ArticleHistoryItemView>,
    val competitorName: String? = null,
    val eventType: String? = null
)
