package com.clipping.mcpserver.admin

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.service.AnalyticsContentLeversService
import com.clipping.mcpserver.service.dto.ContentLeversSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 콘텐츠 레버 대시보드 API (소스 품질 단일 레버).
 * 기간은 "7d" / "14d" / "28d" / "90d" 문자열 중 하나 (KST 달력 기준).
 */
@RestController
@RequestMapping("/api/admin/analytics/content-levers")
class AnalyticsContentLeversController(
    private val service: AnalyticsContentLeversService,
) {

    @GetMapping("/summary")
    fun summary(
        @RequestParam(defaultValue = "28d") period: String
    ): ContentLeversSummary {
        val (from, to) = parsePeriod(period)
        return service.summary(from, to)
    }

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
