package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 수집된 RSS 기사 엔티티.
 * rss_items 테이블에 매핑된다.
 */
@Entity
@Table(name = "rss_items")
class RssItemEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 1000, nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    @Column(length = 2000, nullable = false)
    var link: String = "",

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(length = 20, nullable = false)
    var language: String = "FOREIGN",

    @Column(name = "is_processed", nullable = false)
    var isProcessed: Boolean = false,

    @Column(name = "category_id", length = 36, nullable = false)
    var categoryId: String = "",

    @Column(name = "rss_source_id", length = 36)
    var rssSourceId: String? = null,

    @Column(name = "screened_score")
    var screenedScore: Float? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
