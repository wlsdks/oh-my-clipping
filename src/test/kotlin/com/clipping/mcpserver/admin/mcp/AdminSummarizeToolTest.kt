package com.clipping.mcpserver.admin.mcp

import com.clipping.mcpserver.service.dto.clipping.SummarizeResult
import com.clipping.mcpserver.mcp.McpRateLimiter
import com.clipping.mcpserver.service.port.ClippingPipelinePort
import com.clipping.mcpserver.service.pipeline.toPipelineSummarizeResult
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
 * admin_summarize 단위 테스트 — PR-06 sync guardrail 추가 후.
 *
 * 검증 포인트:
 *  - 해피패스: categoryId 가 주어지면 ClippingService.summarize 호출.
 *  - categoryId 없으면 InvalidInputException 으로 차단 (전체 요약 금지).
 *  - 가드레일 실패 시 rate limiter 호출도 없어야 한다 (불필요한 쿼터 소비 방지).
 */
class AdminSummarizeToolTest {

    private val clippingService = mockk<ClippingPipelinePort>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminSummarizeTool(clippingService, rateLimiter)

    private val result = SummarizeResult(
        totalSummarized = 5,
        categories = emptyList(),
    )

    @Nested
    inner class `sync guardrail` {

        @Test
        fun `해피패스 — categoryId 명시 시 summarize 호출`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { clippingService.summarize("c1") } returns result.toPipelineSummarizeResult()

            val json = tool.admin_summarize(categoryId = "c1")

            json shouldContain "\"totalSummarized\":5"
            json shouldNotContain "\"error\""
            verify(exactly = 1) { clippingService.summarize("c1") }
        }

        @Test
        fun `categoryId 가 없으면 InvalidInputException 으로 차단된다`() {
            val json = tool.admin_summarize(categoryId = null)

            json shouldContain "\"error\""
            json shouldContain "admin_summarize_async"
            verify(exactly = 0) { clippingService.summarize(any()) }
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `categoryId 가 공백이면 InvalidInputException 으로 차단된다`() {
            val json = tool.admin_summarize(categoryId = "   ")

            json shouldContain "\"error\""
            json shouldContain "admin_summarize_async"
            verify(exactly = 0) { clippingService.summarize(any()) }
        }
    }
}
