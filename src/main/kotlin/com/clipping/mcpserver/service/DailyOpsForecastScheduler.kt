package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.DailyForecast
import com.clipping.mcpserver.service.port.RiskSourceSummary
import com.clipping.mcpserver.service.port.NotificationSeverity
import com.clipping.mcpserver.service.port.OpsLogNotifier
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.PersonaStore
import com.clipping.mcpserver.store.PipelineRunStore
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 일별 운영 예측 리포트(M12) 스케줄러.
 *
 * OpsScheduleConfig의 동적 트리거로 호출된다.
 * - 최근 24시간 실패 카테고리 집계
 * - LLM 월간 비용 집계 (LlmCostService 연동)
 * - 비용 예측 (월초~오늘 기준 선형 보정)
 */
@Component
class DailyOpsForecastScheduler(
    private val pipelineRunStore: PipelineRunStore,
    private val notifier: OpsLogNotifier,
    private val llmCostService: LlmCostService,
    private val categoryStore: CategoryStore,
    private val personaStore: PersonaStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")

    /** OpsScheduleConfig 트리거 진입점. */
    fun runOnce(now: Instant) {
        val forecastDate = now.atZone(seoul).toLocalDate()
        val forecast = buildForecast(forecastDate, now)
        notifier.postDailyForecast(forecast)
    }

    /**
     * forecastDate / now 기준으로 DailyForecast를 조립한다.
     * 테스트에서 직접 호출 가능하도록 internal 접근을 열어 둔다.
     */
    fun buildForecast(date: LocalDate, now: Instant): DailyForecast {
        val last24h = now.minus(24, ChronoUnit.HOURS)

        // 최근 24시간 내 3회 이상 실패한 카테고리를 위험 소스로 분류한다
        val riskSources = pipelineRunStore.findFailureCountsPerCategorySince(last24h, minFailures = 3)
            .map { RiskSourceSummary(it.categoryId, it.categoryName, it.failureCount) }

        // 이번 달 LLM 사용 비용을 조회하고 KRW로 변환한다 (1 USD ≈ 1,350 KRW 고정 환율)
        val monthlyUsageUsd = llmCostService.getCurrentMonthCostUsd()
        val monthlyUsageKrw = (monthlyUsageUsd * USD_TO_KRW).toLong()

        // 월간 예산 (USD → KRW 변환)
        val budgetSetting = llmCostService.getBudget()
        val monthlyBudgetKrw = (budgetSetting.monthlyBudgetUsd * USD_TO_KRW).toLong()

        // 선형 보정으로 월말 예상 비용을 계산한다
        val projectedMonthEndKrw = projectLinear(monthlyUsageKrw, date)

        // 활성 카테고리 수 × 오늘 남은 크론 tick 수로 예상 실행 횟수를 계산한다
        val activeCategories = categoryStore.countActive()
        val ticksRemaining = ticksRemainingToday(now)
        val expectedRunCount = (activeCategories * ticksRemaining).toInt()

        // 활성 구독 수로 예상 다이제스트 발송 건수를 계산한다
        val expectedDigestCount = personaStore.countTotalActiveSubscriptions().toInt()

        // 예산 대비 사용률(정수 %)을 계산하고 심각도를 결정한다
        val usagePct = if (monthlyBudgetKrw == 0L) 0
                       else ((monthlyUsageKrw * 100L) / monthlyBudgetKrw).toInt()
        val severity = computeSeverity(usagePct, riskSources.size)

        return DailyForecast(
            forecastDate = date,
            expectedRunCount = expectedRunCount,
            expectedDigestCount = expectedDigestCount,
            llmMonthlyUsageKrw = monthlyUsageKrw,
            llmMonthlyBudgetKrw = monthlyBudgetKrw,
            llmProjectedMonthEndKrw = projectedMonthEndKrw,
            riskSources = riskSources,
            severity = severity,
        )
    }

    /**
     * 예산 사용률과 위험 소스 수를 기반으로 알림 심각도를 결정한다.
     * - usagePct ≥ OPS_BUDGET_CRITICAL_PCT 또는 riskCount > 0 → CRITICAL
     * - usagePct ≥ OPS_BUDGET_WARN_PCT → WARN
     * - 그 외 → INFO
     */
    private fun computeSeverity(usagePct: Int, riskCount: Int): NotificationSeverity = when {
        usagePct >= OPS_BUDGET_CRITICAL_PCT || riskCount > 0 -> NotificationSeverity.CRITICAL
        usagePct >= OPS_BUDGET_WARN_PCT                      -> NotificationSeverity.WARN
        else                                                 -> NotificationSeverity.INFO
    }

    /**
     * 당월 현재까지 사용량을 기준으로 월말 예상 비용을 선형 보정한다.
     * dayOfMonth가 0이면(방어 코드) 그대로 반환한다.
     */
    private fun projectLinear(usage: Long, today: LocalDate): Long {
        val daysInMonth = today.lengthOfMonth()
        val dayOfMonth = today.dayOfMonth
        return if (dayOfMonth == 0) 0L else (usage * daysInMonth) / dayOfMonth
    }

    /**
     * now 이후 오늘(KST) 자정 이전에 남은 크론 tick 수를 반환한다.
     * 크론 표현식은 AutoCollectionScheduler.cronExpression()과 반드시 일치해야 한다.
     * Must match AutoCollectionScheduler.cronExpression() = "0 5 3,7,11,15,19,23 * * *"
     */
    private fun ticksRemainingToday(now: Instant): Long {
        // AutoCollectionScheduler의 크론 표현식을 하드코딩한다 (순환 의존성 방지)
        val cronExpr = CronExpression.parse("0 5 3,7,11,15,19,23 * * *")
        val nowKst = ZonedDateTime.ofInstant(now, seoul)
        val endOfDayKst = nowKst.toLocalDate().atTime(23, 59, 59).atZone(seoul)
        var count = 0L
        var pivot: ZonedDateTime = nowKst
        // 안전 상한 20회로 무한 루프를 방지한다
        repeat(20) {
            val next = cronExpr.next(pivot) ?: return count
            if (next.isAfter(endOfDayKst)) return count
            count++
            pivot = next
        }
        return count
    }

    companion object {
        /** USD → KRW 변환 고정 환율. */
        private const val USD_TO_KRW = 1_350.0

        /** 예산 대비 사용률 경고 임계값(%). */
        private const val OPS_BUDGET_WARN_PCT = 80

        /** 예산 대비 사용률 위험 임계값(%). */
        private const val OPS_BUDGET_CRITICAL_PCT = 90
    }
}
