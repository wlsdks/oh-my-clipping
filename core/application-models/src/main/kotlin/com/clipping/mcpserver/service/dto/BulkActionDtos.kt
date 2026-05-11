package com.clipping.mcpserver.service.dto

/**
 * 일괄 처리 결과 통합 DTO.
 * 사용자 요청/계정 벌크 승인·반려 등 모든 벌크 액션에서 동일한 응답 구조를 사용한다.
 * 프론트엔드가 기대하는 `{succeeded, failed}` 형태와 일치한다.
 */
data class BulkActionResponse(
    val succeeded: List<String>,
    val failed: List<BulkActionFailure>
)

/**
 * 벌크 처리 개별 실패 항목.
 * code로 실패 유형(NOT_FOUND, ALREADY_PROCESSED 등)을 구분한다.
 */
data class BulkActionFailure(
    val id: String,
    val reason: String,
    val code: String = "UNKNOWN"
)
