package com.ohmyclipping.service.dto.admin

import java.time.Instant

/**
 * 관리자 클리핑 설정 조회/수정 결과.
 * 서비스 내부 중첩 DTO를 피하고 controller가 응답 DTO로 변환할 수 있도록 별도 타입으로 둔다.
 */
data class ClippingSetting(
    val categoryId: String,
    val categoryName: String,
    val categoryUpdatedAt: Instant,
    val isActive: Boolean,
    val slackChannelId: String?,
    val maxItems: Int,
    val retentionKeepDays: Int,
    val retentionEnabled: Boolean,
    val retentionSource: String
)
