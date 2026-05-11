package com.clipping.mcpserver.service.port

import com.clipping.mcpserver.service.port.NotificationSeverity
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 파이프라인 실행 운영 알림에 필요한 최소 데이터.
 */
data class PipelineRunOpsEvent(
    val id: String,
    val categoryId: String,
    val categoryName: String?,
    val status: String,
    val totalCollected: Int?,
    val totalSummarized: Int?,
    val totalDigestSelected: Int?,
    val endedAt: Instant?,
)

/**
 * 파이프라인 단계 실패 운영 알림에 필요한 최소 데이터.
 */
data class PipelineStepTraceOpsEvent(
    val step: String,
    val status: String,
    val detail: String?,
)

/**
 * 동일 시간 슬롯 내 여러 카테고리 실패를 하나의 Slack 스레드로 묶기 위해
 * 메모리에서 관리하는 가변 상태 객체다.
 */
data class IncidentWindowState(
    val windowKey: Long,
    val categories: MutableSet<String> = ConcurrentHashMap.newKeySet(),
    var parentTs: String? = null,
    var payloadJson: String? = null,
    var updateCount: Int = 0,
    val failedRuns: MutableList<String> = mutableListOf(),
)

/**
 * 파이프라인/다이제스트 실패 정보.
 */
data class DigestFailure(
    val categoryId: String,
    val categoryName: String,
    val targetLabel: String,
    val errorMessage: String,
    val failedAt: Instant,
)

/**
 * 시간대별 배치 처리 요약.
 */
data class HourlyBatchSummary(
    val windowStart: Instant,
    val windowEnd: Instant,
    val completedCount: Int,
    val failedCount: Int,
)

/**
 * 장애 위험 소스 요약.
 */
data class RiskSourceSummary(
    val sourceId: String,
    val sourceName: String,
    val failureCount: Int,
)

/**
 * 일별 운영 예측 리포트.
 */
data class DailyForecast(
    val forecastDate: LocalDate,
    val expectedRunCount: Int,
    val expectedDigestCount: Int,
    val llmMonthlyUsageKrw: Long,
    val llmMonthlyBudgetKrw: Long,
    val llmProjectedMonthEndKrw: Long,
    val riskSources: List<RiskSourceSummary>,
    val severity: NotificationSeverity,
)

/**
 * 카테고리별 클릭 하락 정보.
 */
data class CategoryClickDrop(
    val categoryId: String,
    val categoryName: String,
    val deltaPct: Double,
)

/**
 * 주간 액션 리포트.
 */
data class WeeklyActionReport(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val topFailingSources: List<RiskSourceSummary>,
    val latencyMedianMsCurrent: Long,
    val latencyMedianMsPrevious: Long,
    val llmWeeklyUsageKrw: Long,
    val llmWeeklyBudgetKrw: Long,
    val keywordVolatilityCount: Int,
    val clickDeclineCategories: List<CategoryClickDrop>? = null,
)
