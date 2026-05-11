package com.ohmyclipping.service

import com.ohmyclipping.service.dto.ActiveSubscriptionsSummaryResult
import com.ohmyclipping.service.dto.DeliveryOpsSummary
import com.ohmyclipping.service.dto.EngagementTrendResult
import com.ohmyclipping.service.dto.OpsSummary
import com.ohmyclipping.service.dto.PipelineOpsSummary
import com.ohmyclipping.service.dto.TodayForecastResult
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.DeliveryLogStore
import com.ohmyclipping.store.PersonaStore
import com.ohmyclipping.store.PipelineRunStore
import com.ohmyclipping.store.UserEventStore
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

/**
 * 홈 대시보드 집계 서비스.
 * 운영 예측·사용자 참여 트렌드·구독 요약 3가지 지표를 조합하여 반환한다.
 */
@Service
class DashboardService(
    private val dailyOpsForecastScheduler: DailyOpsForecastScheduler,
    private val autoCollectionScheduler: AutoCollectionScheduler,
    private val analyticsService: AnalyticsService,
    private val userEventStore: UserEventStore,
    private val categoryStore: CategoryStore,
    private val personaStore: PersonaStore,
    private val deliveryLogStore: DeliveryLogStore,
    private val pipelineRunStore: PipelineRunStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
    private val isoOffsetFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /**
     * 오늘 운영 예측 데이터를 반환한다.
     * DailyOpsForecastScheduler.buildForecast()를 재사용하고,
     * nextRunAtKst는 AutoCollectionScheduler의 크론 표현식으로 계산한다.
     */
    fun todayForecast(): TodayForecastResult {
        val now = clock.instant()
        val todayKst = now.atZone(seoul).toLocalDate()

        // 운영 예측 데이터를 조립한다 (기존 스케줄러 로직 재사용)
        val forecast = dailyOpsForecastScheduler.buildForecast(todayKst, now)

        // AutoCollectionScheduler의 크론 표현식에서 다음 실행 시각을 계산한다
        val cronExpr = CronExpression.parse(autoCollectionScheduler.cronExpression())
        val nowKst = ZonedDateTime.ofInstant(now, seoul)
        val nextRun = cronExpr.next(nowKst)
        val nextRunAtKst = if (nextRun != null) {
            isoOffsetFormatter.format(nextRun)
        } else {
            // 오늘 자정 이후 다음 날 첫 번째 실행을 계산한다
            val tomorrowStart = todayKst.plusDays(1).atStartOfDay(seoul)
            isoOffsetFormatter.format(cronExpr.next(tomorrowStart) ?: tomorrowStart)
        }

        return TodayForecastResult(
            expectedRunCount = forecast.expectedRunCount,
            expectedDigestCount = forecast.expectedDigestCount,
            nextRunAtKst = nextRunAtKst,
        )
    }

    /**
     * 최근 7일 사용자 참여 트렌드를 반환한다.
     * 어제부터 7일 전까지의 일별 클릭률을 집계하여 평균·표준편차를 계산한다.
     */
    fun engagementTrend(): EngagementTrendResult {
        val yesterday = LocalDate.now(clock).minusDays(1)

        // 어제(day 0)부터 7일 전(day 6)까지 일별 클릭률을 수집한다
        val days = (0L until 7L).map { offset -> yesterday.minusDays(offset) }
        val rateByDay = analyticsService.getClickRatesForDays(days)
        val rates = days.map { day -> rateByDay[day] ?: 0.0 }

        val yesterdayClickRate = rates[0]
        val avg = rates.average()

        // 표본 표준편차를 계산한다 (n < 2이면 0.0 반환)
        val stdDev = if (rates.size < 2) 0.0 else {
            val variance = rates.sumOf { (it - avg) * (it - avg) } / (rates.size - 1)
            sqrt(variance)
        }

        // 어제 피드백 이벤트 수를 집계한다
        val feedback = userEventStore.countFeedbackByDay(yesterday)

        return EngagementTrendResult(
            yesterdayClickRate = yesterdayClickRate,
            sevenDayAvgClickRate = avg,
            sevenDayStdDev = stdDev,
            feedbackPositiveYesterday = feedback.positive,
            feedbackNegativeYesterday = feedback.negative,
        )
    }

    /**
     * 활성 구독 요약 데이터를 반환한다.
     * 현재 활성 구독 수와 이번 주(7일) 신규·비활성화 건수를 집계한다.
     */
    fun activeSubscriptionsSummary(): ActiveSubscriptionsSummaryResult {
        val now = clock.instant()
        val sevenDaysAgo = now.minus(7, ChronoUnit.DAYS)

        // 현재 총 활성 구독 수를 조회한다
        val activeCount = personaStore.countTotalActiveSubscriptions()

        // 이번 주 신규·비활성화 카테고리 수를 조회한다
        val newThisWeek = categoryStore.countNewSince(sevenDaysAgo)
        val deactivatedThisWeek = categoryStore.countDeactivatedSince(sevenDaysAgo)

        return ActiveSubscriptionsSummaryResult(
            activeCount = activeCount,
            newThisWeek = newThisWeek,
            deactivatedThisWeek = deactivatedThisWeek,
            netChange = newThisWeek - deactivatedThisWeek,
        )
    }

    /**
     * 오늘 KST 기준 운영 지표 요약을 반환한다.
     * delivery_log 는 delivery_date = TODAY_KST, pipeline_runs 는 started_at >= TODAY_KST_MIDNIGHT 으로
     * 동일한 KST 달력 기준을 유지한다.
     */
    fun getOpsSummary(): OpsSummary {
        // 오늘 KST 날짜와 KST 자정 Instant 를 계산한다. 타임존 정책은 서비스가 소유한다.
        val todayKst = clock.instant().atZone(seoul).toLocalDate()
        val todayKstMidnight = todayKst.atStartOfDay(seoul).toInstant()

        // pipeline_run.started_at >= TODAY_KST_MIDNIGHT
        // 오늘 시작한 run 만 카운트한다. 자정 직전에 시작돼 오늘까지 이어진 RUNNING 은 포함되지 않는다.
        val deliveryCounts = deliveryLogStore.countByStatusOn(todayKst)
        val pipelineCounts = pipelineRunStore.countByStatusSince(todayKstMidnight)

        // 상태별 Map 을 합성해 total 은 values.sum() 으로 계산한다.
        return OpsSummary(
            delivery = DeliveryOpsSummary(
                total = deliveryCounts.values.sum(),
                sent = deliveryCounts["SENT"] ?: 0L,
                failed = deliveryCounts["FAILED"] ?: 0L,
            ),
            pipeline = PipelineOpsSummary(
                total = pipelineCounts.values.sum(),
                // PipelineRunStatus enum 은 SUCCEEDED 를 사용한다 (SUCCESS 가 아님)
                success = pipelineCounts["SUCCEEDED"] ?: 0L,
                failed = pipelineCounts["FAILED"] ?: 0L,
            ),
        )
    }
}
