package com.ohmyclipping.mcp.dto

/**
 * MCP 응답용 사용자 구독 카테고리 뷰.
 *
 * 발송 스케줄 메타(요일/시간)는 카테고리 개별 규칙 → 글로벌 기본 폴백으로 결정된다.
 * 구독별 개별 설정이 없으면 [deliveryDaysSource] 는 "global" 로 표시된다.
 */
data class SubscriptionView(
    val categoryId: String,
    val categoryName: String,
    val categoryDescription: String?,
    val deliveryDays: List<String>,
    val deliveryHour: Int,
    val deliveryDaysSource: String,
)
