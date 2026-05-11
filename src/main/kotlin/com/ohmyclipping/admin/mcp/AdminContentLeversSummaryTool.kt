package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.AnalyticsContentLeversService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Admin 도구 — 콘텐츠 레버 대시보드 요약 (소스 품질 단일 레버).
 *
 * 기존 [AnalyticsContentLeversController] 의 `/summary` 엔드포인트와 동일한 기간 규칙을 따른다.
 * 기간은 "7d"|"14d"|"28d"|"90d" 중 하나이며 KST 달력 기준으로 [from, to) 구간을 만든다.
 */
@Component
class AdminContentLeversSummaryTool(
    private val analyticsContentLeversService: AnalyticsContentLeversService,
    private val rateLimiter: McpRateLimiter,
) {

    @Tool(
        description = """
            콘텐츠 레버 대시보드 요약을 반환한다 (RSS 소스 품질 집계).
            **언제 쓰나:** 운영자가 "소스 품질 현황", "콘텐츠 레버 지표" 를 물어볼 때.
            **쓰지 말 것:** 특정 카테고리만 필요한 경우 — 상세 분석은 별도 admin API 를 사용.
            **파라미터:** period — "7d"|"14d"|"28d"|"90d" 중 하나 (기본 "28d").
            **반환:** sourceQuality 배열이 담긴 ContentLeversSummary.
        """,
    )
    fun admin_content_levers_summary(
        @ToolParam(
            description = "조회 기간 (7d|14d|28d|90d, 기본 28d)",
            required = false,
        ) period: String?,
    ): String = mcpToolCall {
        // 빈도 제한: 30회/시간 — 대시보드 집계 쿼리는 비용이 중간 수준.
        rateLimiter.checkOrThrow("admin_content_levers_summary", maxRequests = 30, windowSeconds = 3600)
        val (from, to) = parsePeriod(period ?: "28d")
        analyticsContentLeversService.summary(from, to)
    }

    /**
     * "7d" / "14d" / "28d" / "90d" 문자열을 KST 기준 [from, to) Instant 쌍으로 변환한다.
     * 기존 AnalyticsContentLeversController 와 동일한 규칙을 유지한다.
     */
    private fun parsePeriod(period: String): Pair<Instant, Instant> {
        val kst = ZoneId.of("Asia/Seoul")
        val clock = Clock.systemUTC()
        val today = LocalDate.now(clock.withZone(kst))
        val to = today.plusDays(1).atStartOfDay(kst).toInstant()
        val days = when (period) {
            "7d" -> 7L
            "14d" -> 14L
            "28d" -> 28L
            "90d" -> 90L
            else -> throw InvalidInputException(
                "period 는 '7d' / '14d' / '28d' / '90d' 중 하나여야 합니다"
            )
        }
        // 오늘을 포함한 N일 구간이 되도록 시작일은 N-1일 전으로 잡는다.
        val from = today.minusDays(days - 1).atStartOfDay(kst).toInstant()
        return from to to
    }
}
