package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.model.LlmRun
import com.ohmyclipping.model.ReviewDecisionStatus
import com.ohmyclipping.model.ReviewItemDecision
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.LlmRunStore
import com.ohmyclipping.store.ReviewItemDecisionStore
import com.ohmyclipping.store.StatsStore
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * PRD 운영 KPI를 일 단위로 집계해 제공한다.
 */
@Service
class OperationalKpiService(
    private val categoryStore: CategoryStore,
    private val statsStore: StatsStore,
    private val reviewItemDecisionStore: ReviewItemDecisionStore,
    private val batchSummaryStore: BatchSummaryStore,
    private val llmRunStore: LlmRunStore,
    private val properties: ClippingMcpServerProperties
) {

    private val seoulZone = ZoneId.of("Asia/Seoul")

    fun getDailyKpis(
        categoryId: String?,
        from: LocalDate,
        to: LocalDate
    ): List<DailyOperationalKpi> {
        ensureValid(!to.isBefore(from)) { "to must be greater than or equal to from" }
        ensureValid(Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays() <= 180) {
            "date range must be 180 days or less"
        }
        if (!categoryId.isNullOrBlank()) {
            categoryStore.findById(categoryId) ?: throw NotFoundException("Category not found: $categoryId")
        }

        val statsRows = statsStore.findDailyRange(categoryId, from, to)
        val byDate = mutableMapOf<LocalDate, DailyKpiAccumulator>()
        statsRows.forEach { stat ->
            val acc = byDate.getOrPut(stat.statDate) { DailyKpiAccumulator() }
            acc.itemsCollected += stat.itemsCollected
            acc.itemsDuplicates += stat.itemsDuplicates
            acc.sendAttempts += stat.slackSendAttempts
            acc.sendSuccesses += stat.slackSendSuccesses
        }

        val fromInstant = from.atStartOfDay(seoulZone).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val reviewed = reviewItemDecisionStore.findReviewedBetween(fromInstant, toInstant, categoryId)
        val summaryCreatedAtById = loadSummaryCreatedAtById(reviewed)
        reviewed.forEach { decision ->
            val reviewedAt = decision.reviewedAt ?: return@forEach
            val reviewedDate = reviewedAt.atZone(seoulZone).toLocalDate()
            val acc = byDate.getOrPut(reviewedDate) { DailyKpiAccumulator() }

            if (decision.status == ReviewDecisionStatus.EXCLUDE) {
                acc.excludedCount += 1
            }

            val summaryCreatedAt = summaryCreatedAtById[decision.summaryId] ?: return@forEach
            val leadSeconds = Duration.between(summaryCreatedAt, reviewedAt).seconds
            if (leadSeconds >= 0) {
                acc.reviewLeadTimeSeconds += leadSeconds.toDouble()
                acc.reviewLeadTimeSamples += 1
            }
        }

        val llmRuns = llmRunStore.findByCreatedAtBetween(fromInstant, toInstant, categoryId)
        llmRuns.forEach { run ->
            val date = run.createdAt.atZone(seoulZone).toLocalDate()
            val acc = byDate.getOrPut(date) { DailyKpiAccumulator() }
            val inputCost = (billableInputTokens(run) / 1_000_000.0) * properties.llmInputCostPerMillionUsd
            val outputCost = (billableOutputTokens(run) / 1_000_000.0) * properties.llmOutputCostPerMillionUsd
            acc.llmEstimatedCostUsd += inputCost + outputCost
        }

        return dateSequence(from, to).map { date ->
            val acc = byDate[date] ?: DailyKpiAccumulator()
            val collected = acc.itemsCollected
            val noiseRate = ratio(acc.excludedCount, collected)
            val duplicateRate = ratio(acc.itemsDuplicates, collected)
            val sendSuccessRate = ratio(acc.sendSuccesses, acc.sendAttempts)
            val reviewLeadTimeHours = if (acc.reviewLeadTimeSamples == 0) {
                0.0
            } else {
                (acc.reviewLeadTimeSeconds / acc.reviewLeadTimeSamples.toDouble()) / 3600.0
            }

            DailyOperationalKpi(
                statDate = date,
                categoryId = categoryId,
                itemsCollected = collected,
                excludedCount = acc.excludedCount,
                itemsDuplicates = acc.itemsDuplicates,
                noiseRate = noiseRate,
                duplicateRate = duplicateRate,
                reviewLeadTimeHours = reviewLeadTimeHours,
                llmEstimatedCostUsd = acc.llmEstimatedCostUsd,
                sendAttempts = acc.sendAttempts,
                sendSuccesses = acc.sendSuccesses,
                sendSuccessRate = sendSuccessRate
            )
        }
    }

    /**
     * 리뷰 리드타임 계산에 필요한 summary 생성 시각을 일괄 조회한다.
     *
     * 동일 summary가 여러 리뷰 결정에 반복되어도 ID를 한 번만 조회해 운영 KPI의 N+1 쿼리를 방지한다.
     */
    private fun loadSummaryCreatedAtById(reviewed: List<ReviewItemDecision>): Map<String, Instant> {
        val summaryIds = reviewed.map { it.summaryId }.distinct()
        if (summaryIds.isEmpty()) return emptyMap()

        // 리드타임 계산에는 생성 시각만 필요하므로 일괄 조회 결과를 ID 기준 Map으로 축약한다.
        return batchSummaryStore.findByIds(summaryIds).associate { it.id to it.createdAt }
    }

    private fun dateSequence(from: LocalDate, to: LocalDate): List<LocalDate> {
        val days = Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays().toInt()
        return (0 until days).map { from.plusDays(it.toLong()) }
    }

    private fun ratio(numerator: Int, denominator: Int): Double {
        if (denominator <= 0) return 0.0
        return numerator.toDouble() / denominator.toDouble()
    }

    private fun billableInputTokens(run: LlmRun): Long =
        run.tokensIn?.toLong() ?: estimateTokens(run.inputChars)

    private fun billableOutputTokens(run: LlmRun): Long =
        run.tokensOut?.toLong() ?: estimateTokens(run.outputChars)

    private fun estimateTokens(chars: Int): Long =
        kotlin.math.ceil(chars.coerceAtLeast(0).toDouble() / 4.0).toLong()

    private data class DailyKpiAccumulator(
        var itemsCollected: Int = 0,
        var excludedCount: Int = 0,
        var itemsDuplicates: Int = 0,
        var reviewLeadTimeSeconds: Double = 0.0,
        var reviewLeadTimeSamples: Int = 0,
        var llmEstimatedCostUsd: Double = 0.0,
        var sendAttempts: Int = 0,
        var sendSuccesses: Int = 0
    )
}

data class DailyOperationalKpi(
    val statDate: LocalDate,
    val categoryId: String?,
    val itemsCollected: Int,
    val excludedCount: Int,
    val itemsDuplicates: Int,
    val noiseRate: Double,
    val duplicateRate: Double,
    val reviewLeadTimeHours: Double,
    val llmEstimatedCostUsd: Double,
    val sendAttempts: Int,
    val sendSuccesses: Int,
    val sendSuccessRate: Double
)
