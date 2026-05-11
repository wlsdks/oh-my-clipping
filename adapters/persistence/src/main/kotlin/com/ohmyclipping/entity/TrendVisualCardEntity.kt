package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 트렌드 비주얼 카드 엔티티.
 * clipping_trend_visual_cards 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_trend_visual_cards")
class TrendVisualCardEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "snapshot_id", length = 36, nullable = false)
    var snapshotId: String = "",

    @Column(name = "card_type", length = 20, nullable = false)
    var cardType: String = "",

    @Column(length = 300, nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT", nullable = false)
    var summary: String = "",

    @Column(columnDefinition = "TEXT", nullable = false)
    var panels: String = "",

    @Column(name = "review_status", length = 20, nullable = false)
    var reviewStatus: String = "PENDING",

    @Column(name = "review_note", columnDefinition = "TEXT")
    var reviewNote: String? = null,

    @Column(name = "generated_by", length = 100)
    var generatedBy: String? = null,

    @Column(name = "reviewed_by", length = 100)
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(nullable = false)
    var published: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
