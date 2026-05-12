package com.ohmyclipping.admin.mcp

import com.ohmyclipping.service.AsyncClipJobService
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminListRecentJobsToolTest {

    private val asyncClipJobService = mockk<AsyncClipJobService>()
    private val tool = AdminListRecentJobsTool(asyncClipJobService)

    @Nested
    inner class `admin_list_recent_jobs` {

        @Test
        fun `limit 이 null 이면 기본값 10 으로 최근 작업을 조회한다`() {
            every { asyncClipJobService.listRecentJobs(10) } returns emptyList()

            val json = tool.admin_list_recent_jobs(limit = null)

            json shouldNotContain "\"error\""
            verify(exactly = 1) { asyncClipJobService.listRecentJobs(10) }
        }

        @Test
        fun `범위 밖 limit 은 서비스 호출 없이 validation error 로 거부한다`() {
            val json = tool.admin_list_recent_jobs(limit = 51)

            json shouldContain "\"error\""
            json shouldContain "limit must be between 1 and 50"
            verify(exactly = 0) { asyncClipJobService.listRecentJobs(any()) }
        }
    }
}
