package com.ohmyclipping.admin.dto

/**
 * 현재 로그인 사용자 정보 응답 DTO.
 */
data class MeResponse(
    val id: String,
    val username: String,
    val displayName: String?,
    val role: String,
    val approvalStatus: String,
    val hasSlackDm: Boolean,
    val mustChangePassword: Boolean,
    val department: String? = null,
    val team: String? = null,
)
