package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

/**
 * 트렌드 스냅샷 엔티티.
 * clipping_trend_snapshots 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_trend_snapshots")
class TrendSnapshotEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "period_type", length = 20, nullable = false)
    var periodType: String = "",

    @Column(name = "snapshot_from", nullable = false)
    var snapshotFrom: LocalDate = LocalDate.now(),

    @Column(name = "snapshot_to", nullable = false)
    var snapshotTo: LocalDate = LocalDate.now(),

    @Column(name = "category_id", length = 36)
    var categoryId: String? = null,

    @Column(name = "category_name", length = 120, nullable = false)
    var categoryName: String = "",

    @Column(name = "region_type", length = 20, nullable = false)
    var regionType: String = "ALL",

    @Column(length = 300, nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT", nullable = false)
    var summary: String = "",

    @Column(name = "key_signals", columnDefinition = "TEXT", nullable = false)
    var keySignals: String = "",

    @Column(name = "action_items", columnDefinition = "TEXT", nullable = false)
    var actionItems: String = "",

    @Column(name = "source_count", nullable = false)
    var sourceCount: Int = 0,

    @Column(name = "item_count", nullable = false)
    var itemCount: Int = 0,

    @Column(length = 20, nullable = false)
    var status: String = "DRAFT",

    @Column(name = "template_type", length = 30)
    var templateType: String? = "DETAILED",

    @Column(name = "generated_by", length = 100)
    var generatedBy: String? = null,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
