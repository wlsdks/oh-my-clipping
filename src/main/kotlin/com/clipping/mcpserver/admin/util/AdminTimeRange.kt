package com.clipping.mcpserver.admin.util

import com.clipping.mcpserver.error.InvalidInputException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 어드민 목록 API 의 `within` 파라미터를 KST 달력 기준 `Instant` 하한으로 변환한다.
 *
 * - `"1d"` → 오늘 KST 자정 (오늘 00:00:00 KST 이후 데이터)
 * - `"7d"` → 오늘 KST 자정의 6일 전 (오늘 포함 최근 7일 KST)
 *
 * `Clock` 을 주입받아 테스트 시 시간 고정이 가능하다. 프로덕션에서는 `Clock.systemUTC()` 기본값을 사용.
 */
object AdminTimeRange {
    private val KST: ZoneId = ZoneId.of("Asia/Seoul")

    fun parseWithin(within: String?, clock: Clock = Clock.systemUTC()): Instant? {
        if (within == null) return null
        // clock 이 UTC 든 상관없이 KST 현재 시각 → 오늘 KST 날짜 → KST 자정으로 floor
        val todayKstMidnight = clock.instant()
            .atZone(KST)
            .toLocalDate()
            .atStartOfDay(KST)
            .toInstant()
        return when (within) {
            "1d" -> todayKstMidnight
            "7d" -> todayKstMidnight.minus(6L, ChronoUnit.DAYS)
            else -> throw InvalidInputException("within 파라미터는 '1d' 또는 '7d' 만 허용합니다")
        }
    }
}
