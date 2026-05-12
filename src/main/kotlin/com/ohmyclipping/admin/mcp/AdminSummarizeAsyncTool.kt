package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.AsyncClipJobService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — AI 요약 비동기 큐잉.
 */
@Component
class AdminSummarizeAsyncTool(
    private val asyncClipJobService: AsyncClipJobService,
    private val rateLimiter: McpRateLimiter
) {

    @Tool(
        description = """
            AI 요약을 비동기 큐에 등록한다.
            **언제 쓰나:** 오래 걸릴 수 있는 AI 요약 작업을 백그라운드에서 돌리고 싶을 때.
            **쓰지 말 것:** 지금 당장 요약 건수를 확인해야 할 때 — admin_summarize 를 사용.
            **결정 규칙 (동기 vs 비동기):** 전체 카테고리이거나 배치 크기를 모르면 이 도구가 기본값.
                           categoryId 를 명시한 짧은 테스트 요약에 한해 admin_summarize 를 쓴다.
            **파라미터:** categoryId 선택 (생략 시 전체).
            **반환:** jobId 와 PENDING 상태가 담긴 AsyncJobQueuedResult.
        """,
    )
    fun admin_summarize_async(
        @ToolParam(description = "요약 대상 카테고리 ID (생략 시 전체)", required = false) categoryId: String?,
    ): String = mcpToolCall {
        val normalizedCategoryId = categoryId?.trim()?.takeIf { it.isNotBlank() }

        // 호출 빈도 제한: 최대 10회/시간 (전역).
        rateLimiter.checkOrThrow("admin_summarize_async", maxRequests = 10, windowSeconds = 3600)
        asyncClipJobService.enqueueSummarize(normalizedCategoryId)
    }
}
