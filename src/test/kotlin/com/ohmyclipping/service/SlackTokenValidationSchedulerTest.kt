package com.ohmyclipping.service

import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.observability.SlackHealthStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SlackTokenValidationSchedulerTest {

    private val slackMessageSender = mockk<SlackMessageSender>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val slackHealthStatus = SlackHealthStatus()
    private val scheduler = SlackTokenValidationScheduler(
        slackMessageSender = slackMessageSender,
        runtimeSettingService = runtimeSettingService,
        metrics = metrics,
        slackHealthStatus = slackHealthStatus
    )

    @Test
    fun `시작 시 현재 토큰과 채널로 Slack 연결을 검사한다`() {
        every { runtimeSettingService.current() } returns runtimeSettings()
        every {
            slackMessageSender.testConnection("xoxb-test", "C-ADMIN")
        } returns SlackMessageSender.SlackConnectionTestResult(
            ok = true,
            botUser = "jarvis",
            team = "Jarvis",
            channelId = "C-ADMIN",
            channelName = "alerts",
            neededScopes = null,
            providedScopes = null,
            rawError = null
        )

        scheduler.validateOnStartup()

        verify(exactly = 1) { slackMessageSender.testConnection("xoxb-test", "C-ADMIN") }
    }

    @Test
    fun `정시 검사는 Slack 검사 실패 예외를 삼키고 종료한다`() {
        every { runtimeSettingService.current() } returns runtimeSettings()
        every { slackMessageSender.testConnection(any(), any()) } throws IllegalStateException("boom")

        scheduler.validateHourly()

        verify(exactly = 1) { slackMessageSender.testConnection("xoxb-test", "C-ADMIN") }
    }

    private fun runtimeSettings(): RuntimeSettingService.RuntimeSettings =
        RuntimeSettingService.RuntimeSettings(
            defaultHoursBack = 24,
            summaryInputMaxChars = 5000,
            digestMinImportanceScore = 0.5f,
            digestDefaultMaxItems = 5,
            digestMaxMessageChars = 3500,
            digestItemSummaryMaxChars = 960,
            digestKeywordMaxCount = 6,
            jobWorkerBatchSize = 5,
            jobMaxAttempts = 3,
            jobInitialBackoffSeconds = 30,
            slackBotToken = "xoxb-test",
            opsLogChannelId = "C-ADMIN",
            slackDigestBlockKitTemplate = "",
            slackAutoDigestEnabled = false,
            slackDigestCron = "0 0 9 * * MON-FRI",
            slackAutoDigestMaxItems = 5,
            slackAutoDigestUnsentOnly = true,
            slackDailyChannelMessageLimit = 5,
            ralphOrchestrationEnabled = false,
            ralphLoopEnabled = true,
            ralphLoopMaxIterations = 4,
            ralphLoopStopPhrase = "RALPH_STOP",
            maintenanceMode = false,
            maintenanceMessage = "",
            updatedAt = null
        )
}
