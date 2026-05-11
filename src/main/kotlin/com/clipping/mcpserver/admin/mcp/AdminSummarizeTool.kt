package com.clipping.mcpserver.admin.mcp

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.mcp.McpRateLimiter
import com.clipping.mcpserver.mcp.mcpToolCall
import com.clipping.mcpserver.service.port.ClippingPipelinePort
import com.clipping.mcpserver.service.pipeline.toSummarizeResult
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — 미처리 RSS 아이템 AI 요약 동기 실행.
 */
@Component
class AdminSummarizeTool(
    private val clippingPipelinePort: ClippingPipelinePort,
    private val rateLimiter: McpRateLimiter
) {

    @Tool(
        description = """
            미처리 RSS 아이템을 AI 로 동기 요약한다.
            **언제 쓰나:** 특정 카테고리에 대해 AI 요약을 지금 바로 돌려달라는 요청이 들어왔을 때.
            **쓰지 말 것:** 배치가 크거나 오래 걸릴 가능성이 있을 때 — admin_summarize_async 를 사용.
            **결정 규칙 (동기 vs 비동기, 강제):** `categoryId` 필수. 전체 카테고리 요약(categoryId 생략) 은
                           **InvalidInputException** 으로 거부되며 admin_summarize_async 를 쓰라고 안내한다.
            **파라미터:** categoryId 필수.
            **반환:** 처리/실패 건수가 담긴 SummarizeResult.
        """,
    )
    fun admin_summarize(
        @ToolParam(description = "요약 대상 카테고리 ID (필수)", required = false) categoryId: String?,
    ): String = mcpToolCall {
        // 강제 가드레일: 전체 카테고리 요약은 시간이 오래 걸려 request timeout 을 유발한다 → async 강제.
        if (categoryId.isNullOrBlank()) {
            throw InvalidInputException(
                "동기 요약은 categoryId 가 필수다. 전체 카테고리 요약은 admin_summarize_async 를 사용하라."
            )
        }
        // 호출 빈도 제한: 최대 10회/시간 (전역).
        rateLimiter.checkOrThrow("admin_summarize", maxRequests = 10, windowSeconds = 3600)
        clippingPipelinePort.summarize(categoryId).toSummarizeResult()
    }
}
