package com.ohmyclipping.store.analytics.dto

import java.time.LocalDate

/** weekly_persona_subscription_state 테이블 매핑 DTO. 카테고리(구독) 단위 주간 상태. */
data class WeeklySubscriptionState(
    val weekStart: LocalDate,
    val personaId: String,
    val categoryId: String,
    val state: String,  // ACTIVE/NEW/CHURNED
    val deliveryOpportunities: Int,
    val deliveredCount: Int,
    val clicksInWeek: Int,
    val bookmarksInWeek: Int
)
