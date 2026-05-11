package com.clipping.mcpserver.service.dto

import java.time.Instant

/**
 * 사용자 승인 구독의 즉시 반영 설정 조회 모델입니다.
 */
data class UserSubscriptionPreferenceView(
    val requestId: String,
    val categoryId: String,
    val requestName: String,
    val isActive: Boolean,
    val maxItems: Int,
    val excludeKeywords: List<String>,
    val includeThreshold: Double,
    val deliveryDays: List<String>?,
    val deliveryHour: Int?,
    val deliveryPreset: String?,
    val updatedAt: Instant
)

/**
 * 사용자 승인 구독의 즉시 반영 설정 수정 명령입니다.
 */
data class UpdateUserSubscriptionPreferenceCommand(
    val isActive: Boolean? = null,
    val maxItems: Int? = null,
    val excludeKeywords: List<String>? = null,
    val includeThreshold: Double? = null,
    val deliveryDays: List<String>? = null,
    val deliveryHour: Int? = null,
    val deliveryPreset: String? = null
)
