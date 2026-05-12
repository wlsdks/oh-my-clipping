package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.port.ClippingQueryPort
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — 카테고리 단위 요약 레코드 대량 내보내기.
 *
 * 벌크 덤프는 DB / 스토리지 부하가 크므로 카테고리 단위로 **최대 5회/시간** 까지만
 * 허용하고, limit 파라미터는 1..500 범위 밖이면 명확한 입력 오류로 거부한다.
 */
@Component
class AdminExportTool(
    private val clippingQueryPort: ClippingQueryPort,
    private val rateLimiter: McpRateLimiter,
) {

    @Tool(
        description = """
            카테고리의 요약 레코드를 대량으로 내보낸다 (운영자 벌크 export).
            **언제 쓰나:** 분석이나 감사 목적으로 여러 건의 원본 레코드 덤프가 필요할 때.
            **쓰지 말 것:** 일반 사용자가 최근 요약을 보려고 할 때 — user_list_recent_summaries 를 사용.
            **파라미터:** categoryId 필수, daysBack 선택, includeOriginal 선택 (기본 false),
              limit 선택 (1~500, 기본 100).
            **Rate limit:** 카테고리 단위로 최대 5회/시간. 초과 시 Retry-After 헤더와 함께 거부된다.
            **반환:** 레코드 배열이 담긴 ExportResult.
        """,
    )
    fun admin_export(
        @ToolParam(description = "내보낼 대상 카테고리 ID") categoryId: String,
        @ToolParam(description = "최근 N일 이내 레코드만 포함", required = false) daysBack: Int?,
        @ToolParam(
            description = "각 레코드에 원본 마크다운을 포함할지 여부 (기본 false)",
            required = false,
        )
        includeOriginal: Boolean?,
        @ToolParam(
            description = "내보낼 최대 레코드 수 (1~500, 기본 100)",
            required = false,
        )
        limit: Int?,
    ): String = mcpToolCall {
        val effectiveLimit = validateLimit(limit)
        // 호출 빈도 제한: 카테고리 단위로 최대 5회/시간. 벌크 덤프 남용을 막는다.
        rateLimiter.checkOrThrow(
            toolName = "admin_export",
            maxRequests = MAX_REQUESTS_PER_HOUR,
            windowSeconds = WINDOW_SECONDS,
            dimension = categoryId,
        )
        clippingQueryPort.exportSummaries(categoryId, daysBack, includeOriginal, effectiveLimit)
    }

    private fun validateLimit(limit: Int?): Int {
        val effective = limit ?: DEFAULT_LIMIT
        if (effective !in MIN_LIMIT..MAX_LIMIT) {
            throw InvalidInputException("limit must be between $MIN_LIMIT and $MAX_LIMIT")
        }
        return effective
    }

    private companion object {
        const val MAX_REQUESTS_PER_HOUR = 5
        const val WINDOW_SECONDS = 3600L
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 500
        const val DEFAULT_LIMIT = 100
    }
}
