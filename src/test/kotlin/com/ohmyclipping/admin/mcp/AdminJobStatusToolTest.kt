package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.model.AsyncJobStatusResult
import com.ohmyclipping.service.AsyncClipJobService
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminJobStatusToolTest {

    private val asyncClipJobService = mockk<AsyncClipJobService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminJobStatusTool(asyncClipJobService, rateLimiter)

    @Nested
    inner class `admin_job_status 입력 검증` {

        @Test
        fun `jobId 는 trim 해서 조회한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { asyncClipJobService.getJobStatus("job-1") } returns statusResult()

            val json = tool.admin_job_status(jobId = " job-1 ")

            json shouldContain "\"id\":\"job-1\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("admin_job_status", maxRequests = 120, windowSeconds = 3600)
            }
            verify(exactly = 1) { asyncClipJobService.getJobStatus("job-1") }
        }

        @Test
        fun `빈 jobId 는 서비스 호출 없이 validation error 로 거부한다`() {
            val json = tool.admin_job_status(jobId = " ")

            json shouldContain "\"error\""
            json shouldContain "jobId is required"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { asyncClipJobService.getJobStatus(any()) }
        }

        @Test
        fun `rate limit 초과 시 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_job_status",
                    maxRequests = 120,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_job_status(jobId = "job-1")

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { asyncClipJobService.getJobStatus(any()) }
        }
    }

    private fun statusResult() = AsyncJobStatusResult(
        id = "job-1",
        jobType = "COLLECT",
        status = "PENDING",
        attempts = 0,
        maxAttempts = 3,
        nextRunAt = "2026-05-13T00:00:00Z",
        lastError = null,
        resultJson = null,
        createdAt = "2026-05-13T00:00:00Z",
        updatedAt = "2026-05-13T00:00:00Z",
    )
}
