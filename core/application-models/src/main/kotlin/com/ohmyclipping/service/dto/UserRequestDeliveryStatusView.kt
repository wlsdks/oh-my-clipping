package com.ohmyclipping.service.dto

/**
 * 사용자 요청의 실제 전달 가능 상태를 표현한다.
 *
 * 요청 승인(APPROVED) 여부와 별개로,
 * 현재 뉴스 수집이 가능한지와 소스 연결 확인 진행 여부를 함께 담는다.
 */
data class UserRequestDeliveryStatusView(
    val deliveryState: String,
    val collectingReady: Boolean,
    val totalSourceCount: Int,
    val readySourceCount: Int,
    val representativeSourceVerificationStatus: String? = null
)
