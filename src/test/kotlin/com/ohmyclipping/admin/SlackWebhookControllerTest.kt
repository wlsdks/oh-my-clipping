package com.ohmyclipping.admin

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.SummaryFeedback
import com.ohmyclipping.service.SummaryFeedbackService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [SlackWebhookController] 단위 테스트.
 *
 * 컨트롤러는 얇은 패스스루 레이어지만 Slack 이 form-urlencoded / JSON 양쪽 포맷으로 웹훅을 보내므로,
 * payload 정규화·공백/빈 요청 차단·서비스 위임 규칙을 행동 관점에서 검증한다.
 */
class SlackWebhookControllerTest {

    private val summaryFeedbackService = mockk<SummaryFeedbackService>()
    private val controller = SlackWebhookController(summaryFeedbackService)

    private fun fakeFeedback(
        id: String = "fb-1",
        summaryId: String = "s-1",
        feedbackType: String = "like"
    ): SummaryFeedback = SummaryFeedback(
        id = id,
        summaryId = summaryId,
        feedbackType = feedbackType,
        userId = "u-1"
    )

    @Nested
    inner class `입력 검증` {

        @Test
        fun `form 경로에 payload 가 null 이면 InvalidInputException 이 발생한다`() {
            // form-urlencoded 전송에서 payload 필드가 누락된 경우
            shouldThrow<InvalidInputException> {
                controller.handleSlackFeedbackForm(null)
            }
            // 서비스에 위임되지 않아야 한다
            verify(exactly = 0) { summaryFeedbackService.recordFromSlackPayload(any()) }
        }

        @Test
        fun `공백 문자열만 담긴 payload 는 InvalidInputException 을 던진다`() {
            // trim 후 blank 이면 처리 거부 — Slack 이 빈 값을 보내는 사고를 방어한다.
            shouldThrow<InvalidInputException> {
                controller.handleSlackFeedbackForm("   ")
            }
        }

        @Test
        fun `JSON 경로에 null body 가 들어와도 InvalidInputException 으로 일관성 있게 거절한다`() {
            shouldThrow<InvalidInputException> {
                controller.handleSlackFeedbackJson(null)
            }
        }
    }

    @Nested
    inner class `정상 payload 처리` {

        @Test
        fun `form 경로로 유효 payload 가 오면 서비스 위임 후 200 응답 body 를 만든다`() {
            // payload 내용은 서비스가 파싱하므로 임의 JSON 문자열을 넘겨도 된다.
            val payload = """{"actions":[{"value":"like"}]}"""
            every {
                summaryFeedbackService.recordFromSlackPayload(payload)
            } returns (fakeFeedback() to "피드백이 저장되었습니다.")

            val response = controller.handleSlackFeedbackForm(payload)

            response.statusCode.value() shouldBe 200
            val body = response.body!!
            body["response_type"] shouldBe "ephemeral"
            body["replace_original"] shouldBe false
            body["text"] shouldBe "피드백이 저장되었습니다."
            body["feedbackId"] shouldBe "fb-1"
            body["summaryId"] shouldBe "s-1"
            body["feedbackType"] shouldBe "like"
            verify(exactly = 1) { summaryFeedbackService.recordFromSlackPayload(payload) }
        }

        @Test
        fun `앞뒤 공백은 trim 한 뒤 서비스에 전달한다`() {
            val rawPayload = "  {\"actions\":[]}  "
            val trimmed = rawPayload.trim()
            every {
                summaryFeedbackService.recordFromSlackPayload(trimmed)
            } returns (fakeFeedback() to "ok")

            val response = controller.handleSlackFeedbackForm(rawPayload)

            response.statusCode.value() shouldBe 200
            verify(exactly = 1) { summaryFeedbackService.recordFromSlackPayload(trimmed) }
        }

        @Test
        fun `JSON 바디 경로도 동일한 서비스 위임 결과를 쓴다`() {
            val payload = """{"type":"block_actions"}"""
            every {
                summaryFeedbackService.recordFromSlackPayload(payload)
            } returns (fakeFeedback(feedbackType = "dislike") to "반영됨")

            val response = controller.handleSlackFeedbackJson(payload)

            response.statusCode.value() shouldBe 200
            response.body?.get("feedbackType") shouldBe "dislike"
            response.body?.get("text") shouldBe "반영됨"
        }

        @Test
        fun `fallback 경로(Content-Type 미지정)에서도 form 과 동일한 동작을 한다`() {
            val payload = """{"actions":[]}"""
            every {
                summaryFeedbackService.recordFromSlackPayload(payload)
            } returns (fakeFeedback() to "ok")

            val response = controller.handleSlackFeedbackFallback(payload)

            response.statusCode.value() shouldBe 200
            verify(exactly = 1) { summaryFeedbackService.recordFromSlackPayload(payload) }
        }
    }

    @Nested
    inner class `서비스 예외 전파` {

        @Test
        fun `서비스가 InvalidInputException 을 던지면 컨트롤러도 그대로 전파한다`() {
            // 컨트롤러는 서비스 예외를 자체 try/catch 하지 않고 전역 핸들러로 넘긴다.
            val payload = """{"malformed":true}"""
            every {
                summaryFeedbackService.recordFromSlackPayload(payload)
            } throws InvalidInputException("유효하지 않은 Slack 피드백 payload")

            shouldThrow<InvalidInputException> {
                controller.handleSlackFeedbackForm(payload)
            }
        }
    }
}
