package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.dto.DtoSanitizer
import com.ohmyclipping.service.dto.clipping.OriginalContentResult
import com.ohmyclipping.service.dto.clipping.SummaryDetailResult
import com.ohmyclipping.service.port.ClippingQueryPort
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserSummaryDetailToolsTest {

    private val clippingQueryPort = mockk<ClippingQueryPort>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tools = UserSummaryDetailTools(clippingQueryPort, DtoSanitizer(), rateLimiter)

    @Nested
    inner class `user_get_summary_detail` {

        @Test
        fun `rate limit 확인 후 요약 상세를 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { clippingQueryPort.getSummaryDetail("s1") } returns summaryDetail()

            val json = tools.user_get_summary_detail(summaryId = "s1")

            json shouldContain "\"id\":\"s1\""
            json shouldContain "\"sourceName\":\"example.com\""
            json shouldNotContain "\"error\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("user_get_summary_detail", maxRequests = 60, windowSeconds = 3600)
            }
            verify(exactly = 1) { clippingQueryPort.getSummaryDetail("s1") }
        }

        @Test
        fun `rate limit 초과 시 상세 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "user_get_summary_detail",
                    maxRequests = 60,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tools.user_get_summary_detail(summaryId = "s1")

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { clippingQueryPort.getSummaryDetail(any()) }
        }
    }

    @Nested
    inner class `user_get_original_preview` {

        @Test
        fun `기존 원문 프리뷰 rate limit 계약을 유지한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { clippingQueryPort.getSummaryDetail("s1") } returns summaryDetail()
            every { clippingQueryPort.getOriginalContent("https://example.com/a") } returns originalContent()

            val json = tools.user_get_original_preview(summaryId = "s1")

            json shouldContain "\"summaryId\":\"s1\""
            json shouldContain "\"sourceName\":\"example.com\""
            json shouldNotContain "\"error\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("user_get_original_preview", maxRequests = 60, windowSeconds = 3600)
            }
        }
    }

    private fun summaryDetail() = SummaryDetailResult(
        id = "s1",
        categoryId = "c1",
        categoryName = "AI",
        originalTitle = "Original title",
        translatedTitle = "Translated title",
        summary = "Summary body",
        insights = null,
        keywords = listOf("AI"),
        importanceScore = 0.8f,
        sourceLink = "https://example.com/a",
        sentiment = null,
        eventType = null,
        isSentToSlack = false,
        contentPreview = "Preview body",
        createdAt = "2026-05-13T00:00:00Z",
    )

    private fun originalContent() = OriginalContentResult(
        found = true,
        sourceLink = "https://example.com/a",
        title = "Original title",
        markdown = "Original article preview text.",
        rssItemId = "r1",
        archivedAt = "2026-05-13T00:00:00Z",
    )
}
