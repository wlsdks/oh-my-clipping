package com.ohmyclipping.service.analytics.dto

/**
 * 주간 글로벌 참여 지표 (전체 카테고리 합산).
 *
 * WeeklyPersonaSnapshotStep 에서 주 1회 조회해 페르소나별 스냅샷 산출에 사용한다.
 * 카테고리별 분리 집계는 Slice 3 에서 보강한다.
 */
data class GlobalEngagement(
    val totalClicks: Long,
    val totalBookmarks: Long,
    val engagedUserCount: Int,
    val totalCategories: Int
)
