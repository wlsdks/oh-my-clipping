package com.ohmyclipping.service.port

/**
 * 알림 발송에 필요한 런타임 설정만 노출하는 포트.
 */
interface NotificationRuntimeSettingsPort {
    fun currentNotificationSettings(): NotificationRuntimeSettings
}

data class NotificationRuntimeSettings(
    val opsLogChannelId: String,
    val opsRequestChannelId: String,
    val slackBotToken: String,
)

/**
 * 알림 dedup 상태를 저장/조회하는 포트.
 */
interface NotificationDedupPort {
    fun isDuplicate(key: String, windowMinutes: Long): Boolean
    fun markSent(key: String, windowMinutes: Long)
}
