package com.clipping.mcpserver.admin.dto

/**
 * 사용자 포털 월간 통계 행 응답 DTO.
 */
data class UserMonthlyStatResponse(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val statDate: String,
    val itemsCollected: Int,
    val itemsSummarized: Int,
    val itemsSent: Int,
    val topKeywords: List<String>,
    val avgImportanceScore: Float
)
