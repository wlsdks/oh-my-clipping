package com.ohmyclipping.admin.mcp

import com.ohmyclipping.model.AsyncJobStatusResult
import com.ohmyclipping.service.AsyncClipJobService
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminJobStatusToolTest {

    private val asyncClipJobService = mockk<AsyncClipJobService>()
    private val tool = AdminJobStatusTool(asyncClipJobService)

    @Nested
    inner class `admin_job_status 입력 검증` {

        @Test
        fun `jobId 는 trim 해서 조회한다`() {
            every { asyncClipJobService.getJobStatus("job-1") } returns statusResult()

            val json = tool.admin_job_status(jobId = " job-1 ")

            json shouldContain "\"id\":\"job-1\""
            verify(exactly = 1) { asyncClipJobService.getJobStatus("job-1") }
        }

        @Test
        fun `빈 jobId 는 서비스 호출 없이 validation error 로 거부한다`() {
            val json = tool.admin_job_status(jobId = " ")

            json shouldContain "\"error\""
            json shouldContain "jobId is required"
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
