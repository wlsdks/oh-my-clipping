package com.ohmyclipping.service.dto.admin

/** 일괄 처리 결과 DTO. */
data class BulkUserAccountReviewResult(
    val succeeded: List<String>,
    val failed: List<BulkUserAccountReviewFailure>
)

/** 일괄 처리 실패 항목. code로 실패 유형을 구분한다. */
data class BulkUserAccountReviewFailure(
    val id: String,
    val reason: String,
    val code: String = "UNKNOWN"
)
