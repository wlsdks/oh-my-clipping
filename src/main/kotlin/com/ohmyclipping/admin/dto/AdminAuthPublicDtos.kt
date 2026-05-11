package com.ohmyclipping.admin.dto

/**
 * 관리자 회원가입 가능 여부를 조회할 때 사용하는 응답 DTO.
 */
data class SignupAvailabilityResponse(
    val allowed: Boolean,
    val reason: String,
    val message: String
)

/**
 * 아이디 중복 확인 응답 DTO.
 */
data class UsernameAvailabilityResponse(
    val available: Boolean
)

/**
 * 사용자 회원가입 요청 DTO.
 */
data class UserSignupRequest(
    val email: String,
    val displayName: String,
    val departmentId: String?,
    val teamId: String? = null,
    val password: String,
)

/**
 * 사용자 회원가입 응답 DTO.
 */
data class UserSignupResponse(
    val id: String,
    val username: String,
    val message: String,
)
