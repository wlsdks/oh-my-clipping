package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * Slack 채널 일간 발송 카운트 복합키.
 */
data class SlackChannelDailySendCountId(
    val channelId: String = "",
    val sendDate: LocalDate = LocalDate.now()
) : Serializable

/**
 * Slack 채널별 일간 메시지 발송 횟수 엔티티.
 * slack_channel_daily_send_counts 테이블에 매핑된다.
 */
@Entity
@Table(name = "slack_channel_daily_send_counts")
@IdClass(SlackChannelDailySendCountId::class)
class SlackChannelDailySendCountEntity(
    @Id
    @Column(name = "channel_id", length = 100)
    val channelId: String = "",

    @Id
    @Column(name = "send_date")
    val sendDate: LocalDate = LocalDate.now(),

    @Column(name = "message_count", nullable = false)
    var messageCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
