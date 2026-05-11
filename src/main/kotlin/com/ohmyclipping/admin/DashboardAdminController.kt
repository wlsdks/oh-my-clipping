package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.ActiveSubscriptionsSummaryDto
import com.ohmyclipping.admin.dto.ForecastDto
import com.ohmyclipping.admin.dto.UserEngagementTrendDto
import com.ohmyclipping.service.DashboardService
import com.ohmyclipping.service.dto.OpsSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 홈 대시보드 관리자 컨트롤러.
 * Tier 1 집계 지표(운영 예측·사용자 참여·구독 요약)를 노출한다.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
class DashboardAdminController(
    private val dashboardService: DashboardService,
) {

    /**
     * 오늘 운영 예측을 반환한다.
     * 예상 파이프라인 실행 횟수, 다이제스트 발송 건수, 다음 크론 실행 시각을 포함한다.
     */
    @GetMapping("/forecast")
    fun forecast(): ForecastDto {
        val result = dashboardService.todayForecast()
        return ForecastDto(
            expectedRunCount = result.expectedRunCount,
            expectedDigestCount = result.expectedDigestCount,
            nextRunAtKst = result.nextRunAtKst,
        )
    }

    /**
     * 최근 7일 사용자 참여 트렌드를 반환한다.
     * 어제 클릭률, 7일 평균·표준편차, 어제 긍정·부정 피드백 수를 포함한다.
     */
    @GetMapping("/user-engagement-trend")
    fun userEngagementTrend(): UserEngagementTrendDto {
        val result = dashboardService.engagementTrend()
        return UserEngagementTrendDto(
            yesterdayClickRate = result.yesterdayClickRate,
            sevenDayAvgClickRate = result.sevenDayAvgClickRate,
            sevenDayStdDev = result.sevenDayStdDev,
            feedbackPositiveYesterday = result.feedbackPositiveYesterday,
            feedbackNegativeYesterday = result.feedbackNegativeYesterday,
        )
    }

    /**
     * 홈 대시보드용 운영 지표 요약.
     * 오늘 KST 기준 delivery + pipeline 을 상태별 카운트로 집계해 단일 응답으로 반환한다.
     * 프론트의 in-memory 필터링 의존을 제거하기 위한 서버 pre-aggregation 엔드포인트.
     */
    @GetMapping("/ops-summary")
    fun getOpsSummary(): OpsSummary = dashboardService.getOpsSummary()

    /**
     * 활성 구독 요약을 반환한다.
     * 현재 총 활성 구독 수와 이번 주 신규·비활성화·순 변화량을 포함한다.
     */
    @GetMapping("/active-subscriptions-summary")
    fun activeSubscriptionsSummary(): ActiveSubscriptionsSummaryDto {
        val result = dashboardService.activeSubscriptionsSummary()
        return ActiveSubscriptionsSummaryDto(
            activeCount = result.activeCount,
            newThisWeek = result.newThisWeek,
            deactivatedThisWeek = result.deactivatedThisWeek,
            netChange = result.netChange,
        )
    }
}
