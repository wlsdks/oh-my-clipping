package com.ohmyclipping.service.dto

/**
 * 위자드 요청 승인 결과.
 *
 * 생성된 카테고리 ID 를 노출하여 호출자(통합 테스트 포함)가
 * rule / org / source 생성 결과를 검증할 수 있게 한다.
 */
data class ApprovalResult(
    val requestId: String,
    val createdCategoryId: String
)
