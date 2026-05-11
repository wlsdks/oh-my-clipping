package com.ohmyclipping.service.dto.user

import com.ohmyclipping.service.dto.clipping.SummaryInfo

/**
 * 사용자 구독 메타 조회 결과.
 * MCP/REST 어댑터가 같은 정책 결과를 재사용할 수 있도록 service 계층 DTO로 둔다.
 */
data class UserSubscriptionQueryItem(
    val categoryId: String,
    val categoryName: String,
    val categoryDescription: String?,
    val deliveryDays: List<String>,
    val deliveryHour: Int,
    val deliveryDaysSource: String,
)

/**
 * 사용자 브리핑 섹션 조회 결과.
 * 요약 표시 형식은 inbound adapter가 결정하도록 원본 SummaryInfo를 유지한다.
 */
data class UserBriefingSectionResult(
    val categoryId: String,
    val categoryName: String,
    val summaries: List<SummaryInfo>,
)

/** 사용자 브리핑 조회 결과. */
data class UserBriefingResult(
    val sinceDays: Int,
    val perCategoryLimit: Int,
    val sections: List<UserBriefingSectionResult>,
    val emptyNote: String?,
)
