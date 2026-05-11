package com.ohmyclipping.admin.dto

/**
 * 사용자가 구독 가능한 카테고리 탐색 응답 DTO.
 */
data class CategoryBrowseItem(
    val id: String,
    val name: String,
    val description: String?,
    val slackChannelId: String?,
    val subscriberCount: Int,
    val isSubscribed: Boolean,
    val deliveryHour: Int?,
    val maxItems: Int,
)

/**
 * 기존 카테고리 즉시 구독 요청 DTO.
 */
data class SubscribeRequest(val slackChannelId: String)

/**
 * 기존 카테고리 즉시 구독 응답 DTO.
 */
data class SubscribeResponse(
    val requestId: String,
    val categoryId: String,
    val status: String,
)
