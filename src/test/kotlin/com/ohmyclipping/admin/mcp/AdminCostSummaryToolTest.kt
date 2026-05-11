package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.LlmCostService
import com.ohmyclipping.service.dto.admin.CostOverviewData
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * admin_cost_summary 단위 테스트.
 *
 * 검증 포인트:
 *  - 해피패스: 기본 7d 로 getOverview 를 호출하고 JSON 반환.
 *  - 잘못된 period 는 InvalidInputException (-32024).
 *  - rate limit 초과 시 서비스 미호출.
 */
class AdminCostSummaryToolTest {

    private val llmCostService = mockk<LlmCostService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminCostSummaryTool(llmCostService, rateLimiter)

    private val emptyOverview = CostOverviewData(
        from = LocalDate.of(2026, 4, 10),
        to = LocalDate.of(2026, 4, 16),
        totalCostUsd = 1.23,
        totalRequests = 42,
        dailyAvgRequests = 6.0,
        projectedMonthEndUsd = 5.5,
        previousPeriodCostUsd = 0.9,
        costChangePercent = 36.6,
        budgetUsd = 10.0,
        budgetUsedPercent = 12.3,
        dailyBreakdown = emptyList(),
    )

    @Nested
    inner class `admin_cost_summary` {

        @Test
        fun `period 생략 시 7d 로 getOverview 를 호출한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { llmCostService.getOverview(any(), any(), null) } returns emptyOverview

            val json = tool.admin_cost_summary(period = null)

            json shouldContain "\"totalCostUsd\":1.23"
            json shouldContain "\"totalRequests\":42"
            json shouldNotContain "\"error\""
            verify(exactly = 1) { llmCostService.getOverview(any(), any(), null) }
        }

        @Test
        fun `잘못된 period 는 validation error 로 거부된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val json = tool.admin_cost_summary(period = "30d")

            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { llmCostService.getOverview(any(), any(), any()) }
        }

        @Test
        fun `rate limit 초과 시 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_cost_summary",
                    maxRequests = 30,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_cost_summary(period = "7d")

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { llmCostService.getOverview(any(), any(), any()) }
        }
    }
}
