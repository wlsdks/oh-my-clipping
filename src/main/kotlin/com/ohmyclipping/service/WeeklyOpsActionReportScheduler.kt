package com.ohmyclipping.service

import com.ohmyclipping.service.port.RiskSourceSummary
import com.ohmyclipping.service.port.WeeklyActionReport
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.PipelineRunStore
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 주간 액션 리포트(M13) 스케줄러.
 *
 * OpsScheduleConfig의 동적 트리거로 호출된다.
 * - 지난 7일간 실패 상위 N개 카테고리
 * - 이번 주 vs 지난 주 레이턴시 중앙값 비교
 * - LLM 주간 비용 집계 (LlmCostService 연동)
 */
@Component
class WeeklyOpsActionReportScheduler(
    private val pipelineRunStore: PipelineRunStore,
    private val notifier: OpsLogNotifier,
    private val llmCostService: LlmCostService,
) {
    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")

    /** OpsScheduleConfig 트리거 진입점. */
    fun runOnce(now: Instant) {
        val report = buildReport(now)
        notifier.postWeeklyActionReport(report)
    }

    /**
     * now 기준으로 WeeklyActionReport를 조립한다.
     * 테스트에서 직접 호출 가능하도록 열어 둔다.
     */
    fun buildReport(now: Instant): WeeklyActionReport {
        val weekEnd = now.atZone(seoul).toLocalDate()
        val weekStart = weekEnd.minusDays(6)

        val thisWeekFrom = weekStart.atStartOfDay(seoul).toInstant()
        val thisWeekTo = now

        val prevWeekFrom = thisWeekFrom.minus(7, ChronoUnit.DAYS)
        val prevWeekTo = thisWeekFrom

        // 지난 7일간 실패 상위 5개 카테고리를 집계한다
        val topFailingSources = pipelineRunStore.findTopFailingSourcesSince(thisWeekFrom, limit = 5)
            .map { RiskSourceSummary(it.categoryId, it.categoryName, it.failureCount) }

        // 이번 주 레이턴시 중앙값을 계산한다
        val latencyCurrentMs = latencyMedian(pipelineRunStore.findDurationsBetween(thisWeekFrom, thisWeekTo))

        // 지난 주 레이턴시 중앙값을 계산한다
        val latencyPreviousMs = latencyMedian(pipelineRunStore.findDurationsBetween(prevWeekFrom, prevWeekTo))

        // 이번 주 LLM 비용 집계 (USD → KRW 변환)
        val weeklyUsageUsd = llmCostService.summarizeByChannel(thisWeekFrom, thisWeekTo).totalEstimatedUsd
        val llmWeeklyUsageKrw = (weeklyUsageUsd * USD_TO_KRW).toLong()

        // 주간 예산 (월간 예산 / 4 로 근사한다)
        val budgetSetting = llmCostService.getBudget()
        val llmWeeklyBudgetKrw = (budgetSetting.monthlyBudgetUsd * USD_TO_KRW / 4).toLong()

        // 키워드 변동 이벤트 테이블이 아직 없으므로 리포트 계약상 0으로 고정한다.
        val keywordVolatilityCount = 0

        // clickDeclineCategories — 클릭률 데이터 인프라 미확인. null로 처리.
        return WeeklyActionReport(
            weekStart = weekStart,
            weekEnd = weekEnd,
            topFailingSources = topFailingSources,
            latencyMedianMsCurrent = latencyCurrentMs,
            latencyMedianMsPrevious = latencyPreviousMs,
            llmWeeklyUsageKrw = llmWeeklyUsageKrw,
            llmWeeklyBudgetKrw = llmWeeklyBudgetKrw,
            keywordVolatilityCount = keywordVolatilityCount,
            clickDeclineCategories = null,
        )
    }

    /**
     * duration 목록에서 중앙값(median)을 JVM에서 계산한다.
     * 빈 목록이면 0을 반환한다.
     */
    internal fun latencyMedian(durations: List<Long>): Long {
        if (durations.isEmpty()) return 0L
        val sorted = durations.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid]
        else (sorted[mid - 1] + sorted[mid]) / 2
    }

    companion object {
        /** USD → KRW 변환 고정 환율. */
        private const val USD_TO_KRW = 1_350.0
    }
}
