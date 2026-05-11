package com.ohmyclipping.service.notification

import com.ohmyclipping.service.port.UserNotificationEvent

import org.springframework.stereotype.Service

/**
 * 사용자에게 Slack DM으로 주요 이벤트를 알리는 서비스.
 * 승인/반려/발송 실패 등 사용자가 알아야 할 이벤트를 DM으로 전송한다.
 * 내부적으로 OperationsNotificationService를 통해 dedup/retry를 공통 처리한다.
 */
@Service
class UserNotificationService(
    private val notificationService: OperationsNotificationService
) {

    /** 구독 승인 알림을 사용자에게 DM으로 전송한다. */
    fun notifySubscriptionApproved(userId: String, categoryName: String) {
        notificationService.sendUserDm(
            UserNotificationEvent.SUBSCRIPTION_APPROVED,
            userId,
            "'${categoryName}' 구독이 승인되었어요! 다음 발송 시간부터 뉴스를 받아볼 수 있어요."
        )
    }

    /** 구독 반려 알림을 사용자에게 DM으로 전송한다. 반려 사유가 있으면 함께 안내한다. */
    fun notifySubscriptionRejected(userId: String, categoryName: String, reason: String?) {
        val msg = if (reason.isNullOrBlank()) {
            "'${categoryName}' 구독 요청이 반려되었어요."
        } else {
            "'${categoryName}' 구독 요청이 반려되었어요. 사유: $reason"
        }
        notificationService.sendUserDm(UserNotificationEvent.SUBSCRIPTION_REJECTED, userId, msg)
    }

    /** 발송 실패 알림을 사용자에게 DM으로 전송한다. */
    fun notifyDeliveryFailed(userId: String, categoryName: String) {
        notificationService.sendUserDm(
            UserNotificationEvent.DELIVERY_FAILED,
            userId,
            "'${categoryName}' 뉴스 발송에 문제가 있어요. 관리자에게 문의해 주세요.",
            mapOf("userId" to userId, "categoryId" to categoryName)
        )
    }
}
