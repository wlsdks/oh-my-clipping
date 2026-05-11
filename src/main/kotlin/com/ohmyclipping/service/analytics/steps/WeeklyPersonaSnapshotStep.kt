package com.ohmyclipping.service.analytics.steps

import com.ohmyclipping.model.Category
import com.ohmyclipping.service.analytics.ActivityJudgment
import com.ohmyclipping.service.analytics.PersonaSubscriptionActivityJudge
import com.ohmyclipping.service.analytics.dto.GlobalEngagement
import com.ohmyclipping.store.analytics.dto.WeeklyPersonaSnapshot
import com.ohmyclipping.store.analytics.dto.WeeklySubscriptionState
import com.ohmyclipping.service.analytics.time.AnalyticsTime
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.DeliveryLogStore
import com.ohmyclipping.store.PersonaAnalyticsStore
import com.ohmyclipping.store.PersonaStore
import com.ohmyclipping.store.UserEventStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 주간 페르소나 스냅샷 배치 스텝.
 *
 * 모든 활성 페르소나를 순회하며 구독별 활성 판정, 발송 실적, 참여 지표를 집계해
 * weekly_persona_snapshot 과 weekly_persona_subscription_state 에 upsert 한다.
 *
 * MondayBatch 오케스트레이터가 호출하는 단일 책임 컴포넌트이다.
 * 멱등 설계이므로 같은 weekStart 로 여러 번 실행해도 결과가 동일하다.
 */
@Component
class WeeklyPersonaSnapshotStep(
    private val personaStore: PersonaStore,
    private val categoryStore: CategoryStore,
    private val categoryRuleStore: CategoryRuleStore,
    private val deliveryLogStore: DeliveryLogStore,
    private val userEventStore: UserEventStore,
    private val analyticsStore: PersonaAnalyticsStore,
    private val activityJudge: PersonaSubscriptionActivityJudge
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 주간 스냅샷 배치를 실행한다.
     *
     * @param weekStart 집계 대상 주의 월요일 (예: 2026-03-31)
     * @param runId     이 배치 실행의 고유 ID (카운터 증분용)
     * @return 처리된 페르소나 수
     */
    fun execute(weekStart: LocalDate, runId: String): Int {
        val personas = personaStore.listActive()
        log.info("WeeklyPersonaSnapshotStep 시작: weekStart={}, personas={}", weekStart, personas.size)

        val prevWeekStart = weekStart.minusWeeks(1)
        // 주간 범위(Instant)를 산출한다.
        val (weekFrom, weekTo) = AnalyticsTime.weekRange(weekStart)
        // 주 전체 참여 지표를 미리 조회한다.
        val globalEngagement = loadGlobalEngagement(weekFrom, weekTo)

        for (persona in personas) {
            processPersona(persona.id, persona.name, persona.isPreset, weekStart, prevWeekStart, globalEngagement)
        }

        // 배치 카운터를 증분한다.
        analyticsStore.updateRunCounter(runId, "personas_scanned", personas.size)

        log.info("WeeklyPersonaSnapshotStep 완료: {}개 페르소나 처리", personas.size)
        return personas.size
    }

    // ── 내부 구현 ────────────────────────────────────────────────────────────

    /**
     * 단일 페르소나에 대한 스냅샷 및 구독 상태를 계산·저장한다.
     */
    private fun processPersona(
        personaId: String,
        personaName: String,
        isPreset: Boolean,
        weekStart: LocalDate,
        prevWeekStart: LocalDate,
        globalEngagement: GlobalEngagement
    ) {
        // 해당 페르소나에 연결된 활성 카테고리(=구독)를 조회한다.
        val categories = categoryStore.findActiveByPersonaId(personaId)
        val weekEnd = weekStart.plusDays(6)

        // 카테고리별 활성 판정 + 구독 상태 수집.
        val subscriptionStates = categories.map { cat ->
            judgeCategory(cat, weekStart, weekEnd)
        }

        // 이번 주의 구독 상태를 저장한다.
        analyticsStore.upsertWeeklySubscriptionStates(subscriptionStates)

        // 집계 지표를 산출한다.
        val snapshot = buildSnapshot(
            personaId, personaName, isPreset, weekStart, prevWeekStart,
            categories, subscriptionStates, globalEngagement
        )
        analyticsStore.upsertWeeklySnapshot(snapshot)
    }

    /**
     * 단일 카테고리(=구독)의 활성 판정 및 주간 발송 실적을 산출한다.
     */
    private fun judgeCategory(
        category: Category,
        weekStart: LocalDate,
        weekEnd: LocalDate
    ): WeeklySubscriptionState {
        val rule = categoryRuleStore.findByCategoryId(category.id)

        // 발송 요일을 DayOfWeek 집합으로 변환한다.
        val deliveryDays = rule?.deliveryDays
            ?.mapNotNull { parseDayOfWeek(it) }
            ?.toSet()
            ?: emptySet()
        val deliveryHour = rule?.deliveryHour

        // 활성 판정을 수행한다.
        val judgment = activityJudge.judge(
            subscriptionCreatedAt = category.createdAt,
            deliveryDays = deliveryDays,
            deliveryHour = deliveryHour,
            asOf = weekEnd,
            isSentOnDate = { date, hour -> deliveryLogStore.existsSent(category.id, date, hour) }
        )

        // 해당 주간의 SENT 발송 건수를 집계한다.
        val deliveredInWeek = deliveryLogStore.countAll(
            categoryId = category.id,
            status = "SENT",
            from = weekStart,
            to = weekStart.plusDays(6)
        )

        // 활성 판정 결과를 구독 상태 문자열로 매핑한다.
        val state = mapJudgmentToState(judgment)

        return WeeklySubscriptionState(
            weekStart = weekStart,
            personaId = category.personaId ?: "",
            categoryId = category.id,
            state = state,
            deliveryOpportunities = countDeliveryOpportunities(deliveryDays, weekStart),
            deliveredCount = deliveredInWeek,
            clicksInWeek = 0,   // Slice 2 MVP: 카테고리별 클릭은 미집계 (이벤트와 카테고리 매핑 부재)
            bookmarksInWeek = 0 // Slice 2 MVP: 카테고리별 북마크는 미집계
        )
    }

    /**
     * 집계된 구독 상태로부터 스냅샷 DTO 를 조립한다.
     */
    private fun buildSnapshot(
        personaId: String,
        personaName: String,
        isPreset: Boolean,
        weekStart: LocalDate,
        prevWeekStart: LocalDate,
        categories: List<Category>,
        subscriptionStates: List<WeeklySubscriptionState>,
        globalEngagement: GlobalEngagement
    ): WeeklyPersonaSnapshot {
        // 활성/신규 구독 수를 산출한다.
        val activeSubs = subscriptionStates.count { it.state == "ACTIVE" || it.state == "NEW" }
        val newSubs = subscriptionStates.count { it.state == "NEW" }

        // 이탈 구독 수를 전주 대비로 계산한다.
        val churnedSubs = analyticsStore.countChurnedSubscriptions(prevWeekStart, weekStart, personaId)

        // 발송 실적을 합산한다.
        val deliveredCount = subscriptionStates.sumOf { it.deliveredCount }

        // 발송 항목 수는 발송 건수와 동일하게 취급한다 (상세 항목 집계는 Slice 3 에서 보강).
        val deliveredItems = deliveredCount

        // 참여 지표는 글로벌 집계를 사용한다 (카테고리별 분리는 Slice 3 에서 보강).
        val totalClicks = if (categories.isNotEmpty()) {
            (globalEngagement.totalClicks * categories.size / maxOf(globalEngagement.totalCategories, 1)).toInt()
        } else 0

        val totalBookmarks = if (categories.isNotEmpty()) {
            (globalEngagement.totalBookmarks * categories.size / maxOf(globalEngagement.totalCategories, 1)).toInt()
        } else 0

        // 참여 유저 수 = 클릭이 1회 이상인 구독 수 (Slice 2 근사치: 전체 비례 배분).
        val engagedUsers = if (activeSubs > 0 && globalEngagement.engagedUserCount > 0) {
            maxOf(1, globalEngagement.engagedUserCount * categories.size / maxOf(globalEngagement.totalCategories, 1))
        } else 0

        // 참여율 = 참여유저 / 활성구독 (활성구독이 0이면 0.0).
        val engagementRate = if (activeSubs > 0) {
            minOf(1.0, engagedUsers.toDouble() / activeSubs.toDouble())
        } else 0.0

        // 클릭/발송 비율 = 총클릭 / 발송건수 (발송이 0이면 0.0).
        val clickPerDelivery = if (deliveredCount > 0) {
            totalClicks.toDouble() / deliveredCount.toDouble()
        } else 0.0

        return WeeklyPersonaSnapshot(
            id = UUID.randomUUID().toString(),
            weekStart = weekStart,
            personaId = personaId,
            personaName = personaName,
            isPreset = isPreset,
            activeSubs = activeSubs,
            newSubs = newSubs,
            churnedSubs = churnedSubs,
            deliveredCount = deliveredCount,
            deliveredItems = deliveredItems,
            engagedUsers = engagedUsers,
            totalClicks = totalClicks,
            totalBookmarks = totalBookmarks,
            engagementRate = engagementRate,
            clickPerDelivery = clickPerDelivery,
            createdAt = Instant.now()
        )
    }

    /**
     * 글로벌 참여 지표를 한 번에 조회한다 (주간 범위).
     */
    private fun loadGlobalEngagement(weekFrom: Instant, weekTo: Instant): GlobalEngagement {
        val totalClicks = userEventStore.countByEventType("ARTICLE_CLICK", weekFrom, weekTo)
        val totalBookmarks = userEventStore.countByEventType("BOOKMARK", weekFrom, weekTo)
        val engagedUserCount = userEventStore.countDistinctUsers(weekFrom, weekTo).toInt()
        val totalCategories = categoryStore.countOperational().toInt()

        return GlobalEngagement(
            totalClicks = totalClicks,
            totalBookmarks = totalBookmarks,
            engagedUserCount = engagedUserCount,
            totalCategories = totalCategories
        )
    }

    /**
     * 활성 판정 결과를 구독 상태 문자열로 매핑한다.
     */
    private fun mapJudgmentToState(judgment: ActivityJudgment): String = when (judgment) {
        ActivityJudgment.ACTIVE -> "ACTIVE"
        ActivityJudgment.ACTIVE_NEW_SUBSCRIBER -> "NEW"
        ActivityJudgment.ZOMBIE -> "CHURNED"
        ActivityJudgment.INACTIVE_NO_SCHEDULE -> "CHURNED"
    }

    /**
     * 주어진 발송 요일 집합으로부터 해당 주의 발송 기회 수를 계산한다.
     */
    private fun countDeliveryOpportunities(deliveryDays: Set<DayOfWeek>, weekStart: LocalDate): Int {
        return (0L until 7L).count { offset ->
            deliveryDays.contains(weekStart.plusDays(offset).dayOfWeek)
        }
    }

    /**
     * 문자열 요일명을 DayOfWeek 으로 변환한다. 인식 불가 시 null 반환.
     */
    private fun parseDayOfWeek(day: String): DayOfWeek? = try {
        DayOfWeek.valueOf(day.uppercase())
    } catch (_: IllegalArgumentException) {
        null
    }

}
