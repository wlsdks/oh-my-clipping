package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.AnalyticsContentLeversService
import com.ohmyclipping.service.dto.ContentLeversSummary
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * admin_content_levers_summary 단위 테스트.
 *
 * 검증 포인트:
 *  - 해피패스: 기본 28d period 로 service.summary 를 호출하고 JSON 을 반환한다.
 *  - period 범위 밖 문자열은 InvalidInputException.
 *  - rate limit 초과 시 JSON-RPC 에러.
 */
class AdminContentLeversSummaryToolTest {

    private val service = mockk<AnalyticsContentLeversService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminContentLeversSummaryTool(service, rateLimiter)

    private val emptySummary = ContentLeversSummary(
        sourceQuality = emptyList(),
    )

    @Nested
    inner class `admin_content_levers_summary` {

        @Test
        fun `period 를 생략하면 28d 로 서비스를 호출한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.summary(any(), any()) } returns emptySummary

            val json = tool.admin_content_levers_summary(period = null)

            json shouldContain "\"sourceQuality\""
            json shouldNotContain "\"error\""
            json shouldNotContain "\"personaSatisfaction\""
            verify(exactly = 1) { service.summary(any(), any()) }
        }

        @Test
        fun `잘못된 period 는 JSON-RPC validation error 로 거부된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val json = tool.admin_content_levers_summary(period = "30d")

            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { service.summary(any(), any()) }
        }

        @Test
        fun `rate limit 초과 시 서비스는 호출되지 않는다`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_content_levers_summary",
                    maxRequests = 30,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_content_levers_summary(period = "7d")

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { service.summary(any(), any()) }
        }

        @Test
        fun `period=7d 는 7일 구간으로 서비스를 호출한다`() {
            val fromSlot = slot<Instant>()
            val toSlot = slot<Instant>()
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.summary(capture(fromSlot), capture(toSlot)) } returns emptySummary

            tool.admin_content_levers_summary(period = "7d")

            Duration.between(fromSlot.captured, toSlot.captured).toDays() shouldBe 7
        }
    }
}
