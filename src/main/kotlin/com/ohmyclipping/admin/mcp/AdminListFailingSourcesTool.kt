package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.source.SourceHealthService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — RSS 소스 헬스 상태 조회.
 *
 * 운영자가 "어느 소스가 깨졌는지" 확인할 때 사용한다. `hours` 를 지정하면
 * 기본 24시간 임계값 대신 해당 시간을 초과해 미수신된 소스까지 포함한다.
 */
@Component
class AdminListFailingSourcesTool(
    private val sourceHealthService: SourceHealthService,
    private val rateLimiter: McpRateLimiter,
) {

    companion object {
        /** 최소 허용 시간. 0 이하 값은 의미가 없고 서비스가 기본값으로 폴백하도록 거부한다. */
        private const val MIN_HOURS = 1

        /** 최대 허용 시간 = 30일. 더 긴 기간을 조회할 일은 없다고 가정한다. */
        private const val MAX_HOURS = 24 * 30
    }

    @Tool(
        description = """
            헬스 실패 상태인 RSS 소스 목록을 반환한다.
            **언제 쓰나:** 운영자가 "어느 소스가 망가졌지", "failing sources", "깨진 소스" 를 물어볼 때.
            **쓰지 말 것:** 수집을 직접 돌리려는 경우 — admin_collect 를 사용.
            **파라미터:** hours 선택 (1~720, 기본 24). 해당 시간 내에 수신 성공이 없는 소스를 불건강으로 판정한다.
            **반환:** 비정상 소스 리스트가 담긴 SourceHealthResponse.
        """,
    )
    fun admin_list_failing_sources(
        @ToolParam(description = "불건강 판정 임계 시간 (1~720, 기본 24)", required = false) hours: Int?,
    ): String = mcpToolCall {
        // 입력 범위를 먼저 검증해 비정상 값이 서비스까지 흘러가지 않도록 한다.
        val normalized = hours?.also {
            if (it !in MIN_HOURS..MAX_HOURS) {
                throw InvalidInputException("hours must be between $MIN_HOURS and $MAX_HOURS")
            }
        }
        rateLimiter.checkOrThrow(
            toolName = "admin_list_failing_sources",
            maxRequests = 60,
            windowSeconds = 3600,
            dimension = normalized?.toString(),
        )
        sourceHealthService.getHealth(staleHours = normalized)
    }
}
