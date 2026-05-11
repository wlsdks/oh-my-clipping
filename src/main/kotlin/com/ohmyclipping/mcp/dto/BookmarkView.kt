package com.ohmyclipping.mcp.dto

import com.ohmyclipping.model.BookmarkedArticle

/**
 * MCP 응답용 북마크 요약 뷰.
 *
 * 북마크 시점의 스냅샷 기반으로 원본 요약이 purge 돼도 안정적으로 노출한다.
 */
data class BookmarkView(
    val summaryId: String,
    val categoryId: String,
    val title: String,
    val summary: String,
    val sourceLink: String,
    val importanceScore: Double,
    val articleCreatedAt: String,
    val bookmarkedAt: String,
) {
    companion object {
        fun from(article: BookmarkedArticle) = BookmarkView(
            summaryId = article.summaryId,
            categoryId = article.categoryId,
            title = article.translatedTitle ?: article.originalTitle,
            summary = article.summary,
            sourceLink = article.sourceLink,
            importanceScore = article.importanceScore.toDouble(),
            articleCreatedAt = article.articleCreatedAt.toString(),
            bookmarkedAt = article.bookmarkedAt.toString(),
        )
    }
}
