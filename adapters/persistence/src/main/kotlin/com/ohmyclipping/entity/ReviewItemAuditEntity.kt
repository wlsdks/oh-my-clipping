package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 요약 기사 검토 결정 변경 감사 이력 엔티티.
 * clipping_review_item_audits 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_review_item_audits")
class ReviewItemAuditEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "summary_id", length = 36, nullable = false)
    val summaryId: String = "",

    @Column(name = "category_id", length = 36, nullable = false)
    val categoryId: String = "",

    @Column(name = "from_status", length = 20)
    val fromStatus: String? = null,

    @Column(name = "to_status", length = 20, nullable = false)
    val toStatus: String = "",

    @Column(columnDefinition = "TEXT")
    val reason: String? = null,

    @Column(name = "reviewed_by", length = 100)
    val reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    val reviewedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
