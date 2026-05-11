package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.LlmCostService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * Admin 도구 — Gemini LLM 비용 요약.
 *
 * `/admin/cost` 페이지의 Overview 탭과 동일한 데이터를 반환하며,
 * LLM 이 "최근 비용 얼마야", "이번 주 토큰 많이 썼어?" 같은 질의에 즉답할 수 있도록 한다.
 */
@Component
class AdminCostSummaryTool(
    private val llmCostService: LlmCostService,
    private val rateLimiter: McpRateLimiter,
) {

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }

    @Tool(
        description = """
            LLM(Gemini) 비용 요약을 반환한다. `/admin/cost` 의 Overview 와 동일한 수치.
            **언제 쓰나:** 운영자가 "최근 비용 얼마야", "이번 주 토큰 많이 썼어?", "예산 대비 사용량" 을 물어볼 때.
            **쓰지 말 것:** 채널/모델/프롬프트별 상세 분해가 필요할 때 — 해당 쿼리는 별도 admin UI 로 이동.
            **파라미터:** period — "7d"|"14d"|"28d" (기본 "7d"). KST 달력 기준 최근 N 일.
            **반환:** totalCostUsd, totalRequests, projectedMonthEndUsd, 전기간 대비 변화율,
                     월간 예산 대비 사용률, 일별 breakdown.
        """,
    )
    fun admin_cost_summary(
        @ToolParam(
            description = "조회 기간 (7d|14d|28d, 기본 7d)",
            required = false,
        ) period: String?,
    ): String = mcpToolCall {
        // 빈도 제한: 30회/시간. 비용 요약은 가볍지만 과도 호출 방지.
        rateLimiter.checkOrThrow("admin_cost_summary", maxRequests = 30, windowSeconds = 3600)
        val days = parsePeriodDays(period ?: "7d")
        val today = LocalDate.now(KST)
        val from = today.minusDays(days - 1L)
        // categoryId=null → 전체 카테고리 합산. AdminCostController.overview 와 동일 로직.
        llmCostService.getOverview(from = from, to = today, categoryId = null)
    }

    /** "7d" / "14d" / "28d" 문자열을 일수로 변환한다. 범위 밖이면 InvalidInputException. */
    private fun parsePeriodDays(period: String): Int = when (period.trim()) {
        "7d" -> 7
        "14d" -> 14
        "28d" -> 28
        else -> throw InvalidInputException("period 는 '7d' / '14d' / '28d' 중 하나여야 합니다")
    }
}
