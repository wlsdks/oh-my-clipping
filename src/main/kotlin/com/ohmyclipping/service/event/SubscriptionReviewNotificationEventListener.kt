package com.ohmyclipping.service.event

import com.ohmyclipping.service.notification.UserNotificationService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 구독 리뷰 결과 이벤트를 트랜잭션 커밋 이후 사용자 DM 알림으로 변환한다.
 */
@Component
class SubscriptionReviewNotificationEventListener(
    private val userNotificationService: UserNotificationService
) {

    /**
     * 승인/반려 이벤트는 DB 커밋이 끝난 뒤에만 사용자에게 알린다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: SubscriptionReviewNotificationEvent) {
        // 이벤트 유형에 따라 사용자 알림 서비스의 전용 메시지를 호출한다.
        when (event.reviewType) {
            SubscriptionReviewNotificationType.APPROVED -> {
                userNotificationService.notifySubscriptionApproved(event.userId, event.requestName)
            }

            SubscriptionReviewNotificationType.REJECTED -> {
                userNotificationService.notifySubscriptionRejected(
                    event.userId,
                    event.requestName,
                    event.reviewNote
                )
            }
        }
    }
}
