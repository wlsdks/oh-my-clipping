package com.clipping.mcpserver.service.analytics

import com.clipping.mcpserver.service.analytics.time.AnalyticsTime
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * 페르소나 구독의 "현재 활성" 판정 결과.
 *
 * spec 2026-04-09-persona-usage-analytics-design §5.2 정의:
 *   - ACTIVE                 : 직전 2 회의 예정 발송 기회 중 1 회 이상 SENT
 *   - ACTIVE_NEW_SUBSCRIBER  : 가입 14 일 미만 (보수적 활성 처리)
 *   - ZOMBIE                 : 발송 기회는 있었으나 모두 미발송
 *   - INACTIVE_NO_SCHEDULE   : 스케줄(요일 / 시간) 정보 자체가 없음
 */
enum class ActivityJudgment {
    ACTIVE,
    ACTIVE_NEW_SUBSCRIBER,
    ZOMBIE,
    INACTIVE_NO_SCHEDULE
}

/**
 * "예정 발송 일시 + 시" 단위 발송 기회.
 * (date, hour) 가 일대일로 delivery_log 행 한 개에 대응한다.
 */
data class DeliveryOpportunity(
    val date: LocalDate,
    val hour: Int
)

/**
 * 페르소나 구독이 "현재 활성"인지 판정하는 단일 책임자.
 *
 * 활성 정의는 발송 기회 기반 N=2 규칙을 사용한다 (spec §5.2 7 케이스):
 *   1. 가입 14 일 미만 → ACTIVE_NEW_SUBSCRIBER (오탐 방지)
 *   2. 스케줄 정보 없음 → INACTIVE_NO_SCHEDULE
 *   3. 직전 2 회의 예정 발송 기회 중 1 회 이상 SENT → ACTIVE
 *   4. 그 외 → ZOMBIE
 *
 * 이 컴포넌트가 활성 판정의 유일한 정답이다. 다른 서비스가 직접 SQL 로
 * 활성 구독을 카운트하면 정의가 갈라지므로 절대 우회하지 않는다.
 */
@Component
class PersonaSubscriptionActivityJudge {

    /**
     * 단일 구독에 대한 활성 판정을 수행한다.
     *
     * 의존 주입을 피하기 위해 발송 여부 조회는 람다 [isSentOnDate] 로 받는다.
     * 호출자가 (categoryId 등) 식별자를 캡처해 lambda 에 넘긴다.
     *
     * @param subscriptionCreatedAt 구독 생성 시점 Instant
     * @param deliveryDays 발송 요일 집합 (예: {MONDAY, WEDNESDAY, FRIDAY})
     * @param deliveryHour 발송 시각 (KST 기준 시 단위, null 이면 스케줄 없음)
     * @param asOf 판정 기준 일자 (보통 weekStart + 6)
     * @param isSentOnDate (date, hour) 에 SENT 행이 존재하는지 조회하는 함수
     */
    fun judge(
        subscriptionCreatedAt: Instant,
        deliveryDays: Set<DayOfWeek>,
        deliveryHour: Int?,
        asOf: LocalDate,
        isSentOnDate: (LocalDate, Int) -> Boolean
    ): ActivityJudgment {
        // 가입 14 일 미만은 발송 기회가 충분하지 않으므로 보수적으로 active 처리.
        val createdLocalDate = subscriptionCreatedAt.atZone(AnalyticsTime.KST).toLocalDate()
        if (createdLocalDate.isAfter(asOf.minusDays(14))) {
            return ActivityJudgment.ACTIVE_NEW_SUBSCRIBER
        }

        // 스케줄(요일 / 시간)이 없으면 발송 자체가 불가능 — 판정 대상에서 제외.
        if (deliveryDays.isEmpty() || deliveryHour == null) {
            return ActivityJudgment.INACTIVE_NO_SCHEDULE
        }

        // 직전 2 번의 예정 발송 기회 산출.
        val opportunities = computePreviousDeliveryOpportunities(
            deliveryDays = deliveryDays,
            deliveryHour = deliveryHour,
            asOf = asOf,
            count = 2
        )
        if (opportunities.isEmpty()) {
            return ActivityJudgment.INACTIVE_NO_SCHEDULE
        }

        // 1 회 이상 SENT 면 ACTIVE, 모두 미발송이면 ZOMBIE.
        val sentCount = opportunities.count { opp -> isSentOnDate(opp.date, opp.hour) }
        return if (sentCount >= 1) ActivityJudgment.ACTIVE else ActivityJudgment.ZOMBIE
    }

    /**
     * asOf 이전으로 거슬러 올라가며 deliveryDays 에 해당하는 [count] 개의 예정 일자를 수집한다.
     * 무한 루프 방지를 위해 30 일 lookback 한도를 둔다.
     *
     * 반환 순서는 가장 최근 일자가 인덱스 0 이다 (count = 2 면 [최근, 그 다음 최근]).
     */
    internal fun computePreviousDeliveryOpportunities(
        deliveryDays: Set<DayOfWeek>,
        deliveryHour: Int?,
        asOf: LocalDate,
        count: Int
    ): List<DeliveryOpportunity> {
        if (deliveryDays.isEmpty() || deliveryHour == null) return emptyList()
        val opps = mutableListOf<DeliveryOpportunity>()
        var cursor = asOf.minusDays(1)  // asOf 당일은 "예정"이 아님
        val lookbackLimit = asOf.minusDays(30)
        while (opps.size < count && cursor.isAfter(lookbackLimit)) {
            if (deliveryDays.contains(cursor.dayOfWeek)) {
                opps.add(DeliveryOpportunity(cursor, deliveryHour))
            }
            cursor = cursor.minusDays(1)
        }
        return opps
    }
}
