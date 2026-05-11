package com.ohmyclipping.admin.dto

/**
 * 통계 조회 응답 DTO.
 */
data class StatResponse(
    val id: String,
    val categoryId: String,
    val statDate: String,
    val itemsCollected: Int,
    val itemsDuplicates: Int,
    val itemsSummarized: Int,
    val itemsSent: Int,
    val slackSendAttempts: Int,
    val slackSendSuccesses: Int,
    val topKeywords: List<String>,
    val avgImportanceScore: Float
)

data class DailyOperationalKpiResponse(
    val statDate: String,
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
