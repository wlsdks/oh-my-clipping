package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.service.UserClippingRequestService
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminListPendingRequestsToolTest {

    private val userClippingRequestService = mockk<UserClippingRequestService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminListPendingRequestsTool(userClippingRequestService, rateLimiter)

    @Nested
    inner class `admin_list_pending_requests` {

        @Test
        fun `limit 이 null 이면 기본값 20 으로 승인 대기 요청을 조회한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every {
                userClippingRequestService.listRecentRequests(UserClippingRequestStatus.PENDING, 20)
            } returns emptyList()

            val json = tool.admin_list_pending_requests(limit = null)

            json shouldNotContain "\"error\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("admin_list_pending_requests", maxRequests = 60, windowSeconds = 3600)
            }
            verify(exactly = 1) {
                userClippingRequestService.listRecentRequests(UserClippingRequestStatus.PENDING, 20)
            }
        }

        @Test
        fun `범위 밖 limit 은 서비스 호출 없이 validation error 로 거부한다`() {
            val json = tool.admin_list_pending_requests(limit = 0)

            json shouldContain "\"error\""
            json shouldContain "limit must be between 1 and 50"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) {
                userClippingRequestService.listRecentRequests(any(), any())
            }
        }

        @Test
        fun `rate limit 초과 시 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_list_pending_requests",
                    maxRequests = 60,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_list_pending_requests(limit = 20)

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) {
                userClippingRequestService.listRecentRequests(any(), any())
            }
        }
    }
}
