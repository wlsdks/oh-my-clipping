package com.clipping.mcpserver.observability

import com.clipping.mcpserver.service.RuntimeSettingService
import com.clipping.mcpserver.service.SlackMessageSender
import com.clipping.mcpserver.service.port.SlackDeliveryResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

/**
 * ErrorSlackNotifier 단위 테스트.
 *
 * 500 에러가 Slack 채널로 전달되는지, 중복 알림 억제(dedup)와 환경변수 guard 가 의도대로 동작하는지,
 * 그리고 Slack 발송 실패가 본 에러 응답을 가로막지 않는지를 검증한다.
 */
class ErrorSlackNotifierTest {

    private val slackSender = mockk<com.clipping.mcpserver.service.port.SlackDeliveryPort>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>()

    private fun settings(botToken: String = "xoxb-test") =
        RuntimeSettingService.RuntimeSettings(
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
            opsLogChannelId = "",
            opsRequestChannelId = "",
            securityAlertChannelId = "",
            updatedAt = null
        )

    private fun notifier(errorChannelId: String): ErrorSlackNotifier =
        ErrorSlackNotifier(slackSender, runtimeSettingService, errorChannelId)

    private fun mockExchange(
        method: String = "POST",
        path: String = "/api/admin/test"
    ): MockServerWebExchange {
        val builder = when (method.uppercase()) {
            "GET" -> MockServerHttpRequest.get(path)
            "POST" -> MockServerHttpRequest.post(path)
            else -> MockServerHttpRequest.get(path)
        }
        return MockServerWebExchange.from(builder.build())
    }

    @Nested
    inner class `환경변수 가드` {

        @Test
        fun `errorChannelId 가 비어있으면 Slack 호출 없이 조기 종료한다`() {
            val sut = notifier(errorChannelId = "")

            sut.notifyError(mockExchange(), RuntimeException("boom"))

            verify(exactly = 0) {
                slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `slackBotToken 이 비어있으면 Slack 호출을 생략한다`() {
            every { runtimeSettingService.current() } returns settings(botToken = "")
            val sut = notifier(errorChannelId = "C_ERROR")

            sut.notifyError(mockExchange(), RuntimeException("boom"))

            verify(exactly = 0) {
                slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Nested
    inner class `정상 알림 발송` {

        @Test
        fun `500 발생 시 에러명, 경로, 메시지가 본문에 포함되어 Slack 전송된다`() {
            every { runtimeSettingService.current() } returns settings()
            val sut = notifier(errorChannelId = "C_ERROR")
            val textSlot = slot<String>()
            every {
                slackSender.sendMessage(any(), capture(textSlot), any(), any(), any(), any(), any(), any())
            } returns SlackDeliveryResult(ts = "1", channelId = "C_ERROR", ok = true, payloadJson = "{}")

            sut.notifyError(
                mockExchange(method = "POST", path = "/api/admin/send"),
                RuntimeException("데이터베이스 커넥션 실패")
            )

            verify(exactly = 1) {
                slackSender.sendMessage(eq("C_ERROR"), any(), any(), any(), any(), any(), any(), any())
            }
            val body = textSlot.captured
            body shouldContain "RuntimeException"
            body shouldContain "/api/admin/send"
            body shouldContain "데이터베이스 커넥션 실패"
            body shouldContain "POST"
        }
    }

    @Nested
    inner class `중복 알림 억제 (dedup)` {

        @Test
        fun `같은 에러 타입과 경로가 반복되면 두 번째 알림은 억제된다`() {
            // dedup 키는 "${exception.simpleName}:$path" 이므로 같은 조합은 1분 내 1회만 전송되어야 한다.
            every { runtimeSettingService.current() } returns settings()
            val sut = notifier(errorChannelId = "C_ERROR")
            every {
                slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            } returns SlackDeliveryResult(ts = "1", channelId = "C_ERROR", ok = true, payloadJson = "{}")

            val exchange = mockExchange(path = "/api/users/123")
            sut.notifyError(exchange, IllegalStateException("first"))
            sut.notifyError(exchange, IllegalStateException("second"))

            verify(exactly = 1) {
                slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `경로가 다르면 dedup 키가 달라 두 번 모두 발송된다`() {
            every { runtimeSettingService.current() } returns settings()
            val sut = notifier(errorChannelId = "C_ERROR")
            every {
                slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            } returns SlackDeliveryResult(ts = "1", channelId = "C_ERROR", ok = true, payloadJson = "{}")

            sut.notifyError(mockExchange(path = "/api/a"), IllegalStateException("first"))
            sut.notifyError(mockExchange(path = "/api/b"), IllegalStateException("second"))

            verify(exactly = 2) {
                slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Nested
    inner class `발송 실패 보호막` {

        @Test
        fun `Slack 호출에서 예외가 발생해도 notifyError 는 조용히 무시한다`() {
            // 알림 실패로 원래 에러 응답 흐름이 끊기면 안 된다.
            every { runtimeSettingService.current() } returns settings()
            every {
                slackSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("slack-down")
            val sut = notifier(errorChannelId = "C_ERROR")

            // 예외가 바깥으로 새면 전역 핸들러가 터진다 — shouldBe Unit 로 명시
            val result = runCatching {
                sut.notifyError(mockExchange(), RuntimeException("internal"))
            }
            result.isSuccess shouldBe true
        }
    }
}
