package com.ohmyclipping.admin.dto

/** Slack 채널 차단 요청 DTO. */
data class BlockChannelRequest(
    val channelId: String,
    val channelName: String,
    val isPrivate: Boolean = false,
    /** 차단 사유 (선택, 200자 이내) */
    val reason: String? = null
)

/** 차단된 Slack 채널 응답 DTO. */
data class BlockedChannelResponse(
    val id: String,
    val channelId: String,
    val channelName: String,
    val isPrivate: Boolean,
    val blockedByUserId: String,
    val blockedAt: String,
    val reason: String? = null
)
