package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.port.ClippingQueryPort
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — 카테고리 일간 종합 요약 생성.
 */
@Component
class AdminDailySummaryTool(
    private val clippingQueryPort: ClippingQueryPort,
    private val rateLimiter: McpRateLimiter
) {

    @Tool(
        description = """
            오늘 생성된 배치 요약을 모아 단일 카테고리의 일간 종합 요약을 만든다.
            **언제 쓰나:** 한 카테고리에 대해 하루 단위로 롤업된 개요가 필요할 때.
            **쓰지 말 것:** 평소의 아이템 나열형 다이제스트가 필요할 때 — admin_pipeline 또는 admin_send_digest 를 사용.
            **파라미터:** categoryId 필수.
            **반환:** 생성된 텍스트가 담긴 DailySummaryResult.
        """,
    )
    fun admin_daily_summary(
        @ToolParam(description = "일간 요약을 생성할 카테고리 ID") categoryId: String,
    ): String = mcpToolCall {
        if (categoryId.isBlank()) {
            throw InvalidInputException("categoryId must not be blank")
        }

        // 호출 빈도 제한: 카테고리 단위로 최대 10회/일.
        rateLimiter.checkOrThrow(
            toolName = "admin_daily_summary",
            maxRequests = 10,
            windowSeconds = 86400,
            dimension = categoryId
        )
        clippingQueryPort.generateDailySummary(categoryId)
    }
}
