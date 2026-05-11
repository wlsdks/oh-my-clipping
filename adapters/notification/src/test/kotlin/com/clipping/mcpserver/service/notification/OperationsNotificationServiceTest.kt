package com.clipping.mcpserver.service.notification

import com.clipping.mcpserver.error.DependencyFailureException
import com.clipping.mcpserver.model.AccountApprovalStatus
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.service.port.NotificationDedupPort
import com.clipping.mcpserver.service.port.NotificationRuntimeSettings
import com.clipping.mcpserver.service.port.NotificationRuntimeSettingsPort
import com.clipping.mcpserver.service.port.OpsNotificationEvent
import com.clipping.mcpserver.service.port.OpsRequestNotificationEvent
import com.clipping.mcpserver.service.port.UserNotificationEvent
import com.clipping.mcpserver.service.port.SlackDeliveryResult
import com.clipping.mcpserver.store.AdminUserStore
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class OperationsNotificationServiceTest {

    private val slackMessageSender = mockk<com.clipping.mcpserver.service.port.SlackDeliveryPort>(relaxed = true)
    private val runtimeSettingsPort = mockk<NotificationRuntimeSettingsPort>()
    private val adminUserStore = mockk<AdminUserStore>()
    private val notificationDedupPort = mockk<NotificationDedupPort>()

    init {
        // 기본: dedup 없음 (중복 아님)
        every { notificationDedupPort.isDuplicate(any(), any()) } returns false
        every { notificationDedupPort.markSent(any(), any()) } just Runs
    }

    private val service = OperationsNotificationService(
        slackMessageSender, runtimeSettingsPort, adminUserStore, notificationDedupPort
    )

    private fun stubRuntime(channelId: String = "C-ops-channel", botToken: String = "xoxb-test") {
        every { runtimeSettingsPort.currentNotificationSettings() } returns NotificationRuntimeSettings(
            opsLogChannelId = channelId,
            opsRequestChannelId = "C-ops-request",
            slackBotToken = botToken,
        )
    }

    private fun stubUser(
        userId: String = "user-1",
        dmChannelId: String? = "D-user-dm",
        isActive: Boolean = true
    ) {
        every { adminUserStore.findById(userId) } returns AdminUser(
            id = userId,
            username = "testuser",
            passwordHash = "hash",
            displayName = "Test",
            department = "dev",
            role = AccountRole.USER,
            approvalStatus = AccountApprovalStatus.APPROVED,
            isActive = isActive,
            slackDmChannelId = dmChannelId,
            createdAt = Instant.now(),
            lastLoginAt = null
        )
    }

    @Nested
    inner class `sendOps 운영 알림` {

        @Test
        fun `운영 채널에 메시지를 전송한다`() {
            stubRuntime()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendOps(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED, "비용 초과 알림")

            verify(exactly = 1) {
                slackMessageSender.sendMessage(
                    channelId = "C-ops-channel",
                    text = match { it.contains("비용 초과 알림") },
                    blocks = any(),
                    botToken = any()
                )
            }
        }

        @Test
        fun `WARN 이벤트는 경고 prefix를 추가한다`() {
            stubRuntime()
            val capturedText = slot<String>()
            every { slackMessageSender.sendMessage(any(), capture(capturedText), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendOps(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED, "비용 초과")

            capturedText.captured shouldBe ":warning: 비용 초과"
        }

        @Test
        fun `CRITICAL 이벤트는 긴급 prefix를 추가한다`() {
            stubRuntime()
            val capturedText = slot<String>()
            every { slackMessageSender.sendMessage(any(), capture(capturedText), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendOps(OpsNotificationEvent.JOB_PERMANENTLY_FAILED, "작업 최종 실패")

            capturedText.captured shouldBe ":rotating_light: 작업 최종 실패"
        }

        @Test
        fun `INFO 이벤트는 prefix를 추가하지 않는다`() {
            stubRuntime()
            val capturedText = slot<String>()
            every { slackMessageSender.sendMessage(any(), capture(capturedText), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendOps(OpsNotificationEvent.SOURCE_RETRY_RESULT, "재시도 성공", mapOf("date" to "2026-04-01"))

            capturedText.captured shouldBe "재시도 성공"
        }

        @Test
        fun `채널 ID가 비어있으면 발송을 건너뛴다`() {
            stubRuntime(channelId = "")

            service.sendOps(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED, "비용 초과")

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

    }

    @Nested
    inner class `sendOps dedup 중복 방지` {

        @Test
        fun `같은 dedup 키로 연속 호출하면 두 번째는 건너뛴다`() {
            stubRuntime()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")
            // 첫 호출은 중복 아님, 두 번째는 중복
            every { notificationDedupPort.isDuplicate(any(), any()) } returns false andThen true

            val params = mapOf("date" to "2026-04-01")
            service.sendOps(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED, "비용 초과", params)
            service.sendOps(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED, "비용 초과 2", params)

            verify(exactly = 1) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `다른 dedup 파라미터는 각각 발송된다`() {
            stubRuntime()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendOps(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED, "4월 1일", mapOf("date" to "2026-04-01"))
            service.sendOps(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED, "4월 2일", mapOf("date" to "2026-04-02"))

            verify(exactly = 2) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `dedup 키가 없는 이벤트는 매번 발송된다`() {
            stubRuntime()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendOps(OpsNotificationEvent.JOB_PERMANENTLY_FAILED, "실패 1")
            service.sendOps(OpsNotificationEvent.JOB_PERMANENTLY_FAILED, "실패 2")

            verify(exactly = 2) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `전송 성공 시 Redis에 dedup 키를 기록한다`() {
            stubRuntime()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendOps(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED, "비용 초과", mapOf("date" to "2026-04-01"))

            verify(exactly = 1) {
                notificationDedupPort.markSent(
                    match { it.contains("COST_THRESHOLD_EXCEEDED") },
                    eq(1440L)
                )
            }
        }
    }

    @Nested
    inner class `sendOps retry 재시도` {

        @Test
        fun `첫 번째 실패 후 재시도에 성공하면 전송 완료된다`() {
            stubRuntime()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } throws
                RuntimeException("일시 오류") andThen SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendOps(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED, "비용 초과", mapOf("date" to "2026-04-01"))

            // maxRetries=1이므로 최대 2회 시도
            verify(exactly = 2) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `maxRetries 0인 이벤트는 실패해도 재시도하지 않는다`() {
            stubRuntime()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("오류")

            service.sendOps(OpsNotificationEvent.KEYWORD_VOLATILITY, "변동 감지", mapOf("date" to "2026-04-01"))

            // maxRetries=0이므로 1회만 시도
            verify(exactly = 1) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `sendUserDm 사용자 DM` {

        @Test
        fun `사용자 DM 채널로 메시지를 전송한다`() {
            stubRuntime()
            stubUser()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendUserDm(UserNotificationEvent.SUBSCRIPTION_APPROVED, "user-1", "구독이 승인되었습니다")

            verify(exactly = 1) {
                slackMessageSender.sendMessage(
                    channelId = "D-user-dm",
                    text = "구독이 승인되었습니다",
                    blocks = any(),
                    botToken = "xoxb-test"
                )
            }
        }

        @Test
        fun `사용자가 없으면 발송을 건너뛴다`() {
            stubRuntime()
            every { adminUserStore.findById("unknown") } returns null

            service.sendUserDm(UserNotificationEvent.SUBSCRIPTION_APPROVED, "unknown", "승인됨")

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `DM 채널이 없으면 발송을 건너뛴다`() {
            stubRuntime()
            stubUser(dmChannelId = null)

            service.sendUserDm(UserNotificationEvent.SUBSCRIPTION_APPROVED, "user-1", "승인됨")

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

    }

    @Nested
    inner class `sendOpsRequest 운영 요청 알림` {

        @Test
        fun `opsRequestChannelId가 설정되면 메시지를 전송한다`() {
            stubRuntime()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-1", channelId = "C-any")

            service.sendOpsRequest(OpsRequestNotificationEvent.USER_SIGNUP_REQUESTED, "새 가입 요청")

            verify(exactly = 1) {
                slackMessageSender.sendMessage(
                    channelId = "C-ops-request",
                    text = match { it.contains("새 가입 요청") },
                    blocks = any(),
                    botToken = any()
                )
            }
        }

        @Test
        fun `opsRequestChannelId가 비어있으면 전송을 생략한다`() {
            every { runtimeSettingsPort.currentNotificationSettings() } returns NotificationRuntimeSettings(
                opsLogChannelId = "C-ops-channel",
                opsRequestChannelId = "",
                slackBotToken = "xoxb-test",
            )

            service.sendOpsRequest(OpsRequestNotificationEvent.USER_SIGNUP_REQUESTED, "새 가입 요청")

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `Slack 전송 실패해도 예외를 던지지 않는다`() {
            stubRuntime()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } throws
                DependencyFailureException("Slack API 오류")

            // 예외가 전파되지 않아야 한다
            service.sendOpsRequest(OpsRequestNotificationEvent.USER_SIGNUP_REQUESTED, "새 가입 요청")
        }
    }

    @Nested
    inner class `resolveDedupKey 키 생성` {

        @Test
        fun `템플릿 파라미터를 치환한다`() {
            val key = service.resolveDedupKey(
                OpsNotificationEvent.SOURCE_AUTO_DISABLED,
                mapOf("sourceId" to "src-42")
            )
            key shouldBe "SOURCE_AUTO_DISABLED:source:src-42"
        }

        @Test
        fun `dedup 템플릿이 없으면 null을 반환한다`() {
            val key = service.resolveDedupKey(OpsNotificationEvent.JOB_PERMANENTLY_FAILED, emptyMap())
            key shouldBe null
        }
    }

}
