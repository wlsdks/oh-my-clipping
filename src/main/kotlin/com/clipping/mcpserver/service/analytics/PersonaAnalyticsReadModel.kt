package com.clipping.mcpserver.service.analytics

import com.clipping.mcpserver.service.analytics.dto.CustomSummary
import com.clipping.mcpserver.service.analytics.dto.PersonaTrendSeries
import com.clipping.mcpserver.service.analytics.dto.PortfolioStatus
import com.clipping.mcpserver.service.analytics.dto.PresetPortfolioItem
import com.clipping.mcpserver.service.analytics.dto.RecentCustomPersona
import com.clipping.mcpserver.service.analytics.dto.SignalsResponse
import com.clipping.mcpserver.service.analytics.dto.TotalsCard
import com.clipping.mcpserver.service.analytics.dto.WeeklyTrendsResponse
import com.clipping.mcpserver.service.analytics.time.AnalyticsTime
import com.clipping.mcpserver.store.PersonaAnalyticsStore
import com.clipping.mcpserver.store.PersonaStore
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * 페르소나 분석 조회 전용 모델.
 *
 * 비즈니스 규칙은 [PersonaAnalyticsService] 가 담고, ReadModel 은 store 호출과
 * DTO 매핑만 책임진다. Slice 1 단계에서는 시계열 인프라가 아직 없으므로
 * weekOverWeekDelta / engagementRate / lastDeliveredAt 같은 시계열 의존 필드는
 * null 로 둔다 (Slice 2 weekly snapshot 도입 시 채워진다).
 *
 * 활성 구독 카운트는 일단 기존 PersonaStore 의 `is_active=TRUE` 정의를 사용한다.
 * 발송 기회 기반 N=2 정의로의 전환은 Slice 2 의 WeeklyPersonaSnapshotStep 에서
 * [PersonaSubscriptionActivityJudge] 를 통해 수행된다.
 *
 * Slice 1 의 의의는 다음과 같다:
 *   - 죽은 필드 4 개를 응답에서 제거 (`weeklySubscriptionDelta` 등)
 *   - 오용된 라벨 `customConversionRate` → `customStyleRatio` 개명
 *   - Analytics 페이지로 이관할 깨끗한 DTO/엔드포인트 제공
 *   - Slice 2 가 그대로 확장할 service / store 골격 마련
 */
@Component
class PersonaAnalyticsReadModel(
    private val personaStore: PersonaStore,
    private val analyticsStore: PersonaAnalyticsStore,
    private val riskClassifier: PersonaRiskClassifier
) {

    /**
     * 위험·성장 신호 페이로드를 조립한다.
     *
     * 1) 가장 최근 스냅샷의 `week_start` 를 기준 주로 삼는다.
     *    (오늘이 속한 주가 아직 배치 전이면 마지막 집계 주가 된다.)
     * 2) lookbackWeeks 주치의 스냅샷을 한 번에 로드해 페르소나별로 그룹화 →
     *    classifier 에 in-memory 로 넘긴다 (N+1 방지).
     *
     * @param lookbackWeeks 유휴 판정·지속 주차 카운팅 범위 (1~12).
     */
    fun loadSignals(lookbackWeeks: Int): SignalsResponse {
        // 입력 가드 — 상위 Service 가 정규화하지만 한번 더 막는다.
        val safeLookback = lookbackWeeks.coerceIn(1, 12)

        // 가장 최근 스냅샷 주차를 기준으로 삼는다 (이번 주 배치 전이면 전주).
        val today = LocalDate.now(AnalyticsTime.KST)
        val thisWeekStart = today.with(DayOfWeek.MONDAY)
        val recent = analyticsStore.findSnapshotsByRange(thisWeekStart.minusWeeks(1), thisWeekStart)
        val asOfWeek = (recent.maxOfOrNull { it.weekStart } ?: thisWeekStart.minusWeeks(1))

        // 범위 내 스냅샷 일괄 로드 (페르소나별 그룹핑).
        val fromWeek = asOfWeek.minusWeeks((safeLookback - 1).toLong())
        val snapshots = analyticsStore.findSnapshotsByRange(fromWeek, asOfWeek)
        val byPersona = snapshots
            .groupBy { it.personaId }
            .mapValues { (_, list) -> list.sortedBy { it.weekStart } }

        // 페르소나 메타 로드 — 비활성/삭제된 것도 스냅샷에 잔존할 수 있으므로 전체.
        val personas = personaStore.list()

        val result = riskClassifier.classify(asOfWeek, personas, byPersona)

        // isWeekComplete: 오늘 >= weekStart + 7일.
        val isWeekComplete = !today.isBefore(asOfWeek.plusDays(7))

        return SignalsResponse(
            asOfWeekIso = toIsoWeek(asOfWeek),
            asOfSnapshotDate = asOfWeek,
            isWeekComplete = isWeekComplete,
            risks = result.risks,
            growth = result.growth,
            excluded = result.excluded
        )
    }

    private fun toIsoWeek(week: LocalDate): String {
        val fields = WeekFields.ISO
        val weekNum = week.get(fields.weekOfWeekBasedYear())
        val weekBasedYear = week.get(fields.weekBasedYear())
        return "%d-W%02d".format(weekBasedYear, weekNum)
    }

    /**
     * 4 개 카드용 totals 집계.
     *
     * - totalStyles: preset 개수 + custom 개수
     * - activeSubscriptions: persona 가 연결된 활성 구독 수
     * - presetUsageRate: 활성 구독 중 프리셋 페르소나가 차지하는 비율 (구독 단위)
     * - customStyleRatio: 전체 스타일 중 커스텀 비율
     *
     * 의미는 "활성 구독 중 프리셋 비율"이며 어드민이 보는 화면 라벨도 동일하게 맞춘다.
     * 사용자 수가 아니라 활성 카테고리 구독 수를 기준으로 비율을 계산한다.
     */
    fun computeLiveTotals(): TotalsCard {
        // 프리셋 / 커스텀 개수 산출.
        val presetCount = personaStore.listPresets().size
        val customCount = personaStore.countCustomPersonas().toInt()
        val totalStyles = presetCount + customCount

        // 활성 구독 수 (Slice 1 은 기존 정의 그대로, Slice 2 부터 N=2 룰로 전환).
        val totalActiveSubs = personaStore.countTotalActiveSubscriptions().toInt()

        // 프리셋이 차지하는 활성 구독 수 — findPresetUsage 를 합산해서 구한다.
        val presetActiveSubs = personaStore.findPresetUsage()
            .sumOf { it.activeSubscriptions }
            .toInt()
        val presetUsageRate = if (totalActiveSubs == 0) 0.0
                              else presetActiveSubs.toDouble() / totalActiveSubs.toDouble()

        // 커스텀 비중 — 전체 스타일 중 커스텀이 차지하는 비율.
        val customStyleRatio = if (totalStyles == 0) 0.0
                               else customCount.toDouble() / totalStyles.toDouble()

        return TotalsCard(
            totalStyles = totalStyles,
            presetCount = presetCount,
            customCount = customCount,
            activeSubscriptions = totalActiveSubs,
            presetUsageRate = presetUsageRate,
            customStyleRatio = customStyleRatio,
            weekOverWeekDelta = null
        )
    }

    /**
     * 프리셋별 포트폴리오 행 목록.
     * Slice 1 의 status 분류는 단순 카운트 기반 (5+ HEALTHY, 0 UNUSED, 그 외 WATCHING).
     * 시계열 기반 DECLINING 은 Slice 2 에서 활성화된다.
     */
    fun loadPresetPortfolio(): List<PresetPortfolioItem> {
        return personaStore.findPresetUsage().map { row ->
            val activeSubs = row.activeSubscriptions.toInt()
            PresetPortfolioItem(
                personaId = row.presetId,
                personaName = row.presetName,
                activeSubs = activeSubs,
                weekOverWeekDelta = null,
                engagementRate = null,
                status = computeStatus(activeSubs),
                lastDeliveredAt = null
            )
        }
    }

    /**
     * 커스텀 페르소나 요약 + 최근 20 건.
     * systemPromptPreview 는 PII 노출 방지를 위해 120 자로 절단한다.
     */
    fun loadCustomSummary(): CustomSummary {
        val totalCustomPersonas = personaStore.countCustomPersonas().toInt()
        val activeCustomSubscriptions = personaStore.countActiveCustomSubscriptions().toInt()
        val recent = personaStore.findRecentCustomPersonas(RECENT_CUSTOM_LIMIT).map { row ->
            RecentCustomPersona(
                id = row.id,
                personaName = row.personaName,
                userName = row.userName,
                systemPromptPreview = row.systemPrompt.take(SYSTEM_PROMPT_PREVIEW_LENGTH),
                createdAt = row.createdAt
            )
        }
        return CustomSummary(
            totalCustomPersonas = totalCustomPersonas,
            activeCustomSubscriptions = activeCustomSubscriptions,
            newThisWeek = 0,
            recentPersonas = recent
        )
    }

    /**
     * N주 기간의 페르소나별 주간 트렌드 시리즈를 빌드한다.
     *
     * - weeks 범위의 월요일 목록을 먼저 생성하고, 스냅샷이 없는 주차는 0으로 채운다.
     * - groupBy 키는 (personaId, personaName, isPreset) 3중 조합이다.
     *
     * @param weeks 조회할 주 수 (1~52). 유효성 검사는 호출 측(Service)이 담당한다.
     */
    fun buildWeeklyTrends(weeks: Int): WeeklyTrendsResponse {
        // 기준 날짜 및 시작 주차 계산 (KST 기준 월요일).
        val now = LocalDate.now(AnalyticsTime.KST)
        val fromWeek = now.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())

        // 범위 내 스냅샷 조회.
        val snapshots = analyticsStore.findSnapshotsByRange(fromWeek, now)

        // 주 목록 생성 (ISO 날짜 문자열, 인덱스 기준).
        val weekList = (0 until weeks).map { i ->
            fromWeek.plusWeeks(i.toLong()).toString()
        }

        // 페르소나별로 그룹화하여 시리즈 구성, 스냅샷 없는 주차는 0으로 채운다.
        val byPersona = snapshots.groupBy { Triple(it.personaId, it.personaName, it.isPreset) }
        val series = byPersona.map { (key, personaSnapshots) ->
            val (personaId, personaName, isPreset) = key
            val byWeek = personaSnapshots.associateBy { it.weekStart.toString() }
            PersonaTrendSeries(
                personaId = personaId,
                personaName = personaName,
                isPreset = isPreset,
                activeSubs = weekList.map { w -> byWeek[w]?.activeSubs ?: 0 },
                engagedUsers = weekList.map { w -> byWeek[w]?.engagedUsers ?: 0 },
                deliveredCount = weekList.map { w -> byWeek[w]?.deliveredCount ?: 0 }
            )
        }

        return WeeklyTrendsResponse(weeks = weekList, series = series)
    }

    private fun computeStatus(activeSubs: Int): PortfolioStatus = when {
        activeSubs >= HEALTHY_THRESHOLD -> PortfolioStatus.HEALTHY
        activeSubs == 0 -> PortfolioStatus.UNUSED
        else -> PortfolioStatus.WATCHING
    }

    companion object {
        private const val RECENT_CUSTOM_LIMIT = 20
        private const val SYSTEM_PROMPT_PREVIEW_LENGTH = 120
        private const val HEALTHY_THRESHOLD = 5
    }
}
