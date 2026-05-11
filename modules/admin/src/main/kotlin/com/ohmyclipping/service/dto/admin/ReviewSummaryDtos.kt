package com.ohmyclipping.service.dto.admin

/**
 * 검토함 카테고리별 요약 응답 DTO.
 */
data class ReviewSummaryResponse(
    val totalCount: Int,
    val reviewCount: Int,
    val includeCount: Int,
    val excludeCount: Int,
    val categories: List<CategorySummary>
)

/**
 * 카테고리 단위 검토함 상태 집계 DTO.
 */
data class CategorySummary(
    val categoryId: String,
    val categoryName: String,
    val totalCount: Int,
    val reviewCount: Int,
    val includeCount: Int,
    val excludeCount: Int,
    val suggestedIncludeCount: Int
)
