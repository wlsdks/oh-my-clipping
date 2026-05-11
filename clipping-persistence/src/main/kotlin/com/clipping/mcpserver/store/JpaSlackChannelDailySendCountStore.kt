package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.SlackChannelDailySendCountEntity
import com.clipping.mcpserver.repository.SlackChannelDailySendCountRepository
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * Slack 채널 일간 발송 카운트 JPA 구현. JdbcSlackChannelDailySendCountStore를 대체한다.
 * 동시성 제어가 필요한 reserveSlot은 원자적 UPDATE를 위해 JdbcTemplate을 병행한다.
 */
@Repository
@Primary
class JpaSlackChannelDailySendCountStore(
    private val repository: SlackChannelDailySendCountRepository,
    private val jdbc: JdbcTemplate
) : SlackChannelDailySendCountStore {

    /**
     * 채널 일간 발송 슬롯을 예약한다.
     * 동시성 안전을 위해 원자적 UPDATE 쿼리를 사용한다.
     */
    override fun reserveSlot(channelId: String, sendDate: LocalDate, dailyLimit: Int): SlackChannelSendReservation {
        val trimmedChannel = channelId.trim()
        require(trimmedChannel.isNotBlank()) { "channelId is required" }
        require(dailyLimit >= 1) { "dailyLimit must be greater than 0" }
        val now = Instant.now()

        // 현재 카운트를 확인한다.
        val current = getCount(trimmedChannel, sendDate)
        if (current >= dailyLimit) {
            return SlackChannelSendReservation(current, false)
        }

        // 원자적 UPDATE: message_count < dailyLimit 조건으로 동시성을 제어한다.
        val sqlDate = Date.valueOf(sendDate)
        val tsNow = Timestamp.from(now)
        val updated = jdbc.update(
            """
            UPDATE slack_channel_daily_send_counts
            SET message_count = message_count + 1,
                updated_at = ?
            WHERE channel_id = ? AND send_date = ? AND message_count < ?
            """.trimIndent(),
            tsNow, trimmedChannel, sqlDate, dailyLimit
        )
        if (updated > 0) {
            return SlackChannelSendReservation(getCount(trimmedChannel, sendDate), true)
        }

        // 행이 없으면 새로 생성한다.
        val existing = repository.findByChannelIdAndSendDate(trimmedChannel, sendDate)
        if (existing == null) {
            repository.save(
                SlackChannelDailySendCountEntity(
                    channelId = trimmedChannel,
                    sendDate = sendDate,
                    messageCount = 1,
                    createdAt = now,
                    updatedAt = now
                )
            )
            return SlackChannelSendReservation(1, true)
        }

        // 동시 생성 후 재시도한다.
        val secondUpdated = jdbc.update(
            """
            UPDATE slack_channel_daily_send_counts
            SET message_count = message_count + 1,
                updated_at = ?
            WHERE channel_id = ? AND send_date = ? AND message_count < ?
            """.trimIndent(),
            tsNow, trimmedChannel, sqlDate, dailyLimit
        )
        if (secondUpdated > 0) {
            return SlackChannelSendReservation(getCount(trimmedChannel, sendDate), true)
        }

        return SlackChannelSendReservation(getCount(trimmedChannel, sendDate), false)
    }

    override fun releaseSlot(channelId: String, sendDate: LocalDate) {
        val trimmedChannel = channelId.trim()
        if (trimmedChannel.isBlank()) return
        // GREATEST를 사용한 원자적 감소를 위해 JdbcTemplate을 사용한다.
        jdbc.update(
            """
            UPDATE slack_channel_daily_send_counts
            SET message_count = GREATEST(message_count - 1, 0),
                updated_at = ?
            WHERE channel_id = ? AND send_date = ? AND message_count > 0
            """.trimIndent(),
            Timestamp.from(Instant.now()),
            trimmedChannel,
            Date.valueOf(sendDate)
        )
    }

    private fun getCount(channelId: String, sendDate: LocalDate): Int {
        val entity = repository.findByChannelIdAndSendDate(channelId, sendDate)
        return entity?.messageCount ?: 0
    }
}
