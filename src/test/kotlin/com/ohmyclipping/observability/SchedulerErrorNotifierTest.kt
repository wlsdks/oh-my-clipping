package com.ohmyclipping.observability

import com.ohmyclipping.service.RuntimeSettingService
import com.ohmyclipping.service.SlackMessageSender
import com.ohmyclipping.service.port.SlackDeliveryResult
import com.ohmyclipping.support.SlackFailureSeverity
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [SchedulerErrorNotifier]의 severity 라우팅과 dedup 동작, 파일 폴백을 검증한다.
 * - CRITICAL은 dedup 우회 + securityAlertChannelId 우선 라우팅 (F8)
 * - 그 외 severity는 기존 dedup 유지 + SLACK_CHANNEL 환경변수 채널 사용
 * - Slack 발송 실패 시 예외를 삼키고 파일 폴백 경로를 타는지 (F12)
 */
class SchedulerErrorNotifierTest {

    private val slackSender = mockk<com.ohmyclipping.service.port.SlackDeliveryPort>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val tracker = mockk<TokenHealthTracker>(relaxed = true)

    private fun settings(
        botToken: String = "xoxb-test",
        opsLogChannelId: String = "",
        securityAlertChannelId: String = ""
    ) = RuntimeSettingService.RuntimeSettings(
        defaultHoursBack = 24,
        summaryInputMaxChars = 2000,
        digestMinImportanceScore = 0.5f,
        digestDefaultMaxItems = 5,
        digestMaxMessageChars = 2000,
        digestItemSummaryMaxChars = 500,
        digestKeywordMaxCount = 5,
        jobWorkerBatchSize = 5,
        jobMaxAttempts = 3,
        jobInitialBackoffSeconds = 10,
        slackBotToken = botToken,
        slackDigestBlockKitTemplate = "",
        slackAutoDigestEnabled = false,
        slackDigestCron = "-",
        slackAutoDigestMaxItems = 3,
        slackAutoDigestUnsentOnly = true,
        slackDailyChannelMessageLimit = 50,
        maintenanceMode = false,
        maintenanceMessage = "",
        opsLogChannelId = opsLogChannelId,
        opsRequestChannelId = "",
        securityAlertChannelId = securityAlertChannelId,
        updatedAt = null
    )

    @Nested
    inner class `severity 라우팅` {

        @Test
        fun `CRITICAL severity는 securityAlertChannelId로 발송된다`() {
            // given: securityAlert 채널이 설정되어 있으면 errorChannelId보다 우선한다
            every { runtimeSettingService.current() } returns settings(
                securityAlertChannelId = "C_SECURITY"
            )
            val notifier = SchedulerErrorNotifier(
                slackMessageSender = slackSender,
                runtimeSettingService = runtimeSettingService,
                tokenHealthTracker = tracker,
                errorChannelId = "C_DEFAULT"
            )
            val channelSlot = slot<String>()
            every { slackSender.sendMessage(capture(channelSlot), any(), any(), any(), any(), any(), any(), any()) } returns
                SlackDeliveryResult(ts = "1", channelId = "C_SECURITY", ok = true, payloadJson = "{}")

            // when
            notifier.notifyBackgroundError(
                context = "Gemini 키 만료",
                exception = RuntimeException("401"),
                severity = SlackFailureSeverity.CRITICAL
            )

            // then
            channelSlot.captured shouldBe "C_SECURITY"
        }

        @Test
        fun `CRITICAL이고 securityAlert 미설정이면 opsLogChannelId로 폴백한다`() {
            // given
            every { runtimeSettingService.current() } returns settings(opsLogChannelId = "C_OPSLOG")
            val notifier = SchedulerErrorNotifier(
                slackMessageSender = slackSender,
                runtimeSettingService = runtimeSettingService,
                tokenHealthTracker = tracker,
                errorChannelId = "C_DEFAULT"
            )
            val channelSlot = slot<String>()
            every { slackSender.sendMessage(capture(channelSlot), any(), any(), any(), any(), any(), any(), any()) } returns
                SlackDeliveryResult(ts = "1", channelId = "C_OPSLOG", ok = true, payloadJson = "{}")

            // when
            notifier.notifySchedulerError(
                schedulerName = "slack-send",
                exception = RuntimeException("invalid_auth"),
                severity = SlackFailureSeverity.CRITICAL
            )

            // then
            channelSlot.captured shouldBe "C_OPSLOG"
        }

        @Test
        fun `비-CRITICAL severity는 기본 SLACK_CHANNEL 환경변수 채널로 발송된다`() {
            every { runtimeSettingService.current() } returns settings(
                securityAlertChannelId = "C_SECURITY",
                opsLogChannelId = "C_OPSLOG"
            )
            val notifier = SchedulerErrorNotifier(
                slackMessageSender = slackSender,
                runtimeSettingService = runtimeSettingService,
                tokenHealthTracker = tracker,
                errorChannelId = "C_DEFAULT"
            )
            val channelSlot = slot<String>()
            every { slackSender.sendMessage(capture(channelSlot), any(), any(), any(), any(), any(), any(), any()) } returns
                SlackDeliveryResult(ts = "1", channelId = "C_DEFAULT", ok = true, payloadJson = "{}")

            notifier.notifySchedulerError(
                schedulerName = "rss-collect",
                exception = RuntimeException("timeout")
                // severity 기본 WARN
            )

            channelSlot.captured shouldBe "C_DEFAULT"
        }
    }

    @Nested
    inner class `dedup 동작` {

        @Test
        fun `CRITICAL은 5분 dedup을 우회해 연달아 발송한다`() {
            every { runtimeSettingService.current() } returns settings(securityAlertChannelId = "C_SEC")
            val notifier = SchedulerErrorNotifier(
                slackMessageSender = slackSender,
                runtimeSettingService = runtimeSettingService,
                tokenHealthTracker = tracker,
                errorChannelId = "C_DEFAULT"
            )
            every { slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                SlackDeliveryResult(ts = "1", channelId = "C_SEC", ok = true, payloadJson = "{}")

            // when: 같은 예외 타입을 CRITICAL로 두 번 발송
            val ex = RuntimeException("401")
            notifier.notifySchedulerError("gemini", ex, severity = SlackFailureSeverity.CRITICAL)
            notifier.notifySchedulerError("gemini", ex, severity = SlackFailureSeverity.CRITICAL)

            // then: 두 번 모두 발송된다
            verify(exactly = 2) { slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `WARN severity는 5분 이내 같은 예외 재발송을 억제한다`() {
            every { runtimeSettingService.current() } returns settings()
            val notifier = SchedulerErrorNotifier(
                slackMessageSender = slackSender,
                runtimeSettingService = runtimeSettingService,
                tokenHealthTracker = tracker,
                errorChannelId = "C_DEFAULT"
            )
            every { slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                SlackDeliveryResult(ts = "1", channelId = "C_DEFAULT", ok = true, payloadJson = "{}")

            val ex = RuntimeException("timeout")
            notifier.notifySchedulerError("rss", ex)
            notifier.notifySchedulerError("rss", ex)

            // 두 번째 호출은 dedup으로 억제되어야 한다
            verify(exactly = 1) { slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `파일 폴백 (F12)` {

        @Test
        fun `Slack 발송이 예외를 던져도 호출자에 전파되지 않는다`() {
            // given: Slack API가 예외를 던지는 상황
            every { runtimeSettingService.current() } returns settings()
            val notifier = SchedulerErrorNotifier(
                slackMessageSender = slackSender,
                runtimeSettingService = runtimeSettingService,
                tokenHealthTracker = tracker,
                errorChannelId = "C_DEFAULT"
            )
            every {
                slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("Slack API down")

            // when & then: 예외가 전파되지 않고 메서드가 정상 반환된다
            notifier.notifySchedulerError(
                schedulerName = "test-job",
                exception = RuntimeException("oops")
            )

            verify(exactly = 1) { slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `SLACK_CHANNEL 미설정이면 비-CRITICAL 발송은 생략된다`() {
            // given
            every { runtimeSettingService.current() } returns settings()
            val notifier = SchedulerErrorNotifier(
                slackMessageSender = slackSender,
                runtimeSettingService = runtimeSettingService,
                tokenHealthTracker = tracker,
                errorChannelId = "" // SLACK_CHANNEL 미설정
            )

            // when
            notifier.notifySchedulerError("rss", RuntimeException("x"))

            // then: Slack API는 호출되지 않는다 (채널 blank → skip)
            verify(exactly = 0) { slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }
}
