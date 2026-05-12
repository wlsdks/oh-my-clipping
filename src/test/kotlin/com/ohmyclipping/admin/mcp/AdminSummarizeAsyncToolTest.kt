package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.model.AsyncJobQueuedResult
import com.ohmyclipping.service.AsyncClipJobService
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminSummarizeAsyncToolTest {

    private val asyncClipJobService = mockk<AsyncClipJobService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminSummarizeAsyncTool(asyncClipJobService, rateLimiter)

    @Nested
    inner class `admin_summarize_async 입력 정규화` {

        @Test
        fun `categoryId 는 trim 해서 큐에 전달한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { asyncClipJobService.enqueueSummarize("cat-1") } returns queuedResult()

            val json = tool.admin_summarize_async(categoryId = " cat-1 ")

            json shouldContain "\"jobType\":\"SUMMARIZE\""
            verify(exactly = 1) { asyncClipJobService.enqueueSummarize("cat-1") }
        }

        @Test
        fun `공백 categoryId 는 전체 요약으로 정규화한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { asyncClipJobService.enqueueSummarize(null) } returns queuedResult()

            val json = tool.admin_summarize_async(categoryId = " ")

            json shouldContain "\"jobType\":\"SUMMARIZE\""
            verify(exactly = 1) { asyncClipJobService.enqueueSummarize(null) }
        }
    }

    private fun queuedResult() = AsyncJobQueuedResult(
        jobId = "job-1",
        jobType = "SUMMARIZE",
        status = "PENDING",
    )
}
