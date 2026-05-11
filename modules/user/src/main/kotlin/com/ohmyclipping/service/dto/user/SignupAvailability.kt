package com.ohmyclipping.service.dto.user

/**
 * 역할별 회원가입 가능 여부와 사유 코드.
 */
data class SignupAvailability(
    val allowed: Boolean,
    val reason: String
)
