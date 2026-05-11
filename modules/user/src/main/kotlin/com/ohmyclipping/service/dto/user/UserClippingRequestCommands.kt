package com.ohmyclipping.service.dto.user

/**
 * 사용자 클리핑 요청 생성 커맨드.
 */
data class UserClippingRequestSubmission(
    val requestName: String,
    val sourceName: String,
    val sourceUrl: String,
    val slackChannelId: String,
    val personaName: String,
    val personaPrompt: String,
    val summaryStyle: String?,
    val targetAudience: String?,
    val selectedPresetId: String? = null,
    val requestNote: String?
)

/**
 * 승인된 기존 요청에 RSS 소스를 추가할 때 사용하는 커맨드.
 */
data class UserAdditionalRssSourcesSubmission(
    val baseRequestId: String,
    val sources: List<UserRssSourceSubmission>,
    val requestNote: String?
)

/**
 * 사용자 추가 RSS 소스 입력 모델.
 */
data class UserRssSourceSubmission(
    val sourceName: String,
    val sourceUrl: String
)
