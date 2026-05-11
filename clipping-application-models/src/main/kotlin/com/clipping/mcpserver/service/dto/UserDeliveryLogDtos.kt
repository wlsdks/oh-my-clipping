package com.clipping.mcpserver.service.dto

import java.time.Instant
import java.time.LocalDate

/**
 * 사용자 발송 이력 응답 전체 뷰.
 */
data class UserDeliveryLogListView(
    val deliveries: List<UserDeliveryLogItemView>
)

/**
 * 사용자 발송 이력 단건 뷰.
 */
data class UserDeliveryLogItemView(
    val date: LocalDate,
    val categoryName: String,
    val itemCount: Int,
    val status: String,
    val deliveredAt: Instant?
)
