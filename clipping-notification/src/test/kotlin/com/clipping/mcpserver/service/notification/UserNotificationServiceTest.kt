package com.clipping.mcpserver.service.notification

import com.clipping.mcpserver.service.port.UserNotificationEvent
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserNotificationServiceTest {

    private val notificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val service = UserNotificationService(notificationService)

    @Nested
    inner class `구독 승인 알림` {

        @Test
        fun `정상적으로 DM을 전송한다`() {
            service.notifySubscriptionApproved("u1", "AI 뉴스")

            verify(exactly = 1) {
                notificationService.sendUserDm(
                    eq(UserNotificationEvent.SUBSCRIPTION_APPROVED),
                    eq("u1"),
                    match { it.contains("AI 뉴스") && it.contains("승인") },
                    any()
                )
            }
        }
    }

    @Nested
    inner class `구독 반려 알림` {

        @Test
        fun `반려 사유가 있으면 메시지에 포함된다`() {
            service.notifySubscriptionRejected("u1", "금융 뉴스", "중복 구독")

            verify(exactly = 1) {
                notificationService.sendUserDm(
                    eq(UserNotificationEvent.SUBSCRIPTION_REJECTED),
                    eq("u1"),
                    match { it.contains("반려") && it.contains("중복 구독") },
                    any()
                )
            }
        }

        @Test
        fun `반려 사유가 null이면 사유 없이 메시지를 전송한다`() {
            service.notifySubscriptionRejected("u1", "금융 뉴스", null)

            verify(exactly = 1) {
                notificationService.sendUserDm(
                    eq(UserNotificationEvent.SUBSCRIPTION_REJECTED),
                    eq("u1"),
                    match { it.contains("반려") },
                    any()
                )
            }
        }

        @Test
        fun `반려 사유가 빈 문자열이면 사유 없이 메시지를 전송한다`() {
            service.notifySubscriptionRejected("u1", "금융 뉴스", "  ")

            verify(exactly = 1) {
                notificationService.sendUserDm(
                    eq(UserNotificationEvent.SUBSCRIPTION_REJECTED),
                    eq("u1"),
                    match { it.contains("반려") },
                    any()
                )
            }
        }
    }

    @Nested
    inner class `발송 실패 알림` {

        @Test
        fun `발송 실패 메시지를 전송한다`() {
            service.notifyDeliveryFailed("u1", "기술 트렌드")

            verify(exactly = 1) {
                notificationService.sendUserDm(
                    eq(UserNotificationEvent.DELIVERY_FAILED),
                    eq("u1"),
                    match { it.contains("기술 트렌드") && it.contains("문제") },
                    any()
                )
            }
        }
    }

    @Nested
    inner class `DM 전송 위임` {

        @Test
        fun `notifySubscriptionApproved는 sendUserDm에 위임한다 - 예외 처리는 OperationsNotificationService가 담당`() {
            service.notifySubscriptionApproved("u1", "AI 뉴스")

            verify(exactly = 1) { notificationService.sendUserDm(any(), any(), any(), any()) }
        }
    }
}
