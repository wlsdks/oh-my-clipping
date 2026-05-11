package com.ohmyclipping.repository

import com.ohmyclipping.entity.SlackChannelDailySendCountEntity
import com.ohmyclipping.entity.SlackChannelDailySendCountId
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SlackChannelDailySendCountRepository :
    JpaRepository<SlackChannelDailySendCountEntity, SlackChannelDailySendCountId> {
    fun findByChannelIdAndSendDate(channelId: String, sendDate: LocalDate): SlackChannelDailySendCountEntity?
}
