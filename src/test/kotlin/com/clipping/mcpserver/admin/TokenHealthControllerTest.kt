package com.clipping.mcpserver.admin

import com.clipping.mcpserver.observability.TokenHealthTracker
import com.clipping.mcpserver.observability.TokenStatus
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [TokenHealthController]의 상태 조회 응답 구조를 검증한다.
 * 컨트롤러는 얇은 패스스루 레이어이므로 wire 값 매핑과 ok 계산 로직만 확인한다.
 */
class TokenHealthControllerTest {

    private val tracker = mockk<TokenHealthTracker>()
    private val controller = TokenHealthController(tracker)

    @Nested
    inner class `정상 상태` {

        @Test
        fun `모든 토큰이 OK이면 ok=true와 wire값 ok를 반환한다`() {
            every { tracker.slackBotStatus() } returns TokenStatus.OK
            every { tracker.geminiStatus() } returns TokenStatus.OK

            val response = controller.getTokenHealth()

            response.slackBot shouldBe "ok"
            response.gemini shouldBe "ok"
            response.ok shouldBe true
        }
    }

    @Nested
    inner class `Slack 토큰 장애` {

        @Test
        fun `Slack이 EXPIRED이면 wire값 expired와 ok=false`() {
            every { tracker.slackBotStatus() } returns TokenStatus.EXPIRED
            every { tracker.geminiStatus() } returns TokenStatus.OK

            val response = controller.getTokenHealth()

            response.slackBot shouldBe "expired"
            response.gemini shouldBe "ok"
            response.ok shouldBe false
        }

        @Test
        fun `Slack이 SCOPE_MISMATCH이면 wire값 scope_mismatch`() {
            every { tracker.slackBotStatus() } returns TokenStatus.SCOPE_MISMATCH
            every { tracker.geminiStatus() } returns TokenStatus.OK

            val response = controller.getTokenHealth()

            response.slackBot shouldBe "scope_mismatch"
            response.ok shouldBe false
        }
    }

    @Nested
    inner class `Gemini 토큰 장애` {

        @Test
        fun `Gemini가 EXPIRED이면 wire값 expired`() {
            every { tracker.slackBotStatus() } returns TokenStatus.OK
            every { tracker.geminiStatus() } returns TokenStatus.EXPIRED

            val response = controller.getTokenHealth()

            response.gemini shouldBe "expired"
            response.ok shouldBe false
        }

        @Test
        fun `Gemini가 QUOTA_EXHAUSTED이면 wire값 quota_exhausted`() {
            every { tracker.slackBotStatus() } returns TokenStatus.OK
            every { tracker.geminiStatus() } returns TokenStatus.QUOTA_EXHAUSTED

            val response = controller.getTokenHealth()

            response.gemini shouldBe "quota_exhausted"
            response.ok shouldBe false
        }
    }

    @Nested
    inner class `동시 장애` {

        @Test
        fun `Slack EXPIRED와 Gemini QUOTA가 동시에 있으면 모두 노출된다`() {
            every { tracker.slackBotStatus() } returns TokenStatus.EXPIRED
            every { tracker.geminiStatus() } returns TokenStatus.QUOTA_EXHAUSTED

            val response = controller.getTokenHealth()

            response.slackBot shouldBe "expired"
            response.gemini shouldBe "quota_exhausted"
            response.ok shouldBe false
        }
    }
}
