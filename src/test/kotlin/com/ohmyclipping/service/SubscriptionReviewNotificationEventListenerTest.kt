package com.ohmyclipping.service

import com.ohmyclipping.service.notification.UserNotificationService
import com.ohmyclipping.service.event.SubscriptionReviewNotificationEvent
import com.ohmyclipping.service.event.SubscriptionReviewNotificationEventListener
import com.ohmyclipping.service.event.SubscriptionReviewNotificationType
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SubscriptionReviewNotificationEventListenerTest {

    private val userNotificationService = mockk<UserNotificationService>()
    private val listener = SubscriptionReviewNotificationEventListener(userNotificationService)

    @Test
    fun `APPROVED 이벤트는 승인 DM 알림으로 전달한다`() {
        justRun { userNotificationService.notifySubscriptionApproved("user-1", "AI 뉴스") }

        listener.handle(
            SubscriptionReviewNotificationEvent(
                userId = "user-1",
                requestName = "AI 뉴스",
                reviewType = SubscriptionReviewNotificationType.APPROVED,
                reviewNote = null
            )
        )

        verify(exactly = 1) { userNotificationService.notifySubscriptionApproved("user-1", "AI 뉴스") }
    }

    @Test
    fun `REJECTED 이벤트는 반려 DM 알림으로 전달한다`() {
        justRun {
            userNotificationService.notifySubscriptionRejected("user-1", "AI 뉴스", "중복")
        }

        listener.handle(
            SubscriptionReviewNotificationEvent(
                userId = "user-1",
                requestName = "AI 뉴스",
                reviewType = SubscriptionReviewNotificationType.REJECTED,
                reviewNote = "중복"
            )
        )

        verify(exactly = 1) {
            userNotificationService.notifySubscriptionRejected("user-1", "AI 뉴스", "중복")
        }
    }
}
