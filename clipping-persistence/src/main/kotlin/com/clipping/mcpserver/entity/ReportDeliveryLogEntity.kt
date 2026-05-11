package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 리포트 발송 이력 엔티티.
 * report_delivery_log 테이블에 매핑된다.
 */
@Entity
@Table(
    name = "report_delivery_log",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["report_type", "period_key", "channel_id"])
    ]
)
class ReportDeliveryLogEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "report_type", length = 20, nullable = false)
    var reportType: String = "",

    @Column(name = "period_key", length = 20, nullable = false)
    var periodKey: String = "",

    @Column(name = "channel_id", length = 100, nullable = false)
    var channelId: String = "",

    @Column(length = 20, nullable = false)
    var status: String = "RESERVED",

    @Column(name = "snapshot_id", length = 36)
    var snapshotId: String? = null,

    @Column(name = "slack_message_ts", length = 50)
    var slackMessageTs: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,

    @Column(name = "items_processed")
    var itemsProcessed: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
