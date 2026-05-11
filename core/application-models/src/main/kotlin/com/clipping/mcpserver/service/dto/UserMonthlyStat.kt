package com.clipping.mcpserver.service.dto

import java.time.LocalDate

/**
 * 사용자 포털 월간 통계 행 모델.
 */
data class UserMonthlyStat(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val statDate: LocalDate,
    val itemsCollected: Int,
    val itemsSummarized: Int,
    val itemsSent: Int,
    val topKeywords: List<String>,
    val avgImportanceScore: Float
)
