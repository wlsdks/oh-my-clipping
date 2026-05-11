package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 원문 콘텐츠 마크다운 저장 엔티티.
 * original_contents 테이블에 매핑된다.
 */
@Entity
@Table(name = "original_contents")
class OriginalContentEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "rss_item_id", length = 36, nullable = false, unique = true)
    val rssItemId: String = "",

    @Column(name = "source_link", length = 2000, nullable = false)
    var sourceLink: String = "",

    @Column(length = 1000, nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT", nullable = false)
    var markdown: String = "",

    @Column(name = "content_hash", length = 64)
    var contentHash: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
