package com.ohmyclipping.service.dto

/**
 * AI 정확도 추적을 위한 검토 통계 응답 DTO.
 */
data class ReviewStatsResponse(
    val period: String,
    val totalReviewed: Int,
    val overallAccuracy: Double,
    val includeAccuracy: Double,
    val excludeAccuracy: Double,
    val overriddenCount: Int,
    val previousPeriodAccuracy: Double?,
    val categoryBreakdown: List<CategoryAccuracy>
)

/**
 * 카테고리별 AI 정확도 집계 DTO.
 */
data class CategoryAccuracy(
    val categoryId: String,
    val categoryName: String,
    val totalReviewed: Int,
    val accuracy: Double,
    val includeAccuracy: Double,
    val excludeAccuracy: Double
)
