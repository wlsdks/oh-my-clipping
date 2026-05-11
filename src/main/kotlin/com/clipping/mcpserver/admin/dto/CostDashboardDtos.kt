package com.clipping.mcpserver.admin.dto

// --- Overview ---
data class CostOverviewResponse(
    val from: String, val to: String,
    val totalCostUsd: Double, val totalRequests: Int, val dailyAvgRequests: Double,
    val projectedMonthEndUsd: Double, val previousPeriodCostUsd: Double, val costChangePercent: Double,
    val budgetUsd: Double?, val budgetUsedPercent: Double?,
    val dailyBreakdown: List<DailyCostRow>
)
data class DailyCostRow(
    val date: String, val inputCostUsd: Double, val outputCostUsd: Double,
    val totalCostUsd: Double, val requestCount: Int
)

// --- Hourly Drilldown ---
data class HourlyCostResponse(val date: String, val hours: List<HourlyCostRow>)
data class HourlyCostRow(
    val hour: Int, val inputCostUsd: Double, val outputCostUsd: Double,
    val totalCostUsd: Double, val requestCount: Int
)

// --- Models ---
data class CostModelsResponse(
    val from: String, val to: String, val modelCount: Int,
    val costPerArticleUsd: Double, val previousCostPerArticleUsd: Double,
    val models: List<ModelCostRow>, val promptVersions: List<PromptVersionRow>,
    val categoryBreakdown: List<CategoryCostRow>
)
data class ModelCostRow(
    val model: String, val requestCount: Int,
    val inputCostUsd: Double, val outputCostUsd: Double, val totalCostUsd: Double, val costPercent: Double
)
data class PromptVersionRow(
    val promptVersion: String, val requestCount: Int,
    val avgTokensIn: Long, val avgTokensOut: Long, val costPerArticleUsd: Double, val avgDurationMs: Long
)
data class CategoryCostRow(
    val categoryId: String, val categoryName: String,
    val totalCostUsd: Double, val costPercent: Double, val requestCount: Int
)

// --- Reliability ---
data class CostReliabilityResponse(
    val from: String, val to: String,
    val successRate: Double, val emptyResultRate: Double, val failureRate: Double,
    val avgDurationMs: Long, val p50DurationMs: Long, val p95DurationMs: Long,
    val dailyBreakdown: List<DailyReliabilityRow>, val topErrors: List<ErrorGroupRow>
)
data class DailyReliabilityRow(
    val date: String, val succeeded: Int, val emptyResult: Int, val failed: Int,
    val avgDurationMs: Long, val p50DurationMs: Long, val p95DurationMs: Long
)
data class ErrorGroupRow(
    val errorPattern: String, val count: Int, val lastOccurred: String, val affectedCategories: List<String>
)

// --- Detail ---
data class CostDetailResponse(
    val from: String, val to: String,
    val inputCostPerMillionUsd: Double, val outputCostPerMillionUsd: Double,
    val rows: List<CostDetailRowResponse>
)
data class CostDetailRowResponse(
    val channelId: String, val categoryId: String, val categoryName: String,
    val requestCount: Int, val tokensIn: Long, val tokensOut: Long,
    val estimatedUsd: Double, val costPercent: Double
)

// --- Budget ---
data class BudgetSettingsResponse(
    val monthlyBudgetUsd: Double, val alertThresholdPercent: Int, val slackAlertEnabled: Boolean
)
data class BudgetSettingsRequest(
    val monthlyBudgetUsd: Double, val alertThresholdPercent: Int, val slackAlertEnabled: Boolean
)
