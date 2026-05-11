package com.ohmyclipping.service.event

/**
 * 구독 승인/반려 결과를 커밋 이후 사용자 DM 알림으로 전달하기 위한 도메인 이벤트.
 */
data class SubscriptionReviewNotificationEvent(
    val userId: String,
    val requestName: String,
    val reviewType: SubscriptionReviewNotificationType,
    val reviewNote: String?
)

enum class SubscriptionReviewNotificationType {
    APPROVED,
    REJECTED
}
