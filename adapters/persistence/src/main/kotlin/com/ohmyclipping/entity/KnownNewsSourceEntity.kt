package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 주요 뉴스사이트 매핑 엔티티.
 * known_news_sources 테이블에 매핑된다.
 */
@Entity
@Table(name = "known_news_sources")
class KnownNewsSourceEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 200, nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT", nullable = false)
    var aliases: String = "[]",

    @Column(length = 200, nullable = false)
    var domain: String = "",

    @Column(name = "rss_url", length = 500, nullable = false)
    var rssUrl: String = "",

    @Column(length = 20, nullable = false)
    var region: String = "UNKNOWN",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
