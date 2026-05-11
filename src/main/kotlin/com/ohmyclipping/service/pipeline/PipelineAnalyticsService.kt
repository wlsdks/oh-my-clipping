package com.ohmyclipping.service.pipeline

import com.ohmyclipping.service.LlmCostService
import com.ohmyclipping.service.OperationalKpiService
import com.ohmyclipping.service.dto.analytics.DeliveryMatrixCategory
import com.ohmyclipping.service.dto.analytics.DeliveryMatrixResponse
import com.ohmyclipping.service.dto.analytics.DeliveryMatrixUser
import com.ohmyclipping.service.dto.analytics.PipelineDailyResponse
import com.ohmyclipping.service.dto.analytics.PipelineDailyRow
import com.ohmyclipping.service.dto.analytics.PipelinePeriodSummary
import com.ohmyclipping.service.dto.analytics.PipelineSummaryResponse
import com.ohmyclipping.store.pipeline.DeliveryLogStatus
import com.ohmyclipping.store.pipeline.LlmRunStatus
import com.ohmyclipping.store.pipeline.PipelineAnalyticsStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

/**
 * 파이프라인 대시보드용 분석 서비스.
 * 수집 -> 요약 -> 발송 전체 흐름의 일간/기간별 통계와 사용자-카테고리 매트릭스를 제공한다.
 *
 * 기존 OperationalKpiService/LlmCostService의 도메인 메서드를 조합하고,
 * 발송/LLM 집계는 PipelineAnalyticsStore에 위임한다.
 */
@Service
class PipelineAnalyticsService(
    private val store: PipelineAnalyticsStore,
    private val llmCostService: LlmCostService,
    private val operationalKpiService: OperationalKpiService
) {

    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

    // ── 오늘 파이프라인 요약 ──

    /**
     * 오늘(KST) 기준 파이프라인 전체 요약을 반환한다.
     * 수집/중복/요약/거절/실패/발송/비용을 한 번에 집계한다.
     * readOnly 트랜잭션으로 스냅샷 일관성을 보장한다.
     */
    @Transactional(readOnly = true)
    fun getPipelineSummary(): PipelineSummaryResponse {
        val today = LocalDate.now(seoulZone)
        val todayStart = today.atStartOfDay(seoulZone).toInstant()
        val tomorrowStart = today.plusDays(1).atStartOfDay(seoulZone).toInstant()

        // 수집/중복 통계: OperationalKpiService 활용
        val kpis = operationalKpiService.getDailyKpis(null, today, today)
        val todayKpi = kpis.firstOrNull()
        val collected = todayKpi?.itemsCollected ?: 0
        val duplicateSkipped = todayKpi?.itemsDuplicates ?: 0

        // 요약 성공/거절/실패
        val llmCounts = store.queryLlmStatusCounts(todayStart, tomorrowStart)
        val summarized = llmCounts[LlmRunStatus.SUCCEEDED] ?: 0
        val rejected = llmCounts[LlmRunStatus.EMPTY_RESULT] ?: 0
        val failed = llmCounts[LlmRunStatus.FAILED] ?: 0

        // 발송 통계
        val deliveryCounts = store.queryDeliveryStatusCounts(today, today)
        val deliverySent = deliveryCounts[DeliveryLogStatus.SENT] ?: 0
        val deliverySkipped = deliveryCounts[DeliveryLogStatus.SKIPPED] ?: 0
        val deliveryFailed = deliveryCounts[DeliveryLogStatus.FAILED] ?: 0

        // 비용/예산: LlmCostService 활용
        val todayCost = todayKpi?.llmEstimatedCostUsd ?: 0.0
        val budget = llmCostService.getBudget()
        val monthCost = llmCostService.getCurrentMonthCostUsd()
        val budgetUsagePercent = if (budget.monthlyBudgetUsd > 0) {
            (monthCost / budget.monthlyBudgetUsd) * 100.0
        } else {
            0.0
        }

        return PipelineSummaryResponse(
            todayCollected = collected,
            todayDuplicateSkipped = duplicateSkipped,
            todaySummarized = summarized,
            todayRejected = rejected,
            todayFailed = failed,
            todayDeliverySent = deliverySent,
            todayDeliverySkipped = deliverySkipped,
            todayDeliveryFailed = deliveryFailed,
            todayCostUsd = todayCost,
            monthlyBudgetUsagePercent = budgetUsagePercent
        )
    }

    // ── 기간별 일간 추이 ──

    /**
     * 최근 N일간 일별 파이프라인 데이터와 거절 사유 분포를 반환한다.
     * readOnly 트랜잭션으로 스냅샷 일관성을 보장한다.
     *
     * @param days 조회 기간(일). 1~30 범위.
     */
    @Transactional(readOnly = true)
    fun getPipelineDaily(days: Int): PipelineDailyResponse {
        val today = LocalDate.now(seoulZone)
        val from = today.minusDays(days.toLong() - 1)

        // 수집/중복: OperationalKpiService
        val kpis = operationalKpiService.getDailyKpis(null, from, today)
        val kpiByDate = kpis.associateBy { it.statDate }

        // 요약 성공/거절/실패: LlmCostService.getReliability
        val reliability = llmCostService.getReliability(from, today, null)
        val reliabilityByDate = reliability.dailyBreakdown.associateBy { it.date }

        // 발송: delivery_log 일자별 집계
        val deliveryByDate = store.queryDeliveryDailyMap(from, today)

        // 거절 사유 분포
        val fromInstant = from.atStartOfDay(seoulZone).toInstant()
        val toInstant = today.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val rejectReasons = store.queryRejectReasons(fromInstant, toInstant)

        // 날짜 시퀀스 생성 (빈 날짜 포함)
        val allDates = (0L until days).map { from.plusDays(it) }
        val rows = allDates.map { date ->
            val kpi = kpiByDate[date]
            val rel = reliabilityByDate[date]
            val delivery = deliveryByDate[date]
            PipelineDailyRow(
                date = date.toString(),
                collected = kpi?.itemsCollected ?: 0,
                duplicateSkipped = kpi?.itemsDuplicates ?: 0,
                summarizeSucceeded = rel?.succeeded ?: 0,
                summarizeRejected = rel?.emptyResult ?: 0,
                summarizeFailed = rel?.failed ?: 0,
                deliverySent = delivery?.sent ?: 0,
                deliverySkipped = delivery?.skipped ?: 0,
                deliveryFailed = delivery?.failed ?: 0
            )
        }

        return PipelineDailyResponse(
            days = rows,
            periodSummary = PipelinePeriodSummary(rejectReasons = rejectReasons)
        )
    }

    // ── 사용자-카테고리 발송 매트릭스 ──

    /**
     * 최근 N일간 카테고리별 발송 매트릭스를 반환한다.
     * 각 카테고리의 소유자(구독자) 정보를 함께 표시한다.
     *
     * @param days 조회 기간(일). 1~30 범위.
     */
    @Transactional(readOnly = true)
    fun getDeliveryMatrix(days: Int): DeliveryMatrixResponse {
        val today = LocalDate.now(seoulZone)
        val from = today.minusDays(days.toLong() - 1)

        // 카테고리별 발송 통계 (delivery_log 기준, 팬아웃 없음)
        val categoryStats = store.queryDeliveryMatrixByCategory(from, today)

        // 카테고리 소유자 맵 (카테고리→사용자 목록)
        val ownerMap = store.queryCategoryOwners(categoryStats.map { it.categoryId })

        // 사용자별로 재구성: 각 사용자가 소유한 카테고리의 발송 통계
        val userMap = linkedMapOf<String, MutableList<DeliveryMatrixCategory>>()
        val userNameMap = mutableMapOf<String, String>()

        for (stat in categoryStats) {
            val owners = ownerMap[stat.categoryId] ?: continue
            for (owner in owners) {
                userNameMap[owner.userId] = owner.username
                userMap.computeIfAbsent(owner.userId) { mutableListOf() }.add(
                    DeliveryMatrixCategory(
                        categoryId = stat.categoryId,
                        categoryName = stat.categoryName,
                        sent = stat.sent,
                        skipped = stat.skipped,
                        failed = stat.failed
                    )
                )
            }
        }

        val users = userMap.map { (userId, categories) ->
            DeliveryMatrixUser(
                userId = userId,
                username = userNameMap[userId] ?: userId,
                categories = categories
            )
        }.sortedBy { it.username }

        return DeliveryMatrixResponse(users = users)
    }
}
