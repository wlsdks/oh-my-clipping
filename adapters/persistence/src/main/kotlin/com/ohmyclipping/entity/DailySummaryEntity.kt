package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

/**
 * 일간 종합 요약 엔티티.
 * daily_summaries 테이블에 매핑된다.
 */
@Entity
@Table(name = "daily_summaries")
class DailySummaryEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 500, nullable = false)
    var title: String = "",

    @Column(name = "total_items", nullable = false)
    var totalItems: Int = 0,

    @Column(name = "summary_date", nullable = false)
    var summaryDate: LocalDate = LocalDate.now(),

    @Column(name = "content_guides", columnDefinition = "TEXT")
    var contentGuides: String? = null,

    @Column(name = "overall_summary", columnDefinition = "TEXT")
    var overallSummary: String? = null,

    @Column(columnDefinition = "TEXT")
    var glossary: String? = null,

    @Column(name = "topic_keywords", columnDefinition = "TEXT")
    var topicKeywords: String? = null,

    @Column(name = "is_sent_to_slack", nullable = false)
    var isSentToSlack: Boolean = false,

    @Column(name = "category_id", length = 36, nullable = false)
    var categoryId: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
