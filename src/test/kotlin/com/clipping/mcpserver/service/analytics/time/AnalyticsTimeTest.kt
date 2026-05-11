package com.clipping.mcpserver.service.analytics.time

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * AnalyticsTime 의 KST 주 경계 계산 검증.
 *
 * 이 6 케이스는 페르소나 분석 주간 집계의 정확도를 보장하는 핵심 회귀 가드다.
 * 일요일 23 시 / 월요일 0 시 / 토요일 1 시 등 경계 시각이 잘못 분류되면
 * 모든 weekly_persona_snapshot row 가 한 주씩 어긋나므로 절대 깨져서는 안 된다.
 */
class AnalyticsTimeTest {

    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `KST 월요일 00 시 00 분은 해당 주의 시작`() {
        val mondayMidnightKst = ZonedDateTime.of(
            2026, 4, 6, 0, 0, 0, 0, kst
        ).toInstant()

        assertThat(AnalyticsTime.weekStartOf(mondayMidnightKst))
            .isEqualTo(LocalDate.of(2026, 4, 6))
    }

    @Test
    fun `일요일 23 시 59 분 KST 는 이전 주에 속함`() {
        val sundayLateKst = ZonedDateTime.of(
            2026, 4, 5, 23, 59, 0, 0, kst
        ).toInstant()

        assertThat(AnalyticsTime.weekStartOf(sundayLateKst))
            .isEqualTo(LocalDate.of(2026, 3, 30))
    }

    @Test
    fun `토요일 01 시 KST 는 해당 주에 속함`() {
        val saturdayEarlyKst = ZonedDateTime.of(
            2026, 4, 11, 1, 0, 0, 0, kst
        ).toInstant()

        assertThat(AnalyticsTime.weekStartOf(saturdayEarlyKst))
            .isEqualTo(LocalDate.of(2026, 4, 6))
    }

    @Test
    fun `previousWeekStart 는 항상 직전 완료 주의 월요일을 반환`() {
        val someWednesday = ZonedDateTime.of(
            2026, 4, 8, 12, 0, 0, 0, kst
        ).toInstant()

        val prev = AnalyticsTime.previousWeekStart(someWednesday)

        assertThat(prev.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(prev).isEqualTo(LocalDate.of(2026, 3, 30))
    }

    @Test
    fun `weekRange 는 월요일 0 시부터 다음 월요일 0 시 직전까지 반개구간`() {
        val weekStart = LocalDate.of(2026, 4, 6)

        val (startInstant, endInstant) = AnalyticsTime.weekRange(weekStart)

        val expectedStart = ZonedDateTime.of(2026, 4, 6, 0, 0, 0, 0, kst).toInstant()
        val expectedEnd = ZonedDateTime.of(2026, 4, 13, 0, 0, 0, 0, kst).toInstant()
        assertThat(startInstant).isEqualTo(expectedStart)
        assertThat(endInstant).isEqualTo(expectedEnd)
    }

    @Test
    fun `UTC 월요일 0 시는 KST 월요일 9 시이므로 같은 주에 속함`() {
        // UTC 2026-04-06 00:00 == KST 2026-04-06 09:00 → 그 주의 월요일
        val mondayMidnightUtc = ZonedDateTime.of(
            2026, 4, 6, 0, 0, 0, 0, ZoneId.of("UTC")
        ).toInstant()

        assertThat(AnalyticsTime.weekStartOf(mondayMidnightUtc))
            .isEqualTo(LocalDate.of(2026, 4, 6))
    }
}
