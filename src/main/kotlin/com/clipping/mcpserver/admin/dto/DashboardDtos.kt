package com.clipping.mcpserver.admin.dto

/**
 * 오늘 운영 예측 응답 DTO.
 * 홈 대시보드 Tier 1 "오늘 예상 실행/발송" 카드에 사용한다.
 */
data class ForecastDto(
    /** 오늘 예상 파이프라인 실행 횟수 */
    val expectedRunCount: Int,
    /** 오늘 예상 다이제스트 발송 건수 */
    val expectedDigestCount: Int,
    /** 다음 크론 실행 시각 (ISO-8601 KST offset, e.g. "2026-04-18T10:00:00+09:00") */
    val nextRunAtKst: String,
)

/**
 * 사용자 참여 트렌드 응답 DTO.
 * 홈 대시보드 Tier 1 "사용자 참여" 카드에 사용한다.
 */
data class UserEngagementTrendDto(
    /** 어제 클릭률 (%) */
    val yesterdayClickRate: Double,
    /** 최근 7일 평균 클릭률 (%) */
    val sevenDayAvgClickRate: Double,
    /** 최근 7일 클릭률 표준편차 */
    val sevenDayStdDev: Double,
    /** 어제 긍정 피드백 수 */
    val feedbackPositiveYesterday: Long,
    /** 어제 부정 피드백 수 */
    val feedbackNegativeYesterday: Long,
)

/**
 * 활성 구독 요약 응답 DTO.
 * 홈 대시보드 Tier 1 "구독 트렌드" 카드에 사용한다.
 */
data class ActiveSubscriptionsSummaryDto(
    /** 현재 총 활성 구독 수 */
    val activeCount: Long,
    /** 이번 주 신규 구독 수 */
    val newThisWeek: Long,
    /** 이번 주 비활성화된 구독 수 */
    val deactivatedThisWeek: Long,
    /** 순 변화량 (newThisWeek - deactivatedThisWeek) */
    val netChange: Long,
)

/**
 * 현재 예산 알림 상태 응답 DTO.
 * 홈 대시보드 Tier 1 예산 뱃지에 사용한다.
 */
data class CostAlertCurrentDto(
    /** 알림 대상 월 (YYYY-MM 형식) */
    val monthId: String,
    /** 현재 임계값 레벨 ("CRITICAL_90", "CRITICAL_100") 또는 null */
    val currentLevel: String?,
    /** 현재 월 예산 사용률 (0~100+) */
    val usagePct: Int,
    /** 월말까지 남은 일수 */
    val remainingDays: Int,
)
