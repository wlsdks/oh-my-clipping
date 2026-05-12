package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.AdminClippingService
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * admin_pipeline 입력 가드레일 테스트.
 *
 * 파이프라인은 수집/요약/digest 를 한 번에 실행하는 고비용 MCP 도구이므로
 * 명백히 잘못된 입력은 rate limit 차감과 서비스 호출 전에 거부한다.
 */
class AdminPipelineToolTest {

    private val adminClippingService = mockk<AdminClippingService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminPipelineTool(adminClippingService, rateLimiter)

    @Nested
    inner class `admin_pipeline 입력 검증` {

        @Test
        fun `빈 categoryId 는 rate limit 차감 없이 validation error 로 거부된다`() {
            val json = tool.admin_pipeline(
                categoryId = " ",
                hoursBack = null,
                maxItems = null,
                unsentOnly = null,
                _ralphLoopEnabled = null,
                _ralphLoopMaxIterations = null,
                _ralphLoopStopPhrase = null,
            )

            json shouldContain "\"error\""
            json shouldContain "categoryId must not be blank"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) {
                adminClippingService.runPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `0 이하 hoursBack 은 rate limit 차감 없이 validation error 로 거부된다`() {
            val json = tool.admin_pipeline(
                categoryId = "c1",
                hoursBack = 0,
                maxItems = null,
                unsentOnly = null,
                _ralphLoopEnabled = null,
                _ralphLoopMaxIterations = null,
                _ralphLoopStopPhrase = null,
            )

            json shouldContain "\"error\""
            json shouldContain "hoursBack must be greater than 0"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) {
                adminClippingService.runPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `maxItems 범위 밖이면 rate limit 차감 없이 validation error 로 거부된다`() {
            val json = tool.admin_pipeline(
                categoryId = "c1",
                hoursBack = null,
                maxItems = 6,
                unsentOnly = null,
                _ralphLoopEnabled = null,
                _ralphLoopMaxIterations = null,
                _ralphLoopStopPhrase = null,
            )

            json shouldContain "\"error\""
            json shouldContain "maxItems must be between 1 and 5"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) {
                adminClippingService.runPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }
    }
}
