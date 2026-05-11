package com.clipping.mcpserver.service.dto

import java.time.Instant
import java.time.LocalDate

// --- Overview ---
data class CostOverviewData(
    val from: LocalDate, val to: LocalDate,
    val totalCostUsd: Double, val totalRequests: Int, val dailyAvgRequests: Double,
    val projectedMonthEndUsd: Double, val previousPeriodCostUsd: Double, val costChangePercent: Double,
    val budgetUsd: Double?, val budgetUsedPercent: Double?,
    val dailyBreakdown: List<DailyCostData>
)
data class DailyCostData(
    val date: LocalDate, val inputCostUsd: Double, val outputCostUsd: Double,
    val totalCostUsd: Double, val requestCount: Int
)

// --- Hourly ---
data class HourlyCostData(val date: LocalDate, val hours: List<HourlyCostRowData>)
data class HourlyCostRowData(
    val hour: Int, val inputCostUsd: Double, val outputCostUsd: Double,
    val totalCostUsd: Double, val requestCount: Int
)

// --- Models ---
data class CostModelsData(
    val from: LocalDate, val to: LocalDate, val modelCount: Int,
    val costPerArticleUsd: Double, val previousCostPerArticleUsd: Double,
    val models: List<ModelCostData>, val promptVersions: List<PromptVersionData>,
    val categoryBreakdown: List<CategoryCostData>
)
data class ModelCostData(
    val model: String, val requestCount: Int,
    val inputCostUsd: Double, val outputCostUsd: Double, val totalCostUsd: Double, val costPercent: Double
)
data class PromptVersionData(
    val promptVersion: String, val requestCount: Int,
    val avgTokensIn: Long, val avgTokensOut: Long, val costPerArticleUsd: Double, val avgDurationMs: Long
)
data class CategoryCostData(
    val categoryId: String, val categoryName: String,
    val totalCostUsd: Double, val costPercent: Double, val requestCount: Int
)

// --- Reliability ---
data class CostReliabilityData(
    val from: LocalDate, val to: LocalDate,
    val successRate: Double, val emptyResultRate: Double, val failureRate: Double,
    val avgDurationMs: Long, val p50DurationMs: Long, val p95DurationMs: Long,
    val dailyBreakdown: List<DailyReliabilityData>, val topErrors: List<ErrorGroupData>
)
data class DailyReliabilityData(
    val date: LocalDate, val succeeded: Int, val emptyResult: Int, val failed: Int,
    val avgDurationMs: Long, val p50DurationMs: Long, val p95DurationMs: Long
)
data class ErrorGroupData(
    val errorPattern: String, val count: Int, val lastOccurred: Instant, val affectedCategories: List<String>
)
