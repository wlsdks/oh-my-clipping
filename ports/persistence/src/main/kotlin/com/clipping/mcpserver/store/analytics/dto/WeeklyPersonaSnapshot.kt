package com.clipping.mcpserver.store.analytics.dto

import java.time.Instant
import java.time.LocalDate

/** weekly_persona_snapshot 테이블 매핑 DTO. 페르소나별 주간 집계 결과. */
data class WeeklyPersonaSnapshot(
    val id: String,
    val weekStart: LocalDate,
    val personaId: String,
    val personaName: String,
    val isPreset: Boolean,
    val activeSubs: Int,
    val newSubs: Int,
    val churnedSubs: Int,
    val deliveredCount: Int,
    val deliveredItems: Int,
    val engagedUsers: Int,
    val totalClicks: Int,
    val totalBookmarks: Int,
    val engagementRate: Double,
    val clickPerDelivery: Double,
    val createdAt: Instant
)
