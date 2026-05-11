package com.clipping.mcpserver.store

import java.time.LocalDate

data class SlackChannelSendReservation(
    val messageCount: Int,
    val allowed: Boolean
)

interface SlackChannelDailySendCountStore {
    fun reserveSlot(channelId: String, sendDate: LocalDate, dailyLimit: Int): SlackChannelSendReservation
    fun releaseSlot(channelId: String, sendDate: LocalDate)
}
