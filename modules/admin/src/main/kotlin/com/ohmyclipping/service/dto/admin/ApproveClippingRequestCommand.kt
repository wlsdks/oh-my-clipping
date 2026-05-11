package com.ohmyclipping.service.dto.admin

/**
 * 사용자 요청 승인 시 어드민이 결정한 법적 검토 정보.
 * 컨트롤러 DTO에서 매핑되어 서비스로 전달된다.
 * 서비스 레이어 경계 규칙에 따라 service/dto/에 위치한다.
 */
data class ApproveClippingRequestCommand(
    val legalBasis: String,
    val summaryAllowed: Boolean,
    val fulltextAllowed: Boolean,
    val reviewNotes: String?,
    /** 관리자가 채널을 재지정할 경우 사용. null이면 신청자가 선택한 채널을 사용한다. */
    val overrideSlackChannelId: String? = null,
)
