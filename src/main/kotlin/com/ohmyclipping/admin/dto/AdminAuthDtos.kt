package com.ohmyclipping.admin.dto

/**
 * 관리자 회원가입 폼 바인딩용 DTO.
 *
 * V129 이후 [departmentId] / [teamId] 를 FK 로 수용한다. 레거시 `department` 문자열 필드는
 * 기존 form-based redirect 경로 호환을 위해 한시적으로 유지했으나 사용되지 않는다.
 */
data class SignupForm(
    val username: String = "",
    val displayName: String? = null,
    val departmentId: String? = null,
    val teamId: String? = null,
    val slackDmChannelId: String? = null,
    val password: String = "",
    val confirmPassword: String = ""
)
