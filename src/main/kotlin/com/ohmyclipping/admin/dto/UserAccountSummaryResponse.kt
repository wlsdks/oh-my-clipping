package com.ohmyclipping.admin.dto

/** 사용자 계정 요약 응답 DTO. 카운트만 반환하여 전체 유저 로드를 방지한다. */
data class UserAccountSummaryResponse(
    val pendingCount: Int,
    val rejectedCount: Int,
    val weeklyProcessedCount: Int
)
