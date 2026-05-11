package com.ohmyclipping.service.analytics.dto

/**
 * 커스텀 페르소나 요약 (Analytics 페이지 우측 패널 + StyleStatsTab 축소판 공용).
 *
 * `newThisWeek` 는 Slice 2 시계열 도입 전까지 0 이며, Slice 2 부터
 * 주간 스냅샷 기반으로 채워진다.
 */
data class CustomSummary(
    val totalCustomPersonas: Int,
    val activeCustomSubscriptions: Int,
    val newThisWeek: Int,
    val recentPersonas: List<RecentCustomPersona>
)
