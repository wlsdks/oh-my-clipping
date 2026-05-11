package com.ohmyclipping.service.dto.admin

/**
 * Slack 채널 단건/목록 조회 응답 DTO.
 */
data class SlackChannelDto(
    val id: String,
    val name: String,
    val isPrivate: Boolean
)

/**
 * Slack 채널 목록 응답 래퍼.
 * 비공개 채널 멤버십 필터링 메타 정보를 함께 반환한다.
 */
data class SlackChannelListResponse(
    val channels: List<SlackChannelDto>,
    val slackConnectRequired: Boolean = false,
    val totalBeforeFilter: Int? = null
)
