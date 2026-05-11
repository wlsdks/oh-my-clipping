package com.ohmyclipping.admin.mcp

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

    @Tool(
        description = """
            최근 MCP 비동기 작업을 최신순으로 반환한다.
            **언제 쓰나:** 운영자가 "최근에 어떤 작업이 돌았지", "last N jobs", "최근 작업 뭐 돌았어" 를 물어볼 때.
            **쓰지 말 것:** 이미 job ID 를 알고 있고 그 상태만 필요할 때 — admin_job_status 를 사용.
            **파라미터:** limit 선택 (기본 10, 최대 50).
            **반환:** 최신순으로 정렬된 AsyncJobStatusResult 리스트.
        """,
    )
    fun admin_list_recent_jobs(
        @ToolParam(description = "최대 결과 수 (기본 10, 최대 50)", required = false) limit: Int?,
    ): String = mcpToolCall {
        // 입력 범위는 1~50으로 고정해 과도한 스캔을 막는다.
        val effectiveLimit = (limit ?: 10).coerceIn(1, 50)
        asyncClipJobService.listRecentJobs(effectiveLimit)
    }
}
