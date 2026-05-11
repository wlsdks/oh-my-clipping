package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 요약 기사 검토 결정 엔티티.
 * clipping_review_items 테이블에 매핑된다.
 * PK는 summary_id 단독이다.
 */
@Entity
@Table(name = "clipping_review_items")
class ReviewItemDecisionEntity(
    @Id
    @Column(name = "summary_id", length = 36)
    val summaryId: String = "",

    @Column(name = "category_id", length = 36, nullable = false)
    var categoryId: String = "",

    @Column(length = 20, nullable = false)
    var status: String = "REVIEW",

    @Column(name = "suggested_status", length = 20)
    var suggestedStatus: String? = null,

    @Column(columnDefinition = "TEXT")
    var reason: String? = null,

    @Column(name = "reviewed_by", length = 100)
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
