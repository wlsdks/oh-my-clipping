package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.model.BudgetSetting
import com.ohmyclipping.model.LlmRun
import com.ohmyclipping.service.dto.*
import com.ohmyclipping.store.BudgetSettingStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.CostAlertNotificationStore
import com.ohmyclipping.store.LlmRunStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.ceil

@Service
class LlmCostService(
    private val jdbc: JdbcTemplate,
    private val properties: ClippingMcpServerProperties,
    private val llmRunStore: LlmRunStore,
    private val budgetSettingStore: BudgetSettingStore,
    private val categoryStore: CategoryStore,
    private val costAlertNotificationStore: CostAlertNotificationStore,
) {

    data class CostSummary(
        val from: Instant,
        val to: Instant,
        val inputCostPerMillionUsd: Double,
        val outputCostPerMillionUsd: Double,
        val totalRequestCount: Int,
        val totalTokensIn: Long,
        val totalTokensOut: Long,
        val totalEstimatedUsd: Double,
        val rows: List<CostRow>
    )

    data class CostRow(
        val channelId: String,
        val categoryId: String,
        val categoryName: String,
        val requestCount: Int,
        val tokensIn: Long,
        val tokensOut: Long,
        val estimatedUsd: Double
    )

    /**
     * 채널/카테고리별 LLM 비용을 집계한다.
     *
     * 원본 llm_runs row를 모두 애플리케이션으로 가져오지 않고 DB에서 토큰 합계와 요청 수를 먼저 줄인다.
     */
    fun summarizeByChannel(from: Instant, to: Instant, categoryId: String? = null): CostSummary {
        val categoryFilter = categoryId.orEmpty()
        val rows = jdbc.queryForList(
            """
            SELECT
                COALESCE(bc.slack_channel_id, '') AS channel_id,
                bc.id AS category_id,
                bc.name AS category_name,
                COUNT(*) AS request_count,
                COALESCE(SUM(COALESCE(lr.tokens_in, CEILING(lr.input_chars / 4.0))), 0) AS tokens_in,
                COALESCE(SUM(COALESCE(lr.tokens_out, CEILING(lr.output_chars / 4.0))), 0) AS tokens_out
            FROM llm_runs lr
            JOIN batch_categories bc ON bc.id = lr.category_id
            WHERE lr.created_at >= ? AND lr.created_at <= ?
              AND (? = '' OR lr.category_id = ?)
            GROUP BY COALESCE(bc.slack_channel_id, ''), bc.id, bc.name
            """.trimIndent(),
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to),
            categoryFilter,
            categoryFilter
        )

        // DB에서 축약된 집계 row만 비용 DTO로 변환한다.
        val costRows = rows.map { row ->
            val tokensIn = (row["tokens_in"] as? Number)?.toLong() ?: 0L
            val tokensOut = (row["tokens_out"] as? Number)?.toLong() ?: 0L
            val requestCount = (row["request_count"] as? Number)?.toInt() ?: 0
            val channelId = row["channel_id"]?.toString().orEmpty()
            CostRow(
                channelId = channelId.ifBlank { "(기본 채널)" },
                categoryId = row["category_id"]?.toString().orEmpty(),
                categoryName = row["category_name"]?.toString().orEmpty(),
                requestCount = requestCount,
                tokensIn = tokensIn,
                tokensOut = tokensOut,
                estimatedUsd = estimateUsd(tokensIn, tokensOut)
            )
        }.sortedByDescending { it.estimatedUsd }

        val totalTokensIn = costRows.sumOf { it.tokensIn }
        val totalTokensOut = costRows.sumOf { it.tokensOut }

        return CostSummary(
            from = from,
            to = to,
            inputCostPerMillionUsd = properties.llmInputCostPerMillionUsd,
            outputCostPerMillionUsd = properties.llmOutputCostPerMillionUsd,
            totalRequestCount = costRows.sumOf { it.requestCount },
            totalTokensIn = totalTokensIn,
            totalTokensOut = totalTokensOut,
            totalEstimatedUsd = estimateUsd(totalTokensIn, totalTokensOut),
            rows = costRows
        )
    }

    private fun estimateTokens(chars: Int): Int =
        ceil(chars.coerceAtLeast(0).toDouble() / 4.0).toInt()

    // ── Overview: 기간별 비용 현황 + 예산 대비 + 전월 비교 ──

    /**
     * 지정 기간의 비용 개요를 반환한다.
     * 일별 비용 분포, 전기 대비 변동률, 월말 예상치, 예산 대비 사용률을 포함한다.
     */
    fun getOverview(from: LocalDate, to: LocalDate, categoryId: String?): CostOverviewData {
        // 현재 기간 데이터 조회
        val fromInstant = from.atStartOfDay(seoulZone).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val runs = llmRunStore.findByCreatedAtBetween(fromInstant, toInstant, categoryId)

        // 일별 집계
        val dailyMap = mutableMapOf<LocalDate, DailyAccumulator>()
        for (run in runs) {
            val date = run.createdAt.atZone(seoulZone).toLocalDate()
            val acc = dailyMap.getOrPut(date) { DailyAccumulator() }
            acc.inputCost += inputCostOf(run)
            acc.outputCost += outputCostOf(run)
            acc.requestCount++
        }

        // from~to 사이 모든 날짜를 빈 값 포함하여 생성
        val allDates = dateSequence(from, to)
        val dailyBreakdown = allDates.map { date ->
            val acc = dailyMap[date]
            DailyCostData(
                date = date,
                inputCostUsd = acc?.inputCost ?: 0.0,
                outputCostUsd = acc?.outputCost ?: 0.0,
                totalCostUsd = (acc?.inputCost ?: 0.0) + (acc?.outputCost ?: 0.0),
                requestCount = acc?.requestCount ?: 0
            )
        }

        val totalCost = dailyBreakdown.sumOf { it.totalCostUsd }
        val totalRequests = dailyBreakdown.sumOf { it.requestCount }
        val periodDays = allDates.size.coerceAtLeast(1)
        val dailyAvgRequests = totalRequests.toDouble() / periodDays

        // 이전 기간 비교 (동일 길이)
        val prevFrom = from.minusDays(periodDays.toLong())
        val prevTo = from.minusDays(1)
        val prevFromInstant = prevFrom.atStartOfDay(seoulZone).toInstant()
        val prevToInstant = prevTo.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val prevCost = estimateCostBetween(prevFromInstant, prevToInstant, categoryId)
        val costChangePercent = if (prevCost > 0.0) ((totalCost - prevCost) / prevCost) * 100.0 else 0.0

        // 월말 예상 비용
        val now = LocalDate.now(seoulZone)
        val monthStart = now.withDayOfMonth(1)
        val daysInMonth = now.lengthOfMonth()
        val daysElapsed = now.dayOfMonth.coerceAtLeast(1)
        val monthStartInstant = monthStart.atStartOfDay(seoulZone).toInstant()
        val monthNowInstant = now.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val monthCostSoFar = estimateCostBetween(monthStartInstant, monthNowInstant, categoryId)
        val projectedMonthEnd = (monthCostSoFar / daysElapsed) * daysInMonth

        // 예산 정보
        val budget = budgetSettingStore.get()
        val budgetUsd = if (budget.monthlyBudgetUsd > 0) budget.monthlyBudgetUsd else null
        val budgetUsedPercent = if (budgetUsd != null && budgetUsd > 0) {
            (monthCostSoFar / budgetUsd) * 100.0
        } else null

        return CostOverviewData(
            from = from,
            to = to,
            totalCostUsd = totalCost,
            totalRequests = totalRequests,
            dailyAvgRequests = dailyAvgRequests,
            projectedMonthEndUsd = projectedMonthEnd,
            previousPeriodCostUsd = prevCost,
            costChangePercent = costChangePercent,
            budgetUsd = budgetUsd,
            budgetUsedPercent = budgetUsedPercent,
            dailyBreakdown = dailyBreakdown
        )
    }

    // ── Hourly: 특정 일자의 시간대별 비용 분포 ──

    /**
     * 특정 날짜의 시간대별(0~23시) 비용 분포를 반환한다.
     * 데이터 없는 시간대는 0으로 채운다.
     */
    fun getHourlyBreakdown(date: LocalDate, categoryId: String?): HourlyCostData {
        val fromInstant = date.atStartOfDay(seoulZone).toInstant()
        val toInstant = date.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val runs = llmRunStore.findByCreatedAtBetween(fromInstant, toInstant, categoryId)

        // 시간대별 집계
        val hourlyMap = mutableMapOf<Int, DailyAccumulator>()
        for (run in runs) {
            val hour = run.createdAt.atZone(seoulZone).hour
            val acc = hourlyMap.getOrPut(hour) { DailyAccumulator() }
            acc.inputCost += inputCostOf(run)
            acc.outputCost += outputCostOf(run)
            acc.requestCount++
        }

        // 0~23시 모든 시간대 포함
        val hours = (0..23).map { hour ->
            val acc = hourlyMap[hour]
            HourlyCostRowData(
                hour = hour,
                inputCostUsd = acc?.inputCost ?: 0.0,
                outputCostUsd = acc?.outputCost ?: 0.0,
                totalCostUsd = (acc?.inputCost ?: 0.0) + (acc?.outputCost ?: 0.0),
                requestCount = acc?.requestCount ?: 0
            )
        }

        return HourlyCostData(date = date, hours = hours)
    }

    // ── Models: 모델별·프롬프트 버전별·카테고리별 비용 분석 ──

    /**
     * 모델별 비용 비중, 프롬프트 버전별 효율, 카테고리별 비용 분포를 반환한다.
     * 전기 대비 건당 비용 변동도 포함한다.
     */
    fun getModels(from: LocalDate, to: LocalDate, categoryId: String?): CostModelsData {
        val fromInstant = from.atStartOfDay(seoulZone).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val runs = llmRunStore.findByCreatedAtBetween(fromInstant, toInstant, categoryId)

        val totalCost = runs.sumOf { estimateRunCost(it) }
        val totalCount = runs.size

        // 모델별 집계
        val modelGroups = runs.groupBy { it.model }
        val models = modelGroups.map { (model, modelRuns) ->
            val inputCost = modelRuns.sumOf { inputCostOf(it) }
            val outputCost = modelRuns.sumOf { outputCostOf(it) }
            val modelTotal = inputCost + outputCost
            ModelCostData(
                model = model,
                requestCount = modelRuns.size,
                inputCostUsd = inputCost,
                outputCostUsd = outputCost,
                totalCostUsd = modelTotal,
                costPercent = if (totalCost > 0) (modelTotal / totalCost) * 100.0 else 0.0
            )
        }.sortedByDescending { it.totalCostUsd }

        // 프롬프트 버전별 집계
        val promptGroups = runs.groupBy { it.promptVersion }
        val promptVersions = promptGroups.map { (version, versionRuns) ->
            val avgTokensIn = versionRuns.map { (it.tokensIn ?: estimateTokens(it.inputChars)).toLong() }
                .average().toLong()
            val avgTokensOut = versionRuns.map { (it.tokensOut ?: estimateTokens(it.outputChars)).toLong() }
                .average().toLong()
            val versionCost = versionRuns.sumOf { estimateRunCost(it) }
            val avgDuration = versionRuns.map { it.durationMs }.average().toLong()
            PromptVersionData(
                promptVersion = version,
                requestCount = versionRuns.size,
                avgTokensIn = avgTokensIn,
                avgTokensOut = avgTokensOut,
                costPerArticleUsd = if (versionRuns.isNotEmpty()) versionCost / versionRuns.size else 0.0,
                avgDurationMs = avgDuration
            )
        }.sortedByDescending { it.requestCount }

        // 카테고리별 집계
        val catGroups = runs.groupBy { it.categoryId }
        val categoryBreakdown = catGroups.map { (catId, catRuns) ->
            val catCost = catRuns.sumOf { estimateRunCost(it) }
            CategoryCostData(
                categoryId = catId,
                categoryName = getCategoryName(catId),
                totalCostUsd = catCost,
                costPercent = if (totalCost > 0) (catCost / totalCost) * 100.0 else 0.0,
                requestCount = catRuns.size
            )
        }.sortedByDescending { it.totalCostUsd }

        // 건당 비용 (현재 / 이전 기간)
        val costPerArticle = if (totalCount > 0) totalCost / totalCount else 0.0
        val periodDays = dateSequence(from, to).size.coerceAtLeast(1)
        val prevFrom = from.minusDays(periodDays.toLong())
        val prevTo = from.minusDays(1)
        val prevFromInstant = prevFrom.atStartOfDay(seoulZone).toInstant()
        val prevToInstant = prevTo.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val prevRuns = llmRunStore.findByCreatedAtBetween(prevFromInstant, prevToInstant, categoryId)
        val prevCostPerArticle = if (prevRuns.isNotEmpty()) {
            prevRuns.sumOf { estimateRunCost(it) } / prevRuns.size
        } else 0.0

        return CostModelsData(
            from = from,
            to = to,
            modelCount = modelGroups.size,
            costPerArticleUsd = costPerArticle,
            previousCostPerArticleUsd = prevCostPerArticle,
            models = models,
            promptVersions = promptVersions,
            categoryBreakdown = categoryBreakdown
        )
    }

    // ── Reliability: 성공률·응답 시간 분포·에러 패턴 분석 ──

    /**
     * 성공/실패/빈 결과 비율, 응답 시간 P50/P95, 일별 안정성, 에러 Top 5를 반환한다.
     */
    fun getReliability(from: LocalDate, to: LocalDate, categoryId: String?): CostReliabilityData {
        val fromInstant = from.atStartOfDay(seoulZone).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val runs = llmRunStore.findByCreatedAtBetween(fromInstant, toInstant, categoryId)

        val total = runs.size
        val succeeded = runs.count { it.status == "SUCCEEDED" }
        val emptyResult = runs.count { it.status == "EMPTY_RESULT" }
        val failed = runs.count { it.status == "FAILED" }

        // 전체 응답 시간 퍼센타일
        val allDurations = runs.map { it.durationMs }.sorted()
        val avgDuration = if (allDurations.isNotEmpty()) allDurations.average().toLong() else 0L
        val p50 = percentile(allDurations, 50)
        val p95 = percentile(allDurations, 95)

        // 일별 안정성 분석
        val allDates = dateSequence(from, to)
        val dailyGrouped = runs.groupBy { it.createdAt.atZone(seoulZone).toLocalDate() }
        val dailyBreakdown = allDates.map { date ->
            val dayRuns = dailyGrouped[date] ?: emptyList()
            val dayDurations = dayRuns.map { it.durationMs }.sorted()
            DailyReliabilityData(
                date = date,
                succeeded = dayRuns.count { it.status == "SUCCEEDED" },
                emptyResult = dayRuns.count { it.status == "EMPTY_RESULT" },
                failed = dayRuns.count { it.status == "FAILED" },
                avgDurationMs = if (dayDurations.isNotEmpty()) dayDurations.average().toLong() else 0L,
                p50DurationMs = percentile(dayDurations, 50),
                p95DurationMs = percentile(dayDurations, 95)
            )
        }

        // 에러 Top 5: 실패 건을 에러 메시지로 그룹화
        val failedRuns = runs.filter { it.status == "FAILED" && !it.errorMessage.isNullOrBlank() }
        val errorGroups = failedRuns.groupBy { it.errorMessage.orEmpty() }
        val topErrors = errorGroups.entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { (errorMsg, errorRuns) ->
                ErrorGroupData(
                    errorPattern = errorMsg,
                    count = errorRuns.size,
                    lastOccurred = errorRuns.maxOf { it.createdAt },
                    affectedCategories = errorRuns.map { getCategoryName(it.categoryId) }.distinct()
                )
            }

        return CostReliabilityData(
            from = from,
            to = to,
            successRate = safeRate(succeeded, total),
            emptyResultRate = safeRate(emptyResult, total),
            failureRate = safeRate(failed, total),
            avgDurationMs = avgDuration,
            p50DurationMs = p50,
            p95DurationMs = p95,
            dailyBreakdown = dailyBreakdown,
            topErrors = topErrors
        )
    }

    // ── Budget: 예산 설정 조회/저장 ──

    /** 현재 예산 설정을 반환한다. */
    fun getBudget(): BudgetSetting = budgetSettingStore.get()

    /** 예산 설정을 저장하고 저장된 결과를 반환한다. */
    fun saveBudget(setting: BudgetSetting): BudgetSetting = budgetSettingStore.save(setting)

    // ── Budget guard: 월 예산 초과 여부를 빠르게 확인하는 퍼블릭 메서드 ──

    /**
     * 이번 달 누적 LLM 비용이 월 예산을 초과했는지 확인한다.
     * 예산이 설정되지 않았거나 0이면 항상 false를 반환한다.
     *
     * @return 예산 초과 시 true
     */
    fun isMonthlyBudgetExceeded(): Boolean {
        val budget = budgetSettingStore.get()
        if (budget.monthlyBudgetUsd <= 0) return false
        val currentCost = getCurrentMonthCostUsd()
        return currentCost >= budget.monthlyBudgetUsd
    }

    /**
     * 이번 달 누적 LLM 비용(USD)을 계산한다.
     */
    fun getCurrentMonthCostUsd(): Double {
        val now = LocalDate.now(seoulZone)
        val monthStart = now.withDayOfMonth(1)
        val monthStartInstant = monthStart.atStartOfDay(seoulZone).toInstant()
        val monthNowInstant = now.plusDays(1).atStartOfDay(seoulZone).toInstant()
        return estimateCostBetween(monthStartInstant, monthNowInstant, null)
    }

    // ── Budget alert: 현재 월 예산 알림 상태 조회 ──

    /**
     * 현재 월의 예산 알림 상태를 반환한다.
     * CRITICAL_100 > CRITICAL_90 우선순위로 활성 임계값 레벨을 결정한다.
     *
     * @return 현재 월 예산 사용률, 임계값 레벨, 남은 일수를 담은 결과
     */
    fun currentBudgetAlert(): CurrentBudgetAlertResult {
        val now = Instant.now()
        val monthId = YearMonth.from(now.atZone(seoulZone)).toString()

        // 이번 달 활성 임계값 레벨을 조회한다
        val criticals = costAlertNotificationStore.findActiveCriticalsByMonth(monthId)
        val currentLevel = when {
            criticals.any { it.thresholdLevel == "CRITICAL_100" } -> "CRITICAL_100"
            criticals.any { it.thresholdLevel == "CRITICAL_90" } -> "CRITICAL_90"
            else -> null
        }

        // 현재 월 예산 사용률을 계산한다
        val currentCost = getCurrentMonthCostUsd()
        val budget = budgetSettingStore.get()
        val usagePct = if (budget.monthlyBudgetUsd > 0) {
            ((currentCost / budget.monthlyBudgetUsd) * 100).toInt().coerceAtLeast(0)
        } else 0

        // 월말까지 남은 일수를 계산한다
        val today = now.atZone(seoulZone).toLocalDate()
        val remainingDays = today.lengthOfMonth() - today.dayOfMonth

        return CurrentBudgetAlertResult(
            monthId = monthId,
            currentLevel = currentLevel,
            usagePct = usagePct,
            remainingDays = remainingDays,
        )
    }

    data class CurrentBudgetAlertResult(
        val monthId: String,
        val currentLevel: String?,
        val usagePct: Int,
        val remainingDays: Int,
    )

    // ── Private helpers ──

    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

    /** from부터 to까지(양 끝 포함) 날짜 시퀀스를 생성한다. */
    private fun dateSequence(from: LocalDate, to: LocalDate): List<LocalDate> {
        val days = Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays().toInt()
        return (0 until days).map { from.plusDays(it.toLong()) }
    }

    /** LlmRun 한 건의 총 비용(USD)을 추정한다. */
    private fun estimateRunCost(run: LlmRun): Double {
        val ti = (run.tokensIn ?: estimateTokens(run.inputChars)).toLong()
        val to = (run.tokensOut ?: estimateTokens(run.outputChars)).toLong()
        return estimateUsd(ti, to)
    }

    /** LlmRun 한 건의 입력 비용(USD)을 계산한다. */
    private fun inputCostOf(run: LlmRun): Double {
        val ti = (run.tokensIn ?: estimateTokens(run.inputChars)).toLong()
        return (ti / 1_000_000.0) * properties.llmInputCostPerMillionUsd
    }

    /** LlmRun 한 건의 출력 비용(USD)을 계산한다. */
    private fun outputCostOf(run: LlmRun): Double {
        val to = (run.tokensOut ?: estimateTokens(run.outputChars)).toLong()
        return (to / 1_000_000.0) * properties.llmOutputCostPerMillionUsd
    }

    /** 정렬된 리스트에서 p번째 퍼센타일 값을 반환한다. */
    private fun percentile(sorted: List<Long>, p: Int): Long {
        if (sorted.isEmpty()) return 0L
        val index = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    /** 분모가 0이면 0.0을 반환하는 안전한 비율 계산. */
    private fun safeRate(numerator: Int, denominator: Int): Double =
        if (denominator > 0) numerator.toDouble() / denominator else 0.0

    /** 카테고리 ID로 이름을 조회한다. 삭제된 카테고리면 기본 문자열을 반환한다. */
    private fun getCategoryName(id: String): String =
        categoryStore.findById(id)?.name ?: "(삭제된 카테고리)"

    private fun estimateUsd(tokensIn: Long, tokensOut: Long): Double {
        val inCost = (tokensIn / 1_000_000.0) * properties.llmInputCostPerMillionUsd
        val outCost = (tokensOut / 1_000_000.0) * properties.llmOutputCostPerMillionUsd
        return inCost + outCost
    }

    /** 비용 합계만 필요한 경로는 llm_runs 전체 row 대신 DB 집계 토큰으로 계산한다. */
    private fun estimateCostBetween(from: Instant, to: Instant, categoryId: String?): Double {
        val (tokensIn, tokensOut) = llmRunStore.sumBillableTokensBetween(from, to, categoryId)
        return estimateUsd(tokensIn, tokensOut)
    }

    private data class DailyAccumulator(
        var inputCost: Double = 0.0,
        var outputCost: Double = 0.0,
        var requestCount: Int = 0
    )

}
