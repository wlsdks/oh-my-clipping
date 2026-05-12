package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.AsyncClipJobService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — 최근 MCP 비동기 작업 목록.
 *
 * 운영자가 "최근 어떤 job이 돌았나" 확인할 때 사용한다.
 */
@Component
class AdminListRecentJobsTool(private val asyncClipJobService: AsyncClipJobService) {

    private companion object {
        const val DEFAULT_LIMIT = 10
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 50
    }

    @Tool(
        description = """
            최근 MCP 비동기 작업을 최신순으로 반환한다.
            **언제 쓰나:** 운영자가 "최근에 어떤 작업이 돌았지", "last N jobs", "최근 작업 뭐 돌았어" 를 물어볼 때.
            **쓰지 말 것:** 이미 job ID 를 알고 있고 그 상태만 필요할 때 — admin_job_status 를 사용.
            **파라미터:** limit 선택 (1~50, 기본 10).
            **반환:** 최신순으로 정렬된 AsyncJobStatusResult 리스트.
        """,
    )
    fun admin_list_recent_jobs(
        @ToolParam(description = "최대 결과 수 (1~50, 기본 10)", required = false) limit: Int?,
    ): String = mcpToolCall {
        val effectiveLimit = validateLimit(limit)
        asyncClipJobService.listRecentJobs(effectiveLimit)
    }

    private fun validateLimit(limit: Int?): Int {
        val effective = limit ?: DEFAULT_LIMIT
        if (effective !in MIN_LIMIT..MAX_LIMIT) {
            throw InvalidInputException("limit must be between $MIN_LIMIT and $MAX_LIMIT")
        }
        return effective
    }
}
