package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.DeliveryAdminService
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
 * admin_retry_failed_delivery 단위 테스트.
 *
 * 검증 포인트:
 *  - 해피패스: deliveryAdminService.retryDelivery 가 호출되고 success JSON 반환.
 *  - 빈 deliveryLogId 는 InvalidInputException.
 *  - rate limit 초과 시 서비스 미호출.
 */
class AdminRetryFailedDeliveryToolTest {

    private val service = mockk<DeliveryAdminService>(relaxed = true)
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminRetryFailedDeliveryTool(service, rateLimiter)

    @Nested
    inner class `admin_retry_failed_delivery` {

        @Test
        fun `해피패스 — retryDelivery 호출 후 success true JSON 을 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.retryDelivery("log1") } returns Unit

            val json = tool.admin_retry_failed_delivery(deliveryLogId = "log1")

            json shouldContain "\"success\":true"
            json shouldContain "\"deliveryLogId\":\"log1\""
            json shouldNotContain "\"error\""
            verify(exactly = 1) { service.retryDelivery("log1") }
        }

        @Test
        fun `빈 deliveryLogId 는 validation error 로 거부된다`() {
            val json = tool.admin_retry_failed_delivery(deliveryLogId = " ")

            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { service.retryDelivery(any()) }
        }

        @Test
        fun `deliveryLogId 는 trim 해서 재시도 서비스에 전달한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.retryDelivery("log1") } returns Unit

            val json = tool.admin_retry_failed_delivery(deliveryLogId = " log1 ")

            json shouldContain "\"success\":true"
            json shouldContain "\"deliveryLogId\":\"log1\""
            verify(exactly = 1) { service.retryDelivery("log1") }
        }

        @Test
        fun `rate limit 초과 시 서비스는 호출되지 않는다`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_retry_failed_delivery",
                    maxRequests = 30,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_retry_failed_delivery(deliveryLogId = "log1")

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { service.retryDelivery(any()) }
        }
    }
}
