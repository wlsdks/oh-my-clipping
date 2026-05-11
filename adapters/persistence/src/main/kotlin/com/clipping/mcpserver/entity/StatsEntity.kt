package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

/**
 * 카테고리별 일간 클리핑 통계 엔티티.
 * clipping_stats 테이블에 매핑된다.
 */
@Entity
@Table(
    name = "clipping_stats",
    uniqueConstraints = [UniqueConstraint(columnNames = ["category_id", "stat_date"])]
)
class StatsEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "category_id", length = 36, nullable = false)
    var categoryId: String = "",

    @Column(name = "stat_date", nullable = false)
    var statDate: LocalDate = LocalDate.now(),

    @Column(name = "items_collected", nullable = false)
    var itemsCollected: Int = 0,

    @Column(name = "items_duplicates", nullable = false)
    var itemsDuplicates: Int = 0,

    @Column(name = "items_summarized", nullable = false)
    var itemsSummarized: Int = 0,

    @Column(name = "items_sent", nullable = false)
    var itemsSent: Int = 0,

    @Column(name = "slack_send_attempts", nullable = false)
    var slackSendAttempts: Int = 0,

    @Column(name = "slack_send_successes", nullable = false)
    var slackSendSuccesses: Int = 0,

    @Column(name = "top_keywords", columnDefinition = "TEXT")
    var topKeywords: String? = null,

    @Column(name = "avg_importance_score", nullable = false)
    var avgImportanceScore: Float = 0f,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
