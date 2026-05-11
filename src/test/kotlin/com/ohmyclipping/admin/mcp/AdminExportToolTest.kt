package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.dto.clipping.ExportResult
import com.ohmyclipping.service.port.ClippingQueryPort
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * admin_export 단위 테스트.
 *
 * 검증 포인트:
 *  - 레이트 리밋(5회/시간, categoryId 단위)이 호출 맨 앞에서 강제된다.
 *  - limit 파라미터가 서버 측에서 1..500 범위로 클램핑되어 서비스로 전달된다.
 *  - 레이트 리밋 초과 시 RateLimitExceededException 이 JSON-RPC 에러로 매핑된다.
 */
class AdminExportToolTest {

    private val clippingService = mockk<ClippingQueryPort>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminExportTool(clippingService, rateLimiter)

    private val emptyResult = ExportResult(
        categoryId = "c1",
        exportedAt = java.time.Instant.now().toString(),
        daysBack = null,
        includeOriginal = false,
        count = 0,
        records = emptyList(),
    )

    @Nested
    inner class `admin_export 호출 시` {

        @Test
        fun `rate limiter 를 categoryId 단위로 호출한다`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_export",
                    maxRequests = 5,
                    windowSeconds = 3600L,
                    dimension = "c1",
                )
            } just Runs
            every { clippingService.exportSummaries(any(), any(), any(), any()) } returns emptyResult

            tool.admin_export(categoryId = "c1", daysBack = null, includeOriginal = null, limit = null)

            verify(exactly = 1) {
                rateLimiter.checkOrThrow(
                    toolName = "admin_export",
                    maxRequests = 5,
                    windowSeconds = 3600L,
                    dimension = "c1",
                )
            }
        }

        @Test
        fun `limit 이 500 을 초과하면 500 으로 클램핑된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            val capturedLimit = slot<Int?>()
            every {
                clippingService.exportSummaries(any(), any(), any(), captureNullable(capturedLimit))
            } returns emptyResult

            tool.admin_export(categoryId = "c1", daysBack = null, includeOriginal = null, limit = 10_000)

            assert(capturedLimit.captured == 500) {
                "limit 10000 은 500 으로 클램핑되어야 하지만 ${capturedLimit.captured}"
            }
        }

        @Test
        fun `limit 이 0 이하이면 1 로 클램핑된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            val capturedLimit = slot<Int?>()
            every {
                clippingService.exportSummaries(any(), any(), any(), captureNullable(capturedLimit))
            } returns emptyResult

            tool.admin_export(categoryId = "c1", daysBack = null, includeOriginal = null, limit = 0)

            assert(capturedLimit.captured == 1) {
                "limit 0 은 1 로 클램핑되어야 하지만 ${capturedLimit.captured}"
            }
        }

        @Test
        fun `limit 이 null 이면 기본값 100 이 전달된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            val capturedLimit = slot<Int?>()
            every {
                clippingService.exportSummaries(any(), any(), any(), captureNullable(capturedLimit))
            } returns emptyResult

            tool.admin_export(categoryId = "c1", daysBack = null, includeOriginal = null, limit = null)

            assert(capturedLimit.captured == 100)
        }

        @Test
        fun `rate limit 초과 시 JSON-RPC 에러로 감싸진다`() {
            every {
                rateLimiter.checkOrThrow(any(), any(), any(), any(), any())
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_export(
                categoryId = "c1",
                daysBack = null,
                includeOriginal = null,
                limit = null,
            )

            json shouldContain "\"error\""
            // RateLimitExceededException 은 McpErrorCode.RATE_LIMITED(-32022) 로 매핑된다.
            json shouldContain "-32022"
            verify(exactly = 0) { clippingService.exportSummaries(any(), any(), any(), any()) }
        }
    }
}
