package com.ohmyclipping.service.dto.user

/**
 * 사용자 요청 분석 통계 응답 DTO.
 * 요청 대기/승인/반려 현황과 상위 토픽 정보를 포함한다.
 */
data class UserRequestStatsResponse(
    val pendingCount: Int,
    val approvedCount: Int,
    val rejectedCount: Int,
    val totalCount: Int,
    val avgApprovalHours: Double?,
    val topTopics: List<TopicRankItem>,
    val rejectionReasons: List<RejectionReasonItem>,
    val weeklyProcessedCount: Int
)

/**
 * 가장 많이 요청된 토픽 순위 항목.
 */
data class TopicRankItem(
    val requestName: String,
    val count: Int
)

/**
 * 반려 사유 분포 항목.
 */
data class RejectionReasonItem(
    val reason: String,
    val count: Int
)
