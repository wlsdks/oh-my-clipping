package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 경쟁사 워치리스트 엔티티.
 * competitor_watchlist 테이블에 매핑된다.
 */
@Entity
@Table(name = "competitor_watchlist")
class CompetitorWatchlistEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 100, nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT", nullable = false)
    var aliases: String = "[]",

    @Column(name = "exclude_keywords", columnDefinition = "TEXT", nullable = false)
    var excludeKeywords: String = "[]",

    @Column(length = 20, nullable = false)
    var tier: String = "DIRECT",

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
