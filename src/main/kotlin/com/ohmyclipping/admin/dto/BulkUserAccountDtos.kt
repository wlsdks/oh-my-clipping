package com.ohmyclipping.admin.dto

/** 일괄 승인/반려 요청 DTO. */
data class BulkReviewUserAccountRequest(
    val ids: List<String>,
    val reviewNote: String? = null
)
