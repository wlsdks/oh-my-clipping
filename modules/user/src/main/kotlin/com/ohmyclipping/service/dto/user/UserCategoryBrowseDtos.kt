package com.ohmyclipping.service.dto.user

/**
 * 사용자가 탐색 가능한 공개 카테고리 정보.
 */
data class UserCategoryBrowseItem(
    val id: String,
    val name: String,
    val description: String?,
    val slackChannelId: String?,
    val subscriberCount: Int,
    val isSubscribed: Boolean,
    val deliveryHour: Int?,
    val maxItems: Int
)

/**
 * 공개 카테고리 즉시 구독 결과.
 */
data class UserCategorySubscribeResult(
    val requestId: String,
    val categoryId: String,
    val status: String
)
