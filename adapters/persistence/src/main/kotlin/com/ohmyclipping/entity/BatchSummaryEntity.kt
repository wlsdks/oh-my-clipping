package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 기사 기반 요약 엔티티.
 * batch_summaries 테이블에 매핑된다.
 */
@Entity
@Table(name = "batch_summaries")
class BatchSummaryEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "original_title", length = 1000, nullable = false)
    var originalTitle: String = "",

    @Column(name = "translated_title", length = 1000)
    var translatedTitle: String? = null,

    @Column(columnDefinition = "TEXT", nullable = false)
    var summary: String = "",

    @Column(columnDefinition = "TEXT")
    var keywords: String? = null,

    @Column(columnDefinition = "TEXT")
    var insights: String? = null,

    @Column(name = "source_link", length = 2000, nullable = false)
    var sourceLink: String = "",

    @Column(name = "is_sent_to_slack", nullable = false)
    var isSentToSlack: Boolean = false,

    @Column(name = "importance_score", nullable = false)
    var importanceScore: Float = 0f,

    @Column(name = "category_id", length = 36, nullable = false)
    var categoryId: String = "",

    @Column(name = "rss_item_id", length = 36, nullable = true)
    var rssItemId: String? = null,

    @Column(length = 20)
    var sentiment: String? = null,

    @Column(name = "event_type", length = 30)
    var eventType: String? = null,

    @Column(name = "is_fallback", nullable = false)
    var isFallback: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
