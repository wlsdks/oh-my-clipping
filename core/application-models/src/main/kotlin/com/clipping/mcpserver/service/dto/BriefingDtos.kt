package com.clipping.mcpserver.service.dto

/**
 * 오늘의 브리핑 조회 응답 DTO.
 */
data class BriefingListResponse(
    val briefings: List<BriefingItem>
)

data class BriefingItem(
    val categoryId: String,
    val categoryName: String,
    val summaryDate: String,
    val title: String,
    val overallSummary: String?,
    val topicKeywords: List<String>,
    val totalItems: Int
)
