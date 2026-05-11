package com.ohmyclipping.user.dto

/** 사용자 본인 계정 탈퇴 요청. */
data class SelfWithdrawRequest(val password: String)

/** 사용자 본인 Slack 멤버 ID 변경 요청. */
data class UpdateSlackRequest(val slackMemberId: String)

/** 사용자 본인 Slack 멤버 ID 변경 응답. */
data class UpdateSlackResponse(val slackMemberId: String)

/** 사용자 본인 비밀번호 변경 요청. */
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

/**
 * 사용자 본인 프로필(부서/팀 FK) 수정 요청.
 * null 인 필드는 변경하지 않고 유지하며, 빈 문자열은 해당 필드를 초기화한다.
 */
data class UpdateProfileRequest(
    val departmentId: String? = null,
    val teamId: String? = null
)

/** 사용자 본인 프로필 수정 응답. FK id와 JOIN 된 이름을 함께 반환한다. */
data class UpdateProfileResponse(
    val departmentId: String?,
    val departmentName: String?,
    val teamId: String?,
    val teamName: String?
)
