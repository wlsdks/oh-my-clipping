package com.clipping.mcpserver.admin.mcp

import com.clipping.mcpserver.mcp.mcpToolCall
import com.clipping.mcpserver.service.AsyncClipJobService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — 비동기 MCP 작업 상태 조회.
 */
@Component
class AdminJobStatusTool(private val asyncClipJobService: AsyncClipJobService) {

    @Tool(
        description = """
            ID 로 비동기 MCP 작업의 현재 상태를 조회한다.
            **언제 쓰나:** 이전에 큐잉한 작업이 완료 또는 실패했는지 확인하고 싶을 때.
            **쓰지 말 것:** 최근 작업 목록이 필요한 경우 — admin_list_recent_jobs 를 사용.
            **파라미터:** jobId 필수 (admin_collect_async / admin_summarize_async 가 반환한 값).
            **반환:** status, startedAt, finishedAt, result/error 가 담긴 AsyncJobStatusResult.
        """,
    )
    fun admin_job_status(
        @ToolParam(description = "비동기 도구에서 반환된 작업 ID") jobId: String,
    ): String = mcpToolCall {
        asyncClipJobService.getJobStatus(jobId)
    }
}
