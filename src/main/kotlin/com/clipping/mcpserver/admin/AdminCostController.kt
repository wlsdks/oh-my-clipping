package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.*
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.BudgetSetting
import com.clipping.mcpserver.service.LlmCostService
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit



@RestController
@RequestMapping("/api/admin/costs")
class AdminCostController(
    private val llmCostService: LlmCostService
) {

    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

    // ── 기존 엔드포인트: 채널별 LLM 비용 요약 ──

    @GetMapping("/llm")
    fun llmCostSummary(
        @RequestParam(required = false, defaultValue = "30") days: Int
    ): LlmCostSummaryResponse {
        ensureValid(days in 1..365) { "조회 기간은 1~365일 사이여야 합니다." }
        val to = Instant.now()
        val from = to.minus(days.toLong(), ChronoUnit.DAYS)
        val summary = llmCostService.summarizeByChannel(from, to)
        return LlmCostSummaryResponse(
            from = summary.from.toString(),
            to = summary.to.toString(),
            inputCostPerMillionUsd = summary.inputCostPerMillionUsd,
            outputCostPerMillionUsd = summary.outputCostPerMillionUsd,
            totalRequestCount = summary.totalRequestCount,
            totalTokensIn = summary.totalTokensIn,
            totalTokensOut = summary.totalTokensOut,
            totalEstimatedUsd = summary.totalEstimatedUsd,
            rows = summary.rows.map { row ->
                LlmCostRowResponse(
                    channelId = row.channelId,
                    categoryId = row.categoryId,
                    categoryName = row.categoryName,
                    requestCount = row.requestCount,
                    tokensIn = row.tokensIn,
                    tokensOut = row.tokensOut,
                    estimatedUsd = row.estimatedUsd
                )
            }
        )
    }

    // ── 1. Overview: 기간별 비용 현황 + 예산 대비 + 전월 비교 ──

    @GetMapping("/overview")
    fun overview(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) categoryId: String?
    ): CostOverviewResponse {
        val (fromDate, toDate) = resolveCostDateRange(from, to)
        val data = llmCostService.getOverview(fromDate, toDate, categoryId)
        return CostOverviewResponse(
            from = data.from.toString(),
            to = data.to.toString(),
            totalCostUsd = data.totalCostUsd,
            totalRequests = data.totalRequests,
            dailyAvgRequests = data.dailyAvgRequests,
            projectedMonthEndUsd = data.projectedMonthEndUsd,
            previousPeriodCostUsd = data.previousPeriodCostUsd,
            costChangePercent = data.costChangePercent,
            budgetUsd = data.budgetUsd,
            budgetUsedPercent = data.budgetUsedPercent,
            dailyBreakdown = data.dailyBreakdown.map { d ->
                DailyCostRow(
                    date = d.date.toString(),
                    inputCostUsd = d.inputCostUsd,
                    outputCostUsd = d.outputCostUsd,
                    totalCostUsd = d.totalCostUsd,
                    requestCount = d.requestCount
                )
            }
        )
    }

    // ── 2. Hourly: 특정 날짜의 시간대별 비용 분포 ──

    @GetMapping("/overview/hourly")
    fun hourlyBreakdown(
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) categoryId: String?
    ): HourlyCostResponse {
        val targetDate = if (date != null) LocalDate.parse(date) else LocalDate.now(seoulZone)
        val data = llmCostService.getHourlyBreakdown(targetDate, categoryId)
        return HourlyCostResponse(
            date = data.date.toString(),
            hours = data.hours.map { h ->
                HourlyCostRow(
                    hour = h.hour,
                    inputCostUsd = h.inputCostUsd,
                    outputCostUsd = h.outputCostUsd,
                    totalCostUsd = h.totalCostUsd,
                    requestCount = h.requestCount
                )
            }
        )
    }

    // ── 3. Models: 모델별 비용 분석 ──

    @GetMapping("/models")
    fun models(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) categoryId: String?
    ): CostModelsResponse {
        val (fromDate, toDate) = resolveCostDateRange(from, to)
        val data = llmCostService.getModels(fromDate, toDate, categoryId)
        return CostModelsResponse(
            from = data.from.toString(),
            to = data.to.toString(),
            modelCount = data.modelCount,
            costPerArticleUsd = data.costPerArticleUsd,
            previousCostPerArticleUsd = data.previousCostPerArticleUsd,
            models = data.models.map { m ->
                ModelCostRow(
                    model = m.model,
                    requestCount = m.requestCount,
                    inputCostUsd = m.inputCostUsd,
                    outputCostUsd = m.outputCostUsd,
                    totalCostUsd = m.totalCostUsd,
                    costPercent = m.costPercent
                )
            },
            promptVersions = data.promptVersions.map { pv ->
                PromptVersionRow(
                    promptVersion = pv.promptVersion,
                    requestCount = pv.requestCount,
                    avgTokensIn = pv.avgTokensIn,
                    avgTokensOut = pv.avgTokensOut,
                    costPerArticleUsd = pv.costPerArticleUsd,
                    avgDurationMs = pv.avgDurationMs
                )
            },
            categoryBreakdown = data.categoryBreakdown.map { c ->
                CategoryCostRow(
                    categoryId = c.categoryId,
                    categoryName = c.categoryName,
                    totalCostUsd = c.totalCostUsd,
                    costPercent = c.costPercent,
                    requestCount = c.requestCount
                )
            }
        )
    }

    // ── 4. Reliability: 안정성 지표 ──

    @GetMapping("/reliability")
    fun reliability(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) categoryId: String?
    ): CostReliabilityResponse {
        val (fromDate, toDate) = resolveCostDateRange(from, to)
        val data = llmCostService.getReliability(fromDate, toDate, categoryId)
        return CostReliabilityResponse(
            from = data.from.toString(),
            to = data.to.toString(),
            successRate = data.successRate,
            emptyResultRate = data.emptyResultRate,
            failureRate = data.failureRate,
            avgDurationMs = data.avgDurationMs,
            p50DurationMs = data.p50DurationMs,
            p95DurationMs = data.p95DurationMs,
            dailyBreakdown = data.dailyBreakdown.map { d ->
                DailyReliabilityRow(
                    date = d.date.toString(),
                    succeeded = d.succeeded,
                    emptyResult = d.emptyResult,
                    failed = d.failed,
                    avgDurationMs = d.avgDurationMs,
                    p50DurationMs = d.p50DurationMs,
                    p95DurationMs = d.p95DurationMs
                )
            },
            topErrors = data.topErrors.map { e ->
                ErrorGroupRow(
                    errorPattern = e.errorPattern,
                    count = e.count,
                    lastOccurred = e.lastOccurred.toString(),
                    affectedCategories = e.affectedCategories
                )
            }
        )
    }

    // ── 5. Detail: 채널별 상세 비용 (Tab 4) ──

    @GetMapping("/detail")
    fun detail(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) categoryId: String?
    ): CostDetailResponse {
        val (fromDate, toDate) = resolveCostDateRange(from, to)
        // LocalDate → Instant 변환 (Asia/Seoul 기준)
        val fromInstant = fromDate.atStartOfDay(seoulZone).toInstant()
        val toInstant = toDate.plusDays(1).atStartOfDay(seoulZone).toInstant()
        val summary = llmCostService.summarizeByChannel(fromInstant, toInstant, categoryId)
        val totalUsd = summary.totalEstimatedUsd

        return CostDetailResponse(
            from = fromDate.toString(),
            to = toDate.toString(),
            inputCostPerMillionUsd = summary.inputCostPerMillionUsd,
            outputCostPerMillionUsd = summary.outputCostPerMillionUsd,
            rows = summary.rows.map { row ->
                CostDetailRowResponse(
                    channelId = row.channelId,
                    categoryId = row.categoryId,
                    categoryName = row.categoryName,
                    requestCount = row.requestCount,
                    tokensIn = row.tokensIn,
                    tokensOut = row.tokensOut,
                    estimatedUsd = row.estimatedUsd,
                    costPercent = if (totalUsd > 0) (row.estimatedUsd / totalUsd) * 100.0 else 0.0
                )
            }
        )
    }

    // ── 6. Budget: 예산 설정 조회 ──

    @GetMapping("/budget")
    fun getBudget(): BudgetSettingsResponse {
        val budget = llmCostService.getBudget()
        return BudgetSettingsResponse(
            monthlyBudgetUsd = budget.monthlyBudgetUsd,
            alertThresholdPercent = budget.alertThresholdPercent,
            slackAlertEnabled = budget.slackAlertEnabled
        )
    }

    // ── 7. Budget: 예산 설정 변경 ──

    @PutMapping("/budget")
    fun updateBudget(@RequestBody request: BudgetSettingsRequest): BudgetSettingsResponse {
        ensureValid(request.monthlyBudgetUsd >= 0) { "월간 예산은 0 이상이어야 합니다." }
        ensureValid(request.alertThresholdPercent in 1..100) { "알림 임계값은 1~100% 사이여야 합니다." }

        val saved = llmCostService.saveBudget(
            BudgetSetting(
                monthlyBudgetUsd = request.monthlyBudgetUsd,
                alertThresholdPercent = request.alertThresholdPercent,
                slackAlertEnabled = request.slackAlertEnabled
            )
        )
        return BudgetSettingsResponse(
            monthlyBudgetUsd = saved.monthlyBudgetUsd,
            alertThresholdPercent = saved.alertThresholdPercent,
            slackAlertEnabled = saved.slackAlertEnabled
        )
    }

    // ── 8. Alerts: 현재 월 예산 알림 상태 ──

    /**
     * 현재 월의 예산 알림 상태를 반환한다.
     * 홈 대시보드 Tier 1 예산 뱃지에 사용한다.
     */
    @GetMapping("/alerts/current")
    fun currentBudgetAlert(): CostAlertCurrentDto {
        val result = llmCostService.currentBudgetAlert()
        return CostAlertCurrentDto(
            monthId = result.monthId,
            currentLevel = result.currentLevel,
            usagePct = result.usagePct,
            remainingDays = result.remainingDays,
        )
    }

    // ── Private helpers ──

    /** 날짜 범위 파라미터를 파싱·검증하여 (fromDate, toDate) 쌍을 반환한다. */
    private fun resolveCostDateRange(from: String?, to: String?): Pair<LocalDate, LocalDate> {
        val range = resolveDateRange(from, to, defaultDays = 30, maxDays = 365)
        return range.from to range.to
    }
}
