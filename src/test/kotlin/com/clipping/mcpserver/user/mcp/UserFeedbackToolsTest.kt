package com.clipping.mcpserver.user.mcp

import com.clipping.mcpserver.error.RateLimitExceededException
import com.clipping.mcpserver.mcp.McpRateLimiter
import com.clipping.mcpserver.model.SummaryFeedback
import com.clipping.mcpserver.service.SummaryFeedbackService
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * user_toggle_feedback 단위 테스트.
 *
 * 검증 포인트:
 *  - 해피패스: LIKE 반응을 orchestrator 주입 userId 와 함께 저장하고 JSON 으로 반환.
 *  - rate limit 초과 시 JSON-RPC 에러로 매핑.
 *  - `_onBehalfOfUserId` 누락 시 InvalidInputException 으로 거부.
 *  - NONE 반응은 서비스의 delete 경로를 타고 reaction=NONE 으로 응답.
 */
class UserFeedbackToolsTest {

    private val service = mockk<SummaryFeedbackService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = UserFeedbackTools(service, rateLimiter)

    @Nested
    inner class `user_toggle_feedback 호출 시` {

        @Test
        fun `LIKE 반응을 저장하고 결과 JSON 을 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.upsertFromMcp("u1", "s1", "LIKE") } returns
                (SummaryFeedback(id = "f1", summaryId = "s1", feedbackType = "LIKE", userId = "u1") to "좋아요로 반영되었습니다.")

            val json = tool.user_toggle_feedback(
                summaryId = "s1",
                reaction = "LIKE",
                _onBehalfOfUserId = "u1",
            )

            json shouldContain "\"summaryId\":\"s1\""
            json shouldContain "\"reaction\":\"LIKE\""
            json shouldNotContain "\"error\""
            verify(exactly = 1) { service.upsertFromMcp("u1", "s1", "LIKE") }
        }

        @Test
        fun `NONE 반응은 reaction=NONE 과 해제 메시지를 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.upsertFromMcp("u1", "s1", "NONE") } returns (null to "피드백이 해제되었습니다.")

            val json = tool.user_toggle_feedback(
                summaryId = "s1",
                reaction = "NONE",
                _onBehalfOfUserId = "u1",
            )

            json shouldContain "\"reaction\":\"NONE\""
            json shouldContain "해제"
        }

        @Test
        fun `_onBehalfOfUserId 누락 시 InvalidInputException 으로 거부된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val json = tool.user_toggle_feedback(
                summaryId = "s1",
                reaction = "LIKE",
                _onBehalfOfUserId = null,
            )

            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { service.upsertFromMcp(any(), any(), any()) }
        }

        @Test
        fun `rate limit 초과 시 JSON-RPC 에러로 감싸진다`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "user_toggle_feedback",
                    maxRequests = 120,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.user_toggle_feedback(
                summaryId = "s1",
                reaction = "LIKE",
                _onBehalfOfUserId = "u1",
            )

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { service.upsertFromMcp(any(), any(), any()) }
        }
    }
}
