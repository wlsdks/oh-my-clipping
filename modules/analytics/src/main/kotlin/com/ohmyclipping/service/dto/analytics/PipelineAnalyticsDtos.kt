package com.ohmyclipping.service.dto.analytics

/**
 * 파이프라인 분석 대시보드용 응답 DTO.
 * 수집 -> 요약 -> 발송 전체 흐름의 일일 집계와 사용자별 발송 매트릭스를 제공한다.
 */

/** 오늘 파이프라인 전체 요약: 수집/중복/요약/거절/실패/발송/비용. */
data class PipelineSummaryResponse(
    val todayCollected: Int,
    val todayDuplicateSkipped: Int,
    val todaySummarized: Int,
    val todayRejected: Int,
    val todayFailed: Int,
    val todayDeliverySent: Int,
    val todayDeliverySkipped: Int,
    val todayDeliveryFailed: Int,
    val todayCostUsd: Double,
    val monthlyBudgetUsagePercent: Double
)

/** 기간별 일간 파이프라인 추이 + 거절 사유 집계. */
data class PipelineDailyResponse(
    val days: List<PipelineDailyRow>,
    val periodSummary: PipelinePeriodSummary
)

/** 일간 파이프라인 수치 한 행. */
data class PipelineDailyRow(
    val date: String,
    val collected: Int,
    val duplicateSkipped: Int,
    val summarizeSucceeded: Int,
    val summarizeRejected: Int,
    val summarizeFailed: Int,
    val deliverySent: Int,
    val deliverySkipped: Int,
    val deliveryFailed: Int
)

/** 기간 전체 요약: 거절 사유 분포. */
data class PipelinePeriodSummary(
    val rejectReasons: Map<String, Int>
)

/** 사용자-카테고리별 발송 매트릭스 응답. */
data class DeliveryMatrixResponse(
    val users: List<DeliveryMatrixUser>
)

/** 매트릭스 내 사용자 한 명. */
data class DeliveryMatrixUser(
    val userId: String,
    val username: String,
    val categories: List<DeliveryMatrixCategory>
)

/** 매트릭스 내 카테고리 한 건의 발송 통계. */
data class DeliveryMatrixCategory(
    val categoryId: String,
    val categoryName: String,
    val sent: Int,
    val skipped: Int,
    val failed: Int
)
