package com.clipping.mcpserver.admin.mcp

import com.clipping.mcpserver.mcp.McpRateLimiter
import com.clipping.mcpserver.mcp.mcpToolCall
import com.clipping.mcpserver.service.AsyncClipJobService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — RSS 수집 비동기 큐잉.
 *
 * 호출 즉시 job ID를 반환하고 실제 수집은 백그라운드에서 수행한다.
 * 진행 상황은 [AdminJobStatusTool]로 조회한다.
 */
@Component
class AdminCollectAsyncTool(
    private val asyncClipJobService: AsyncClipJobService,
    private val rateLimiter: McpRateLimiter
) {

    @Tool(
        description = """
            RSS 수집을 비동기 큐에 등록한다.
            **언제 쓰나:** 오래 걸릴 수 있는 수집 작업을 대화 흐름을 막지 않고 백그라운드에서 돌리고 싶을 때.
            **쓰지 말 것:** 같은 응답 안에서 바로 수집 건수를 받아야 할 때 — admin_collect 를 사용.
            **결정 규칙 (동기 vs 비동기):** 전체 카테고리이거나 hoursBack > 6 이거나 불확실하면 이 도구가 기본값.
                           categoryId 명시 + hoursBack ≤ 6 인 짧은 테스트에 한해서만 admin_collect 를 쓴다.
            **파라미터:** categoryId 선택 (생략 시 전체 카테고리), hoursBack 선택 (생략 시 서버 기본값).
            **반환:** jobId 와 PENDING 상태가 담긴 AsyncJobQueuedResult.
        """,
    )
    fun admin_collect_async(
        @ToolParam(description = "수집 대상 카테고리 ID (생략 시 전체)", required = false) categoryId: String?,
        @ToolParam(description = "몇 시간 이전까지 조회할지 (생략 시 서버 기본값)", required = false) hoursBack: Int?,
    ): String = mcpToolCall {
        // 호출 빈도 제한: 카테고리 단위로 최대 20회/시간. 전체 수집은 dimension null.
        rateLimiter.checkOrThrow(
            toolName = "admin_collect_async",
            maxRequests = 20,
            windowSeconds = 3600,
            dimension = categoryId
        )
        asyncClipJobService.enqueueCollect(categoryId, hoursBack)
    }
}
