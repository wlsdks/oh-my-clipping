package com.clipping.mcpserver.service.dto

/**
 * 오늘 운영 예측 도메인 모델.
 * DashboardService.todayForecast()의 반환 타입.
 */
data class TodayForecastResult(
    val expectedRunCount: Int,
    val expectedDigestCount: Int,
    val nextRunAtKst: String,
)

/**
 * 사용자 참여 트렌드 도메인 모델.
 * DashboardService.engagementTrend()의 반환 타입.
 */
data class EngagementTrendResult(
    val yesterdayClickRate: Double,
    val sevenDayAvgClickRate: Double,
    val sevenDayStdDev: Double,
    val feedbackPositiveYesterday: Long,
    val feedbackNegativeYesterday: Long,
)

/**
 * 활성 구독 요약 도메인 모델.
 * DashboardService.activeSubscriptionsSummary()의 반환 타입.
 */
data class ActiveSubscriptionsSummaryResult(
    val activeCount: Long,
    val newThisWeek: Long,
    val deactivatedThisWeek: Long,
    val netChange: Long,
)

/**
 * 오늘 KST 기준 발송 운영 지표 요약.
 * delivery_log 를 status 별로 집계한 결과를 홈 대시보드에 노출한다.
 */
data class DeliveryOpsSummary(
    val total: Long,
    val sent: Long,
    val failed: Long,
)

/**
 * 오늘 KST 기준 파이프라인 운영 지표 요약.
 * pipeline_runs.started_at >= TODAY_KST_MIDNIGHT 을 status 별로 집계한 결과.
 */
data class PipelineOpsSummary(
    val total: Long,
    val success: Long,
    val failed: Long,
)

/**
 * 운영 지표 요약 — 오늘 KST 기준 발송 + 파이프라인 상태별 카운트.
 * 홈 대시보드에서 단일 GET 으로 받아 in-memory 필터 없이 숫자를 그대로 표시한다.
 */
data class OpsSummary(
    val delivery: DeliveryOpsSummary,
    val pipeline: PipelineOpsSummary,
)
