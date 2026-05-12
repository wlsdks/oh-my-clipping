package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.dto.clipping.DailySummaryResult
import com.ohmyclipping.service.port.ClippingQueryPort
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * admin_daily_summary 입력 가드레일 테스트.
 *
 * 일간 요약 생성은 비멱등 MCP 도구이므로 명백한 입력 오류는
 * rate limit 차감과 서비스 호출 전에 거부한다.
 */
class AdminDailySummaryToolTest {

    private val clippingQueryPort = mockk<ClippingQueryPort>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminDailySummaryTool(clippingQueryPort, rateLimiter)

    @Test
    fun `categoryId 는 trim 해서 rate limit dimension 과 서비스에 전달한다`() {
        every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
        every { clippingQueryPort.generateDailySummary("cat-1") } returns DailySummaryResult(
            id = "summary-1",
            title = "daily summary",
            totalItems = 3,
            summaryDate = "2026-05-13",
            topicKeywords = listOf("AI"),
            categoryId = "cat-1",
        )

        val json = tool.admin_daily_summary(categoryId = " cat-1 ")

        json shouldContain "daily summary"
        verify(exactly = 1) {
            rateLimiter.checkOrThrow(
                toolName = "admin_daily_summary",
                maxRequests = 10,
                windowSeconds = 86400,
                dimension = "cat-1",
                actor = null,
            )
        }
        verify(exactly = 1) { clippingQueryPort.generateDailySummary("cat-1") }
    }

    @Test
    fun `빈 categoryId 는 rate limit 차감 없이 validation error 로 거부된다`() {
        val json = tool.admin_daily_summary(categoryId = " ")

        json shouldContain "\"error\""
        json shouldContain "categoryId must not be blank"
        verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { clippingQueryPort.generateDailySummary(any()) }
    }
}
