package com.ohmyclipping.admin.dto

/**
 * 사용자 구독의 즉시 반영 설정 응답 DTO입니다.
 */
data class UserSubscriptionPreferenceResponse(
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
    val updatedAt: String
)

/**
 * 사용자 구독의 즉시 반영 설정 수정 요청 DTO입니다.
 */
data class UpdateUserSubscriptionPreferenceRequest(
    val isActive: Boolean? = null,
    val maxItems: Int? = null,
    val excludeKeywords: List<String>? = null,
    val includeThreshold: Double? = null,
    val deliveryDays: List<String>? = null,
    val deliveryHour: Int? = null,
    val deliveryPreset: String? = null
)
