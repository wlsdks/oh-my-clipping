package com.ohmyclipping.service.analytics.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 페르소나 분석 배치 / 집계가 사용하는 KST 기준 주 경계 유틸.
 *
 * 모든 주 경계는 월요일 00:00 KST 로 고정한다.
 * delivery_log.delivery_date(LocalDate) 와 user_events.created_at(Instant) 처럼
 * 서로 다른 시간 표현을 한 곳에서 일관되게 변환하기 위해 전용 유틸을 둔다.
 *
 * 호출 예:
 *   val weekStart = AnalyticsTime.previousWeekStart()
 *   val (from, to) = AnalyticsTime.weekRange(weekStart)
 */
object AnalyticsTime {

    /** 페르소나 분석에서 사용하는 단일 정답 timezone. */
    val KST: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * 주어진 Instant 가 속한 KST 주의 월요일 LocalDate 를 반환한다.
     *
     * 일요일 23:59 KST → 그 주의 월요일,
     * 월요일 00:00 KST → 새 주의 월요일.
     */
    fun weekStartOf(instant: Instant): LocalDate =
        instant.atZone(KST).toLocalDate().with(DayOfWeek.MONDAY)

    /**
     * 현재 시점 기준 직전 완료 주의 월요일 LocalDate 를 반환한다.
     * 주간 배치가 호출하는 표준 진입점이다.
     */
    fun previousWeekStart(now: Instant = Instant.now()): LocalDate =
        weekStartOf(now).minusWeeks(1)

    /**
     * 주 시작일을 받아 [월요일 00:00 KST, 다음 월요일 00:00 KST) 반개구간을 반환한다.
     * delivery_log / user_events 같은 Instant 컬럼을 기간 필터링할 때 사용한다.
     */
    fun weekRange(weekStart: LocalDate): Pair<Instant, Instant> = Pair(
        weekStart.atStartOfDay(KST).toInstant(),
        weekStart.plusDays(7).atStartOfDay(KST).toInstant()
    )
}
