package com.clipping.mcpserver.admin.dto

/**
 * 검토함 단건 응답 DTO.
 */
data class ReviewItemResponse(
    val summaryId: String,
    val categoryId: String,
    val categoryName: String,
    val title: String,
    val summary: String,
    val sourceLink: String,
    val keywords: List<String>,
    val importanceScore: Float,
    val suggestedStatus: String,
    val currentStatus: String,
    val statusReason: String,
    val reviewedBy: String?,
    val reviewedAt: String?,
    val priorityScore: Int,
    val priorityLabel: String,
    val createdAt: String
)

/**
 * 검토함 이력 응답 DTO.
 */
data class ReviewItemAuditResponse(
    val id: String,
    val summaryId: String,
    val categoryId: String,
    val fromStatus: String?,
    val toStatus: String,
    val reason: String?,
    val reviewedBy: String?,
    val reviewedAt: String?,
    val createdAt: String
)

/**
 * 검토함 액션 요청 DTO.
 */
data class ReviewItemActionRequest(
    val reason: String? = null,
    val reviewedBy: String? = null
)
