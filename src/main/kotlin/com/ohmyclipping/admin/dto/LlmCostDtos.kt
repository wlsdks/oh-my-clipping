package com.ohmyclipping.admin.dto

data class LlmCostSummaryResponse(
    val from: String,
    val to: String,
    val inputCostPerMillionUsd: Double,
    val outputCostPerMillionUsd: Double,
    val totalRequestCount: Int,
    val totalTokensIn: Long,
    val totalTokensOut: Long,
    val totalEstimatedUsd: Double,
    val rows: List<LlmCostRowResponse>
)

data class LlmCostRowResponse(
    val channelId: String,
    val categoryId: String,
    val categoryName: String,
    val requestCount: Int,
    val tokensIn: Long,
    val tokensOut: Long,
    val estimatedUsd: Double
)
