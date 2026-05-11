package com.clipping.mcpserver.admin.mcp

import com.clipping.mcpserver.service.source.SourceHealthService
import com.clipping.mcpserver.service.dto.SourceHealthResponse
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * admin_list_failing_sources 단위 테스트.
 *
 * - hours 파라미터가 SourceHealthService.getHealth(staleHours=...) 로 전달되는지
 * - 범위 밖 hours 는 InvalidInputException → JSON-RPC 에러로 변환되는지
 * - hours 미지정 시 null 이 그대로 전달되는지 (기본 24시간 폴백)
 */
class AdminListFailingSourcesToolTest {

    private val sourceHealthService = mockk<SourceHealthService>()
    private val tool = AdminListFailingSourcesTool(sourceHealthService)

    private val emptyResponse = SourceHealthResponse(
        totalCount = 0,
        healthyCount = 0,
        unhealthy = emptyList(),
    )

    @Nested
    inner class `hours 파라미터 전달` {

        @Test
        fun `hours 값이 있으면 staleHours 로 SourceHealthService 에 전달된다`() {
            every { sourceHealthService.getHealth(staleHours = 6) } returns emptyResponse

            tool.admin_list_failing_sources(hours = 6)

            verify(exactly = 1) { sourceHealthService.getHealth(staleHours = 6) }
        }

        @Test
        fun `hours 가 null 이면 staleHours=null 로 호출되어 기본값을 폴백한다`() {
            every { sourceHealthService.getHealth(staleHours = null) } returns emptyResponse

            tool.admin_list_failing_sources(hours = null)

            verify(exactly = 1) { sourceHealthService.getHealth(staleHours = null) }
        }
    }

    @Nested
    inner class `hours 범위 검증` {

        @Test
        fun `0 이하 hours 는 InvalidInputException 으로 거부된다`() {
            val json = tool.admin_list_failing_sources(hours = 0)

            // mcpToolCall 은 InvalidInputException 을 JSON-RPC 에러로 감싼다.
            json shouldContain "\"error\""
            json shouldContain "hours must be between"
            verify(exactly = 0) { sourceHealthService.getHealth(any()) }
        }

        @Test
        fun `720 초과 hours 는 InvalidInputException 으로 거부된다`() {
            val json = tool.admin_list_failing_sources(hours = 721)

            json shouldContain "\"error\""
            json shouldContain "hours must be between"
            verify(exactly = 0) { sourceHealthService.getHealth(any()) }
        }

        @Test
        fun `경계값 1 과 720 은 허용된다`() {
            every { sourceHealthService.getHealth(staleHours = 1) } returns emptyResponse
            every { sourceHealthService.getHealth(staleHours = 720) } returns emptyResponse

            tool.admin_list_failing_sources(hours = 1)
            tool.admin_list_failing_sources(hours = 720)

            verify(exactly = 1) { sourceHealthService.getHealth(staleHours = 1) }
            verify(exactly = 1) { sourceHealthService.getHealth(staleHours = 720) }
        }
    }
}
