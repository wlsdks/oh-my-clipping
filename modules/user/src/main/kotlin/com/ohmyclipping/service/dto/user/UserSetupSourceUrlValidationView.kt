package com.ohmyclipping.service.dto.user

/**
 * 사용자 setup 단계에서 RSS URL 또는 Google News RSS 연결 가능 여부를 반환한다.
 */
data class UserSetupSourceUrlValidationView(
    val valid: Boolean,
    val status: String? = null,
    val reason: String
)
