package com.ohmyclipping.admin.dto

/**
 * 사용자 요청 승인 API 요청 본문.
 * 어드민이 명시적으로 선택한 법적 근거와 정책을 포함한다.
 * 검증은 서비스 레이어와 컨트롤러 진입점에서 명시적으로 수행한다.
 */
data class ApproveClippingRequestRequest(
    val legalBasis: String,
    val summaryAllowed: Boolean,
    val fulltextAllowed: Boolean,
    val reviewNotes: String? = null,
    val responsibilityAcknowledged: Boolean = false,
    /** 관리자가 승인 시점에 채널을 재지정할 경우 사용. null이면 신청자가 선택한 채널을 사용한다. */
    val overrideSlackChannelId: String? = null,
)

/**
 * 벌크 승인 요청 본문.
 * 일괄 적용할 법적 검토 정보를 포함한다.
 */
data class BulkApproveClippingRequestRequest(
    val ids: List<String>,
    val legalBasis: String,
    val summaryAllowed: Boolean,
    val fulltextAllowed: Boolean,
    val reviewNotes: String? = null,
    val responsibilityAcknowledged: Boolean = false,
)
