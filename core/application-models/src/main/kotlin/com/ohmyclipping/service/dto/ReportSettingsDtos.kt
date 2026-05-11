package com.ohmyclipping.service.dto

/**
 * 자동 리포트 설정 응답 DTO.
 */
data class ReportSettingsResponse(
    val weeklyEnabled: Boolean,
    val weeklyDay: String,
    val weeklyHour: Int,
    val weeklySlackChannelId: String?,
    val weeklyIncludeKeywordTrend: Boolean,
    val weeklyIncludeCompetitor: Boolean,
    val weeklyIncludeTopArticles: Boolean,
    val weeklyIncludeSentiment: Boolean,
    val monthlyEnabled: Boolean,
    val monthlyHour: Int,
    val monthlySlackChannelId: String?
)

/**
 * 자동 리포트 설정 수정 요청 DTO.
 * null인 필드는 기존 값을 유지한다.
 */
data class ReportSettingsUpdateRequest(
    val weeklyEnabled: Boolean? = null,
    val weeklyDay: String? = null,
    val weeklyHour: Int? = null,
    val weeklySlackChannelId: String? = null,
    val weeklyIncludeKeywordTrend: Boolean? = null,
    val weeklyIncludeCompetitor: Boolean? = null,
    val weeklyIncludeTopArticles: Boolean? = null,
    val weeklyIncludeSentiment: Boolean? = null,
    val monthlyEnabled: Boolean? = null,
    val monthlyHour: Int? = null,
    val monthlySlackChannelId: String? = null
)
