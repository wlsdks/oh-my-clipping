package com.ohmyclipping.admin.dto

/**
 * 사용자 요청 벌크 승인/반려 요청 DTO.
 */
data class BulkReviewRequest(
    val ids: List<String>,
    val reviewNote: String? = null
)

/**
 * 벌크 처리 응답 DTO.
 * 각 항목별 처리 결과를 개별 리포트한다.
 */
data class BulkReviewResponse(
    val results: List<BulkReviewResult>
)

/**
 * 벌크 처리 개별 결과 항목.
 */
data class BulkReviewResult(
    val id: String,
    val status: String,
    val reason: String? = null
)
