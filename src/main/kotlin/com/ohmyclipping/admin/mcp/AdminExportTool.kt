package com.ohmyclipping.admin.mcp

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
 * 허용하고, limit 파라미터는 서버 측에서 1..500 범위로 클램핑한다.
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
              limit 선택 (기본 100, 서버에서 1..500 범위로 강제 클램핑).
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
            description = "내보낼 최대 레코드 수 (기본 100, 최대 500 으로 클램핑됨)",
            required = false,
        )
        limit: Int?,
    ): String = mcpToolCall {
        // 호출 빈도 제한: 카테고리 단위로 최대 5회/시간. 벌크 덤프 남용을 막는다.
        rateLimiter.checkOrThrow(
            toolName = "admin_export",
            maxRequests = MAX_REQUESTS_PER_HOUR,
            windowSeconds = WINDOW_SECONDS,
            dimension = categoryId,
        )
        // limit 은 서버 측에서 클램핑해 악의/실수로 수백만 건을 요청하는 경로를 봉쇄한다.
        val cappedLimit = limit?.coerceIn(MIN_LIMIT, MAX_LIMIT) ?: DEFAULT_LIMIT
        clippingQueryPort.exportSummaries(categoryId, daysBack, includeOriginal, cappedLimit)
    }

    private companion object {
        const val MAX_REQUESTS_PER_HOUR = 5
        const val WINDOW_SECONDS = 3600L
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 500
        const val DEFAULT_LIMIT = 100
    }
}
