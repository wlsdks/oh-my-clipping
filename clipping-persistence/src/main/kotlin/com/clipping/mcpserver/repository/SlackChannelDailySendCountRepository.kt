package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.SlackChannelDailySendCountEntity
import com.clipping.mcpserver.entity.SlackChannelDailySendCountId
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SlackChannelDailySendCountRepository :
    JpaRepository<SlackChannelDailySendCountEntity, SlackChannelDailySendCountId> {
    fun findByChannelIdAndSendDate(channelId: String, sendDate: LocalDate): SlackChannelDailySendCountEntity?
}
