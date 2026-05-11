package com.clipping.mcpserver.admin.dto

/**
 * 사용자 계정 승인/반려 요청 DTO.
 */
data class ReviewUserAccountRequest(
    val reviewNote: String? = null
)

/**
 * 사용자 계정 승인 상태 응답 DTO.
 */
data class UserAccountApprovalResponse(
    val id: String,
    val username: String,
    val displayName: String?,
    val department: String?,
    /** V124(Phase 3 PR1): 부서 하위 team. 정규화된 소문자 문자열 또는 null. */
    val team: String? = null,
    val isActive: Boolean,
    val approvalStatus: String,
    val approvalNote: String?,
    val approvedByUserId: String?,
    val approvedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val lastLoginAt: String? = null,
    val subscriptionCount: Int = 0,
    val recentActivitySummary: String? = null,
    val role: String = "USER",
    val approvedByUsername: String? = null
)

/**
 * 사용자 임시 비밀번호 재설정 응답 DTO.
 */
data class ResetPasswordResponse(val tempPassword: String, val message: String)
