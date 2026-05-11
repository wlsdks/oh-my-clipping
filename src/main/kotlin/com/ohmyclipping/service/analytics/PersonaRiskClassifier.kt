package com.ohmyclipping.service.analytics

import com.ohmyclipping.model.Persona
import com.ohmyclipping.service.analytics.dto.ChurnExcessDetails
import com.ohmyclipping.service.analytics.dto.EngagementDropDetails
import com.ohmyclipping.service.analytics.dto.EngagementRiseDetails
import com.ohmyclipping.service.analytics.dto.ExcludedPersonaItem
import com.ohmyclipping.service.analytics.dto.ExcludedReason
import com.ohmyclipping.service.analytics.dto.FirstSubscriptionDetails
import com.ohmyclipping.service.analytics.dto.GrowthSignalItem
import com.ohmyclipping.service.analytics.dto.GrowthSignalType
import com.ohmyclipping.service.analytics.dto.IdleDetails
import com.ohmyclipping.service.analytics.dto.RiskSignalItem
import com.ohmyclipping.service.analytics.dto.RiskSignalType
import com.ohmyclipping.service.analytics.dto.SubsSurgeDetails
import com.ohmyclipping.store.analytics.dto.WeeklyPersonaSnapshot
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 위험 / 성장 판정기.
 *
 * 주간 스냅샷 집합을 받아 스펙 §2, §2.5 규칙으로 위험·성장 신호를 계산한다.
 * 판정은 read-time 이며 캐시(5분)를 상위 서비스가 관리한다.
 *
 * `PersonaSubscriptionActivityJudge` 와 역할이 다르다 — 후자는 개별 구독의
 * zombie 여부 판정(grain 이 다름). 여기서는 페르소나 단위 집계를 본다.
 */
@Component
class PersonaRiskClassifier(
    private val riskProps: AnalyticsRiskProperties,
    private val growthProps: AnalyticsGrowthProperties
) {

    /**
     * 단일 주차 기준으로 모든 페르소나를 분류한다.
     *
     * @param weekStart 판정 기준 주 (월요일). 이 주의 스냅샷이 각 페르소나에
     *                  반드시 있어야 한다 — 없으면 해당 페르소나는 건너뛴다.
     * @param personas  생성일/프리셋 여부를 참조하기 위한 페르소나 메타.
     * @param snapshotsByPersona 페르소나별 12주치 스냅샷. week_start ASC 로 정렬된
     *                  리스트가 전달된다고 가정한다.
     */
    fun classify(
        weekStart: LocalDate,
        personas: List<Persona>,
        snapshotsByPersona: Map<String, List<WeeklyPersonaSnapshot>>
    ): ClassificationResult {
        val risks = mutableListOf<RiskSignalItem>()
        val growth = mutableListOf<GrowthSignalItem>()
        val excluded = mutableListOf<ExcludedPersonaItem>()

        for (persona in personas) {
            val series = snapshotsByPersona[persona.id] ?: continue
            val currentIdx = series.indexOfLast { it.weekStart == weekStart }
            if (currentIdx < 0) continue
            val current = series[currentIdx]
            val prev = series.getOrNull(currentIdx - 1)

            // --- 위험 판정 ---
            judgeChurnExcess(persona, current, prev, series, currentIdx)?.let(risks::add)
            judgeIdle(persona, series, currentIdx)?.let(risks::add)
            judgeEngagementDrop(persona, current, prev, series, currentIdx)?.let(risks::add)

            // --- 성장 판정 — 동일 시리즈에서 별개 판정 ---
            judgeFirstSubscription(persona, current, series, currentIdx)?.let(growth::add)
            // FIRST_SUBSCRIPTION 이 있으면 SUBS_SURGE 생략 (스펙: 0→N 은 deltaPct 무의미).
            val first = growth.lastOrNull()?.takeIf {
                it.personaId == persona.id && it.signalType == GrowthSignalType.FIRST_SUBSCRIPTION
            }
            if (first == null) {
                judgeSubsSurge(persona, current, prev, series, currentIdx)?.let(growth::add)
            }
            judgeEngagementRise(persona, current, prev, series, currentIdx)?.let(growth::add)

            // --- 제외 사유 기록 (분석 대상 외) ---
            if (prev != null && prev.activeSubs > 0 && prev.activeSubs < riskProps.churnBaselineMin &&
                current.churnedSubs >= riskProps.churnMinCount
            ) {
                excluded += ExcludedPersonaItem(persona.id, persona.name, ExcludedReason.CHURN_BASELINE_BELOW_MIN)
            } else if (prev != null &&
                (prev.deliveredCount < riskProps.engagementMinDeliveries ||
                    current.deliveredCount < riskProps.engagementMinDeliveries) &&
                abs(current.engagementRate - prev.engagementRate) * 100 >= riskProps.engagementDropPp
            ) {
                excluded += ExcludedPersonaItem(persona.id, persona.name, ExcludedReason.ENGAGEMENT_DELIVERIES_BELOW_MIN)
            } else if (!persona.isPreset && isIdlePattern(series, currentIdx)) {
                excluded += ExcludedPersonaItem(persona.id, persona.name, ExcludedReason.IDLE_NOT_PRESET)
            }
        }

        // 2차 정렬: 지속 주차 ASC → 변화폭 DESC (스펙 §2).
        risks.sortWith(compareBy<RiskSignalItem> { it.persistentWeeks }.thenByDescending { riskDeltaMagnitude(it) })
        growth.sortWith(compareBy<GrowthSignalItem> { it.persistentWeeks }.thenByDescending { growthDeltaMagnitude(it) })

        return ClassificationResult(risks = risks, growth = growth, excluded = excluded)
    }

    // ── 위험 판정 ────────────────────────────────────────────────────────────

    private fun judgeChurnExcess(
        persona: Persona,
        current: WeeklyPersonaSnapshot,
        prev: WeeklyPersonaSnapshot?,
        series: List<WeeklyPersonaSnapshot>,
        currentIdx: Int
    ): RiskSignalItem? {
        if (prev == null) return null
        if (prev.activeSubs < riskProps.churnBaselineMin) return null
        if (current.churnedSubs < riskProps.churnMinCount) return null
        if (current.churnedSubs <= current.newSubs) return null
        return RiskSignalItem(
            personaId = persona.id,
            personaName = persona.name,
            isPreset = persona.isPreset,
            riskType = RiskSignalType.CHURN_EXCESS,
            persistentWeeks = countPersistentWeeks(series, currentIdx) { s, p ->
                val b = p ?: return@countPersistentWeeks false
                b.activeSubs >= riskProps.churnBaselineMin &&
                    s.churnedSubs >= riskProps.churnMinCount &&
                    s.churnedSubs > s.newSubs
            },
            details = ChurnExcessDetails(
                churnedSubs = current.churnedSubs,
                newSubs = current.newSubs,
                activeSubs = current.activeSubs
            )
        )
    }

    private fun judgeIdle(
        persona: Persona,
        series: List<WeeklyPersonaSnapshot>,
        currentIdx: Int
    ): RiskSignalItem? {
        // 유휴는 프리셋 전용 (스펙 §2).
        if (!persona.isPreset) return null
        if (!isIdlePattern(series, currentIdx)) return null
        val current = series[currentIdx]
        val consecutive = countIdleWeeks(series, currentIdx)
        return RiskSignalItem(
            personaId = persona.id,
            personaName = persona.name,
            isPreset = true,
            riskType = RiskSignalType.IDLE,
            // 유휴는 지속 주차 = 연속 유휴 주 수 자체.
            persistentWeeks = consecutive,
            details = IdleDetails(consecutiveWeeks = consecutive, activeSubs = current.activeSubs)
        )
    }

    private fun judgeEngagementDrop(
        persona: Persona,
        current: WeeklyPersonaSnapshot,
        prev: WeeklyPersonaSnapshot?,
        series: List<WeeklyPersonaSnapshot>,
        currentIdx: Int
    ): RiskSignalItem? {
        if (prev == null) return null
        if (prev.deliveredCount < riskProps.engagementMinDeliveries) return null
        if (current.deliveredCount < riskProps.engagementMinDeliveries) return null
        val deltaPp = ((current.engagementRate - prev.engagementRate) * 100).roundToInt()
        if (deltaPp > -riskProps.engagementDropPp) return null
        return RiskSignalItem(
            personaId = persona.id,
            personaName = persona.name,
            isPreset = persona.isPreset,
            riskType = RiskSignalType.ENGAGEMENT_DROP,
            persistentWeeks = countPersistentWeeks(series, currentIdx) { s, p ->
                if (p == null) false
                else p.deliveredCount >= riskProps.engagementMinDeliveries &&
                    s.deliveredCount >= riskProps.engagementMinDeliveries &&
                    ((s.engagementRate - p.engagementRate) * 100).roundToInt() <= -riskProps.engagementDropPp
            },
            details = EngagementDropDetails(
                engagementRate = current.engagementRate,
                prevEngagementRate = prev.engagementRate,
                deltaPp = deltaPp,
                deliveredCount = current.deliveredCount,
                totalClicks = current.totalClicks
            )
        )
    }

    // ── 성장 판정 ────────────────────────────────────────────────────────────

    private fun judgeSubsSurge(
        persona: Persona,
        current: WeeklyPersonaSnapshot,
        prev: WeeklyPersonaSnapshot?,
        series: List<WeeklyPersonaSnapshot>,
        currentIdx: Int
    ): GrowthSignalItem? {
        if (prev == null || prev.activeSubs <= 0) return null
        val deltaAbs = current.activeSubs - prev.activeSubs
        if (deltaAbs < growthProps.subsSurgeMin) return null
        val deltaPct = ((deltaAbs.toDouble() / prev.activeSubs) * 100).roundToInt()
        if (deltaPct < growthProps.subsSurgePct) return null
        return GrowthSignalItem(
            personaId = persona.id,
            personaName = persona.name,
            isPreset = persona.isPreset,
            signalType = GrowthSignalType.SUBS_SURGE,
            persistentWeeks = countPersistentWeeks(series, currentIdx) { s, p ->
                if (p == null || p.activeSubs <= 0) false
                else {
                    val abs = s.activeSubs - p.activeSubs
                    val pct = ((abs.toDouble() / p.activeSubs) * 100).roundToInt()
                    abs >= growthProps.subsSurgeMin && pct >= growthProps.subsSurgePct
                }
            },
            details = SubsSurgeDetails(
                activeSubs = current.activeSubs,
                prevActiveSubs = prev.activeSubs,
                deltaAbs = deltaAbs,
                deltaPct = deltaPct
            )
        )
    }

    private fun judgeEngagementRise(
        persona: Persona,
        current: WeeklyPersonaSnapshot,
        prev: WeeklyPersonaSnapshot?,
        series: List<WeeklyPersonaSnapshot>,
        currentIdx: Int
    ): GrowthSignalItem? {
        if (prev == null) return null
        if (prev.deliveredCount < riskProps.engagementMinDeliveries) return null
        if (current.deliveredCount < riskProps.engagementMinDeliveries) return null
        val deltaPp = ((current.engagementRate - prev.engagementRate) * 100).roundToInt()
        if (deltaPp < growthProps.engagementRisePp) return null
        return GrowthSignalItem(
            personaId = persona.id,
            personaName = persona.name,
            isPreset = persona.isPreset,
            signalType = GrowthSignalType.ENGAGEMENT_RISE,
            persistentWeeks = countPersistentWeeks(series, currentIdx) { s, p ->
                if (p == null) false
                else p.deliveredCount >= riskProps.engagementMinDeliveries &&
                    s.deliveredCount >= riskProps.engagementMinDeliveries &&
                    ((s.engagementRate - p.engagementRate) * 100).roundToInt() >= growthProps.engagementRisePp
            },
            details = EngagementRiseDetails(
                engagementRate = current.engagementRate,
                prevEngagementRate = prev.engagementRate,
                deltaPp = deltaPp,
                deliveredCount = current.deliveredCount,
                totalClicks = current.totalClicks
            )
        )
    }

    private fun judgeFirstSubscription(
        persona: Persona,
        current: WeeklyPersonaSnapshot,
        series: List<WeeklyPersonaSnapshot>,
        currentIdx: Int
    ): GrowthSignalItem? {
        if (current.activeSubs < 1) return null
        // 이번 주 이전의 모든 주에서 active_subs == 0 이어야 "처음".
        val beforeEmpty = series.subList(0, currentIdx).all { it.activeSubs == 0 }
        if (!beforeEmpty) return null
        // 생성 4주 이내 (noise floor).
        val daysSinceCreation = ChronoUnit.DAYS.between(
            persona.createdAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
            current.weekStart
        ).toInt()
        if (daysSinceCreation > 28) return null
        return GrowthSignalItem(
            personaId = persona.id,
            personaName = persona.name,
            isPreset = persona.isPreset,
            signalType = GrowthSignalType.FIRST_SUBSCRIPTION,
            persistentWeeks = 1,
            details = FirstSubscriptionDetails(
                activeSubs = current.activeSubs,
                daysSinceCreation = maxOf(0, daysSinceCreation)
            )
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** 유휴 패턴: 이번 주 포함 최근 `idleWeeks` 주 각각 delivered=0 AND 이번 주 active_subs=0. */
    private fun isIdlePattern(series: List<WeeklyPersonaSnapshot>, currentIdx: Int): Boolean {
        val need = riskProps.idleWeeks
        val fromIdx = currentIdx - need + 1
        if (fromIdx < 0) return false
        val current = series[currentIdx]
        if (current.activeSubs != 0) return false
        return (fromIdx..currentIdx).all { series[it].deliveredCount == 0 }
    }

    /** 연속 유휴 주 수 (이번 주 포함, 최대 series 길이). */
    private fun countIdleWeeks(series: List<WeeklyPersonaSnapshot>, currentIdx: Int): Int {
        var count = 0
        for (i in currentIdx downTo 0) {
            if (series[i].deliveredCount == 0) count += 1 else break
        }
        return count
    }

    /**
     * 지속 주차 카운트.
     * 현재 주에서 신호 ON 이 확정된 뒤, 직전 주부터 거꾸로 훑으며 신호가 끊어질 때까지 센다.
     * 스펙 §2 "리셋 규칙" — 중간 OFF 1주 이상이면 카운트 리셋.
     */
    private fun countPersistentWeeks(
        series: List<WeeklyPersonaSnapshot>,
        currentIdx: Int,
        isOn: (current: WeeklyPersonaSnapshot, prev: WeeklyPersonaSnapshot?) -> Boolean
    ): Int {
        var count = 1 // 현재 주는 ON 으로 진입 조건이 이미 충족.
        var i = currentIdx - 1
        while (i >= 1) {
            val c = series[i]
            val p = series[i - 1]
            if (isOn(c, p)) count += 1 else break
            i -= 1
        }
        return count
    }

    private fun riskDeltaMagnitude(item: RiskSignalItem): Int = when (val d = item.details) {
        is ChurnExcessDetails -> d.churnedSubs - d.newSubs
        is IdleDetails -> d.consecutiveWeeks
        is EngagementDropDetails -> abs(d.deltaPp)
        else -> 0
    }

    private fun growthDeltaMagnitude(item: GrowthSignalItem): Int = when (val d = item.details) {
        is SubsSurgeDetails -> abs(d.deltaPct)
        is EngagementRiseDetails -> abs(d.deltaPp)
        is FirstSubscriptionDetails -> d.activeSubs
        else -> 0
    }
}

/** 판정 결과 묶음. */
data class ClassificationResult(
    val risks: List<RiskSignalItem>,
    val growth: List<GrowthSignalItem>,
    val excluded: List<ExcludedPersonaItem>
)
