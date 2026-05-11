package com.clipping.mcpserver.store

/**
 * 사용자 구독 요청 통계 조회 결과.
 * 대량 요청 목록을 서비스 메모리로 올리지 않고 DB 집계 결과만 전달한다.
 */
data class UserClippingRequestStatsSnapshot(
    val pendingCount: Int,
    val approvedCount: Int,
    val rejectedCount: Int,
    val totalCount: Int,
    val avgApprovalHours: Double?,
    val topTopics: List<UserClippingRequestCountRow>,
    val rejectionReasons: List<UserClippingRequestCountRow>,
    val weeklyProcessedCount: Int
)

/**
 * 요청 통계의 이름/건수 쌍.
 * 토픽명과 반려 사유 랭킹에서 공통으로 사용한다.
 */
data class UserClippingRequestCountRow(
    val name: String,
    val count: Int
)
