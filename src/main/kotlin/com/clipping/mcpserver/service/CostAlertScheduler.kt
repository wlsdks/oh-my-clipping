package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.OpsNotificationEvent

import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.service.port.OpsLogNotifier
import com.clipping.mcpserver.store.BudgetSettingStore
import com.clipping.mcpserver.store.CostAlertNotificationStore
import com.clipping.mcpserver.store.LlmRunStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

/**
 * 매시간 당월 LLM 사용 비용을 집계하여 3단계 임계값 초과 시 운영 채널에 알린다.
 *
 * - 80%: DailyForecast(M12)에서만 표시 — 즉시 알림 없음.
 * - 90%: WARN 알림 1회 (DB dedup, 월별·임계값별).
 * - 100%: CRITICAL 알림 1회 (DB dedup, 월별·임계값별, 90% 이력과 독립).
 */
@Component
class CostAlertScheduler(
    private val llmRunStore: LlmRunStore,
    private val budgetSettingStore: BudgetSettingStore,
    private val properties: ClippingMcpServerProperties,
    private val runtime: RuntimeSettingService,
    private val opsLogNotifier: OpsLogNotifier,
    private val costAlertNotificationStore: CostAlertNotificationStore,
    private val metrics: ClippingMetrics,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    companion object {
        /** USD → KRW 환산 비율 (근사값) */
        const val USD_TO_KRW = 1_350.0

        /** 레거시 일일 비용 알림 임계값 (원) — 월 예산 미설정 시 사용 */
        const val LEGACY_DAILY_COST_THRESHOLD_KRW = 30_000.0

        private val KST = ZoneId.of("Asia/Seoul")
        private val MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM")

        /** 100% 임계값 레벨 키 */
        internal const val LEVEL_CRITICAL_100 = "CRITICAL_100"
    }

    /**
     * 매 정시 실행: 당월 LLM 사용 비용을 집계하고 임계값 도달 시 운영 채널에 알린다.
     * 80%는 DailyForecast 전용이므로 즉시 알림을 발송하지 않는다.
     * 90%와 100%는 DB dedup으로 각각 독립적으로 월 1회 발송한다.
     */
    @Scheduled(cron = "0 0 * * * *")
    fun checkDailyCost() = metrics.recordSchedulerRun("cost_alert") {
        log.info { "CostAlertScheduler started" }
        val start = System.nanoTime()

        // DB 예산 설정을 읽어 Slack 알림 비활성화 시 조기 종료
        val budget = budgetSettingStore.get()
        if (!budget.slackAlertEnabled) return@recordSchedulerRun

        val today = LocalDate.now(clock.withZone(KST))
        val monthId = today.format(MONTH_FMT)

        // 월 예산이 설정되어 있으면 사용 비율을 계산하고, 없으면 레거시 일일 임계값으로 폴백한다
        if (budget.monthlyBudgetUsd <= 0) {
            // 월 예산 미설정 — 레거시 일일 비용 임계값 로직을 유지한다
            checkLegacyDailyCost(today, budget, monthId)
        } else {
            // 월 예산 설정 — 사용 비율로 3단계 임계값 체크
            checkMonthlyBudgetThresholds(today, budget, monthId)
        }

        val elapsed = (System.nanoTime() - start) / 1_000_000
        log.info { "CostAlertScheduler completed in ${elapsed}ms" }
    }

    /**
     * 월 예산 기반 3단계 임계값 체크.
     * 80% 미만: 무시. 90~100%: WARN 1회. 100%+: CRITICAL 1회.
     */
    private fun checkMonthlyBudgetThresholds(
        today: LocalDate,
        budget: com.clipping.mcpserver.model.BudgetSetting,
        monthId: String,
    ) {
        val (monthlyUsageKrw, _) = computeMonthlyUsageKrw(today) ?: return
        val monthlyBudgetKrw = budget.monthlyBudgetUsd * USD_TO_KRW
        if (monthlyBudgetKrw <= 0) return

        val usagePct = ((monthlyUsageKrw / monthlyBudgetKrw) * 100).toInt()
        log.debug { "Monthly budget usage: ${usagePct}% (${monthlyUsageKrw}KRW / ${monthlyBudgetKrw}KRW)" }

        checkThresholds(usagePct, runtime.current(), monthId)
    }

    /**
     * 레거시 일일 임계값 로직 — 월 예산 미설정 시 LEGACY_DAILY_COST_THRESHOLD_KRW 기반으로 동작한다.
     * 하루에 1회만 알림을 발송한다 — cost_alert_notifications 테이블에 (monthId, "DAILY_COST_{today}") 로 dedup.
     */
    private fun checkLegacyDailyCost(
        today: LocalDate,
        budget: com.clipping.mcpserver.model.BudgetSetting,
        monthId: String,
    ) {
        val (inputChars, outputChars) = runCatching {
            val startOfDay = today.atStartOfDay(KST).toInstant()
            val endOfDay = today.plusDays(1).atStartOfDay(KST).toInstant()
            llmRunStore.sumCharsBetween(startOfDay, endOfDay)
        }.onFailure { e ->
            log.error(e) { "Failed to query daily LLM cost" }
        }.getOrDefault(0L to 0L)

        val costUsd = calculateCostUsd(inputChars, outputChars)
        val costKrw = costUsd * USD_TO_KRW
        val dailyThresholdKrw = LEGACY_DAILY_COST_THRESHOLD_KRW

        if (costKrw >= dailyThresholdKrw) {
            // 당일 중복 발송 방지 — 이미 오늘 알림을 보냈으면 조기 종료
            val dailyLevel = "DAILY_COST_$today"
            if (!costAlertNotificationStore.tryRegister(monthId, dailyLevel)) return

            val formattedCost = String.format("%,.0f", costKrw)
            val message = "AI 비용 알림: 오늘(${today}) 누적 비용이 ₩${formattedCost}에 도달했습니다. " +
                "(입력: ${formatChars(inputChars)}, 출력: ${formatChars(outputChars)})"
            log.warn { message }
            // 레거시 일일 임계값 도달 — 당일 1회만 COST_THRESHOLD_EXCEEDED 발송
            opsLogNotifier.postOpsEvent(
                OpsNotificationEvent.COST_THRESHOLD_EXCEEDED,
                mapOf("date" to today.toString(), "usage_krw" to costKrw.toLong())
            )
        }
    }

    /**
     * 사용 비율에 따라 임계값을 체크하고 DB dedup 후 알림을 발송한다.
     * 80%: DailyForecast 전용 — 즉시 알림 없음.
     * 90%+: WARN 1회 (월별·레벨별 dedup).
     * 100%+: CRITICAL 1회 (월별·레벨별 dedup, 90% 이력과 독립).
     */
    internal fun checkThresholds(usagePct: Int, settings: RuntimeSettingService.RuntimeSettings, monthId: String) {
        when {
            usagePct >= 100 -> {
                // 100% 초과 — CRITICAL 알림 (90% 발송 이력과 독립)
                if (costAlertNotificationStore.tryRegister(monthId, LEVEL_CRITICAL_100)) {
                    log.warn { "Monthly budget 100% reached: $usagePct% (month=$monthId)" }
                    opsLogNotifier.postOpsEvent(
                        OpsNotificationEvent.BUDGET_EXCEEDED,
                        mapOf("usage_pct" to usagePct, "month_id" to monthId)
                    )
                }
            }
            usagePct >= settings.opsBudgetCriticalPct -> {
                // 90% 도달 — WARN 알림 1회
                val level = "CRITICAL_${settings.opsBudgetCriticalPct}"
                if (costAlertNotificationStore.tryRegister(monthId, level)) {
                    log.warn { "Monthly budget ${settings.opsBudgetCriticalPct}% reached: $usagePct% (month=$monthId)" }
                    opsLogNotifier.postOpsEvent(
                        OpsNotificationEvent.BUDGET_CRITICAL,
                        mapOf("usage_pct" to usagePct, "month_id" to monthId)
                    )
                }
            }
            // 80% 미만 또는 80~89%: DailyForecast(M12)에서만 표시 — 즉시 알림 없음
        }
    }

    /** 당월 LLM 사용 비용(KRW)을 계산한다. */
    private fun computeMonthlyUsageKrw(today: LocalDate): Pair<Double, Pair<Long, Long>>? {
        val (inputChars, outputChars) = runCatching {
            val startOfMonth = today.withDayOfMonth(1).atStartOfDay(KST).toInstant()
            val endOfMonth = today.plusDays(1).atStartOfDay(KST).toInstant()
            llmRunStore.sumCharsBetween(startOfMonth, endOfMonth)
        }.onFailure { e ->
            log.error(e) { "Failed to query monthly LLM cost" }
        }.getOrNull() ?: return null

        val costUsd = calculateCostUsd(inputChars, outputChars)
        return (costUsd * USD_TO_KRW) to (inputChars to outputChars)
    }

    /**
     * 입력/출력 문자 수를 기반으로 USD 비용을 계산한다.
     * ClippingMcpServerProperties의 cost per million 단위를 사용한다.
     */
    private fun calculateCostUsd(inputChars: Long, outputChars: Long): Double {
        // 문자 수 → 토큰 수 환산 (대략 4자당 1토큰)
        val inputTokens = inputChars / 4.0
        val outputTokens = outputChars / 4.0
        val inputCost = (inputTokens / 1_000_000.0) * properties.llmInputCostPerMillionUsd
        val outputCost = (outputTokens / 1_000_000.0) * properties.llmOutputCostPerMillionUsd
        return inputCost + outputCost
    }

    private fun formatChars(chars: Long): String = when {
        chars >= 1_000_000 -> String.format("%.1fM자", chars / 1_000_000.0)
        chars >= 1_000 -> String.format("%.1fK자", chars / 1_000.0)
        else -> "${chars}자"
    }

}
