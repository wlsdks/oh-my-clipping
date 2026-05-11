package com.clipping.mcpserver.service.analytics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "발송 기회 기반 N=2" 활성 판정 규칙의 7 가지 핵심 시나리오를 잠그는 테스트.
 *
 * 이 컴포넌트가 잘못 동작하면 weekly_persona_snapshot 의 active_subs 가
 * 전체적으로 부정확해지므로 모든 시나리오를 명시적으로 검증한다.
 */
class PersonaSubscriptionActivityJudgeTest {

    private val judge = PersonaSubscriptionActivityJudge()
    private val kst = ZoneId.of("Asia/Seoul")
    private val asOf = LocalDate.of(2026, 4, 9)  // 목요일

    private fun createdAt(daysAgo: Long): Instant =
        asOf.minusDays(daysAgo).atStartOfDay(kst).toInstant()

    @Nested
    inner class `judge — 7 핵심 케이스` {

        @Test
        fun `매일 발송 + 최근 2 일 연속 SENT 면 ACTIVE`() {
            val result = judge.judge(
                subscriptionCreatedAt = createdAt(30),
                deliveryDays = DayOfWeek.values().toSet(),
                deliveryHour = 9,
                asOf = asOf,
                isSentOnDate = { _, _ -> true }
            )
            assertThat(result).isEqualTo(ActivityJudgment.ACTIVE)
        }

        @Test
        fun `매일 발송 + 최근 2 일 연속 FAILED 면 ZOMBIE`() {
            val result = judge.judge(
                subscriptionCreatedAt = createdAt(30),
                deliveryDays = DayOfWeek.values().toSet(),
                deliveryHour = 9,
                asOf = asOf,
                isSentOnDate = { _, _ -> false }
            )
            assertThat(result).isEqualTo(ActivityJudgment.ZOMBIE)
        }

        @Test
        fun `주 1 회 월요일 발송 + 지난 2 주 중 1 주만 SENT 면 ACTIVE`() {
            // asOf = 2026-04-09 (목요일) → 직전 2번의 월요일 = 4/6, 3/30
            val result = judge.judge(
                subscriptionCreatedAt = createdAt(60),
                deliveryDays = setOf(DayOfWeek.MONDAY),
                deliveryHour = 9,
                asOf = asOf,
                isSentOnDate = { date, _ ->
                    // 가장 최근 월요일만 SENT
                    date == LocalDate.of(2026, 4, 6)
                }
            )
            assertThat(result).isEqualTo(ActivityJudgment.ACTIVE)
        }

        @Test
        fun `주 1 회 월요일 발송 + 지난 2 주 연속 FAILED 면 ZOMBIE`() {
            val result = judge.judge(
                subscriptionCreatedAt = createdAt(60),
                deliveryDays = setOf(DayOfWeek.MONDAY),
                deliveryHour = 9,
                asOf = asOf,
                isSentOnDate = { _, _ -> false }
            )
            assertThat(result).isEqualTo(ActivityJudgment.ZOMBIE)
        }

        @Test
        fun `구독 생성 3 일 전 + 발송 기회 0 회면 ACTIVE_NEW_SUBSCRIBER`() {
            val result = judge.judge(
                subscriptionCreatedAt = createdAt(3),
                deliveryDays = setOf(DayOfWeek.MONDAY),
                deliveryHour = 9,
                asOf = asOf,
                isSentOnDate = { _, _ -> false }
            )
            assertThat(result).isEqualTo(ActivityJudgment.ACTIVE_NEW_SUBSCRIBER)
        }

        @Test
        fun `구독 60 일 전 + deliveryDays 빈 세트면 INACTIVE_NO_SCHEDULE`() {
            val result = judge.judge(
                subscriptionCreatedAt = createdAt(60),
                deliveryDays = emptySet(),
                deliveryHour = null,
                asOf = asOf,
                isSentOnDate = { _, _ -> false }
            )
            assertThat(result).isEqualTo(ActivityJudgment.INACTIVE_NO_SCHEDULE)
        }

        @Test
        fun `정확히 14 일 전 가입은 신규 처리되지 않아 ZOMBIE`() {
            // 14 일 "미만" 만 신규 처리하므로 14 일 정확히는 일반 판정 대상
            val result = judge.judge(
                subscriptionCreatedAt = createdAt(14),
                deliveryDays = DayOfWeek.values().toSet(),
                deliveryHour = 9,
                asOf = asOf,
                isSentOnDate = { _, _ -> false }
            )
            assertThat(result).isEqualTo(ActivityJudgment.ZOMBIE)
        }
    }

    @Nested
    inner class `computePreviousDeliveryOpportunities` {

        @Test
        fun `매일 발송이면 최근 2 일을 시간 정보와 함께 반환`() {
            val opps = judge.computePreviousDeliveryOpportunities(
                deliveryDays = DayOfWeek.values().toSet(),
                deliveryHour = 9,
                asOf = LocalDate.of(2026, 4, 9),
                count = 2
            )

            assertThat(opps).hasSize(2)
            assertThat(opps[0].date).isEqualTo(LocalDate.of(2026, 4, 8))  // 가장 최근 (수요일)
            assertThat(opps[1].date).isEqualTo(LocalDate.of(2026, 4, 7))  // 그 전 (화요일)
            assertThat(opps).allMatch { it.hour == 9 }
        }

        @Test
        fun `주 1 회 월요일 발송이면 직전 2 번의 월요일을 반환`() {
            val opps = judge.computePreviousDeliveryOpportunities(
                deliveryDays = setOf(DayOfWeek.MONDAY),
                deliveryHour = 9,
                asOf = LocalDate.of(2026, 4, 9),  // 목요일
                count = 2
            )

            assertThat(opps).hasSize(2)
            assertThat(opps[0].date).isEqualTo(LocalDate.of(2026, 4, 6))  // 이번 주 월요일
            assertThat(opps[1].date).isEqualTo(LocalDate.of(2026, 3, 30)) // 지난 주 월요일
        }
    }
}
