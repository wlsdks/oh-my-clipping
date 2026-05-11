package com.ohmyclipping.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 북마크된 기사의 스냅샷 엔티티.
 * 원본 batch_summaries가 retention으로 삭제돼도 북마크는 유지된다.
 */
@Entity
@Table(name = "bookmarked_articles")
class BookmarkedArticleEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "user_id", length = 120, nullable = false)
    val userId: String = "",

    @Column(name = "summary_id", length = 36, nullable = false)
    val summaryId: String = "",

    @Column(name = "original_title", length = 1000, nullable = false)
    val originalTitle: String = "",

    @Column(name = "translated_title", length = 1000)
    val translatedTitle: String? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    val summary: String = "",

    @Column(columnDefinition = "TEXT")
    val insights: String? = null,

    @Column(columnDefinition = "TEXT")
    val keywords: String? = null,

    @Column(name = "importance_score", nullable = false)
    val importanceScore: Float = 0f,

    @Column(name = "source_link", length = 2000, nullable = false)
    val sourceLink: String = "",

    @Column(name = "category_id", length = 36, nullable = false)
    val categoryId: String = "",

    @Column(length = 20)
    val sentiment: String? = null,

    @Column(name = "event_type", length = 30)
    val eventType: String? = null,

    @Column(name = "article_created_at", nullable = false)
    val articleCreatedAt: Instant = Instant.now(),

    @Column(name = "bookmarked_at", nullable = false)
    val bookmarkedAt: Instant = Instant.now()
)
