package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

/**
 * 카테고리-채널 단위 발송 이력 엔티티.
 * delivery_log 테이블에 매핑된다.
 */
@Entity
@Table(
    name = "delivery_log",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["category_id", "channel_id", "delivery_date", "delivery_hour"])
    ]
)
class DeliveryLogEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "category_id", length = 100, nullable = false)
    var categoryId: String = "",

    @Column(name = "channel_id", length = 100, nullable = false)
    var channelId: String = "",

    @Column(name = "delivery_date", nullable = false)
    var deliveryDate: LocalDate = LocalDate.now(),

    @Column(name = "delivery_hour", nullable = false)
    var deliveryHour: Int = 0,

    @Column(length = 20, nullable = false)
    var status: String = "RESERVED",

    @Column(name = "item_count", nullable = false)
    var itemCount: Int = 0,

    @Column(name = "slack_message_ts", length = 50)
    var slackMessageTs: String? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "next_retry_at")
    var nextRetryAt: Instant? = null,

    @Column(name = "claimed_at")
    var claimedAt: Instant? = null,

    @Column(name = "last_error", length = 500)
    var lastError: String? = null,

    @Column(name = "prepared_digest_json", columnDefinition = "TEXT")
    var preparedDigestJson: String? = null,

    @Column(name = "fallback_used", nullable = false)
    var fallbackUsed: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
