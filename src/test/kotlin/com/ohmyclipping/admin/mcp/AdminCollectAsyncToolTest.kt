package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.model.AsyncJobQueuedResult
import com.ohmyclipping.service.AsyncClipJobService
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
 * admin_collect_async 는 무거운 수집 작업을 큐에 넣는 MCP 도구다.
 * 명백히 잘못된 입력은 큐 적재와 rate limit 차감 전에 거부한다.
 */
class AdminCollectAsyncToolTest {

    private val asyncClipJobService = mockk<AsyncClipJobService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminCollectAsyncTool(asyncClipJobService, rateLimiter)

    @Nested
    inner class `admin_collect_async 입력 검증` {

        @Test
        fun `categoryId 는 trim 해서 큐와 rate limit dimension 에 전달한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { asyncClipJobService.enqueueCollect("cat-1", 12) } returns queuedResult()

            val json = tool.admin_collect_async(categoryId = " cat-1 ", hoursBack = 12)

            json shouldNotContain "\"error\""
            json shouldContain "\"jobType\":\"COLLECT\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow(
                    toolName = "admin_collect_async",
                    maxRequests = 20,
                    windowSeconds = 3600,
                    dimension = "cat-1",
                    actor = null,
                )
            }
            verify(exactly = 1) { asyncClipJobService.enqueueCollect("cat-1", 12) }
        }

        @Test
        fun `공백 categoryId 는 전체 수집으로 정규화한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { asyncClipJobService.enqueueCollect(null, null) } returns queuedResult()

            val json = tool.admin_collect_async(categoryId = " ", hoursBack = null)

            json shouldNotContain "\"error\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow(
                    toolName = "admin_collect_async",
                    maxRequests = 20,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            }
            verify(exactly = 1) { asyncClipJobService.enqueueCollect(null, null) }
        }

        @Test
        fun `0 이하 hoursBack 은 rate limit 차감 없이 validation error 로 거부한다`() {
            val json = tool.admin_collect_async(categoryId = "cat-1", hoursBack = 0)

            json shouldContain "\"error\""
            json shouldContain "hoursBack must be greater than 0"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }
    }

    private fun queuedResult() = AsyncJobQueuedResult(
        jobId = "job-1",
        jobType = "COLLECT",
        status = "PENDING",
    )
}
