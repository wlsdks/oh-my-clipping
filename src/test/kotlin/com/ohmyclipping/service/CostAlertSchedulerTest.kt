package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsNotificationEvent

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.model.BudgetSetting
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.BudgetSettingStore
import com.ohmyclipping.store.CostAlertNotificationStore
import com.ohmyclipping.store.LlmRunStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CostAlertSchedulerTest {

    private val llmRunStore = mockk<LlmRunStore>()
    private val budgetSettingStore = mockk<BudgetSettingStore>()
    private val properties = ClippingMcpServerProperties(
        llmInputCostPerMillionUsd = 0.30,
        llmOutputCostPerMillionUsd = 2.50
    )
    private val runtime = mockk<RuntimeSettingService>()
    private val opsLogNotifier = mockk<OpsLogNotifier>(relaxed = true)
    private val costAlertNotificationStore = mockk<CostAlertNotificationStore>()
    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }

    /** KST 2026-04-17 12:00 기준 고정 Clock */
    private val fixedClock2026_04_17: Clock =
        Clock.fixed(Instant.parse("2026-04-17T03:00:00Z"), ZoneId.of("Asia/Seoul"))

    private val scheduler = CostAlertScheduler(
        llmRunStore,
        budgetSettingStore,
        properties,
        runtime,
        opsLogNotifier,
        costAlertNotificationStore,
        metrics,
        fixedClock2026_04_17,
    )

    private fun newSchedulerWithClock(clock: Clock) = CostAlertScheduler(
        llmRunStore,
        budgetSettingStore,
        properties,
        runtime,
        opsLogNotifier,
        costAlertNotificationStore,
        metrics,
        clock,
    )

    private fun defaultRuntimeSettings(
        opsBudgetCriticalPct: Int = 90,
    ) = mockk<RuntimeSettingService.RuntimeSettings> {
        every { this@mockk.opsBudgetCriticalPct } returns opsBudgetCriticalPct
    }

    @Nested
    inner class `레거시 일일 임계값 (월 예산 미설정)` {

        @Test
        fun `임계값 미만이면 알림을 전송하지 않는다`() {
            every { budgetSettingStore.get() } returns BudgetSetting(slackAlertEnabled = true)
            // 소량 사용: 1000자 입력 + 1000자 출력 → 비용 거의 0
            every { llmRunStore.sumCharsBetween(any(), any()) } returns (1000L to 1000L)

            scheduler.checkDailyCost()

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }

        @Test
        fun `임계값 초과하면 COST_THRESHOLD_EXCEEDED 알림을 전송한다`() {
            every { budgetSettingStore.get() } returns BudgetSetting(slackAlertEnabled = true)
            // 대량 사용: 100M자 입력 + 50M자 출력 → 비용 초과
            every { llmRunStore.sumCharsBetween(any(), any()) } returns (100_000_000L to 50_000_000L)
            every { costAlertNotificationStore.tryRegister("2026-04", "DAILY_COST_2026-04-17") } returns true

            scheduler.checkDailyCost()

            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED), any()) }
        }

        @Test
        fun `Slack 알림이 비활성화되면 비용 집계를 건너뛴다`() {
            every { budgetSettingStore.get() } returns BudgetSetting(slackAlertEnabled = false)

            scheduler.checkDailyCost()

            verify(exactly = 0) { llmRunStore.sumCharsBetween(any(), any()) }
            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }
    }

    @Nested
    inner class `비용 집계 실패 시 에러 격리` {

        @Test
        fun `LLM 비용 조회 실패 시 예외를 전파하지 않는다`() {
            every { budgetSettingStore.get() } returns BudgetSetting(slackAlertEnabled = true)
            every { llmRunStore.sumCharsBetween(any(), any()) } throws RuntimeException("DB connection lost")

            // 예외 없이 완료되어야 한다
            scheduler.checkDailyCost()

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }
    }

    @Nested
    inner class `3단계 임계값 체크 (checkThresholds)` {

        @Test
        fun `80퍼센트 도달 시 즉시 알림을 발송하지 않는다 (DailyForecast 전용)`() {
            every { runtime.current() } returns defaultRuntimeSettings(opsBudgetCriticalPct = 90)

            scheduler.checkThresholds(80, runtime.current(), "2026-04")

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
            verify(exactly = 0) { costAlertNotificationStore.tryRegister(any(), any()) }
        }

        @Test
        fun `89퍼센트 도달 시에도 즉시 알림을 발송하지 않는다`() {
            every { runtime.current() } returns defaultRuntimeSettings(opsBudgetCriticalPct = 90)

            scheduler.checkThresholds(89, runtime.current(), "2026-04")

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }

        @Test
        fun `90퍼센트 도달 시 BUDGET_CRITICAL WARN 알림을 1회 발송한다`() {
            val settings = defaultRuntimeSettings(opsBudgetCriticalPct = 90)
            every { costAlertNotificationStore.tryRegister("2026-04", "CRITICAL_90") } returns true

            scheduler.checkThresholds(90, settings, "2026-04")

            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.BUDGET_CRITICAL), any()) }
        }

        @Test
        fun `90퍼센트 재호출 시 dedup으로 알림을 발송하지 않는다`() {
            val settings = defaultRuntimeSettings(opsBudgetCriticalPct = 90)
            every { costAlertNotificationStore.tryRegister("2026-04", "CRITICAL_90") } returns false

            scheduler.checkThresholds(90, settings, "2026-04")

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }

        @Test
        fun `100퍼센트 초과 시 BUDGET_EXCEEDED CRITICAL 알림을 1회 발송한다`() {
            val settings = defaultRuntimeSettings(opsBudgetCriticalPct = 90)
            every { costAlertNotificationStore.tryRegister("2026-04", "CRITICAL_100") } returns true

            scheduler.checkThresholds(100, settings, "2026-04")

            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.BUDGET_EXCEEDED), any()) }
        }

        @Test
        fun `100퍼센트 재호출 시 dedup으로 알림을 발송하지 않는다`() {
            val settings = defaultRuntimeSettings(opsBudgetCriticalPct = 90)
            every { costAlertNotificationStore.tryRegister("2026-04", "CRITICAL_100") } returns false

            scheduler.checkThresholds(100, settings, "2026-04")

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }

        @Test
        fun `90퍼센트 발송 이력이 있어도 100퍼센트 도달 시 독립적으로 발송한다`() {
            val settings = defaultRuntimeSettings(opsBudgetCriticalPct = 90)
            // 100% 레벨은 90% 레벨과 독립 — tryRegister("CRITICAL_100") = true
            every { costAlertNotificationStore.tryRegister("2026-04", "CRITICAL_100") } returns true

            scheduler.checkThresholds(100, settings, "2026-04")

            // BUDGET_EXCEEDED가 발송된다 (90% 이력과 무관)
            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.BUDGET_EXCEEDED), any()) }
        }
    }

    @Nested
    inner class `레거시 일일 비용 dedup` {

        @Test
        fun `같은 날 동일 일비 초과 알림은 1회만 발송한다`() {
            // 당일 2번의 hourly tick에서 각각 tryRegister — 첫 번째만 true
            every { budgetSettingStore.get() } returns BudgetSetting(slackAlertEnabled = true)
            every { llmRunStore.sumCharsBetween(any(), any()) } returns (100_000_000L to 50_000_000L)
            every {
                costAlertNotificationStore.tryRegister("2026-04", "DAILY_COST_2026-04-17")
            } returnsMany listOf(true, false)

            scheduler.checkDailyCost()
            scheduler.checkDailyCost()

            verify(exactly = 1) {
                opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED), any())
            }
        }

        @Test
        fun `다음 날은 dedup 리셋되어 각 날짜별로 알림 1회씩 발송된다`() {
            every { budgetSettingStore.get() } returns BudgetSetting(slackAlertEnabled = true)
            every { llmRunStore.sumCharsBetween(any(), any()) } returns (100_000_000L to 50_000_000L)
            every { costAlertNotificationStore.tryRegister(any(), any()) } returns true

            val day1Clock = Clock.fixed(Instant.parse("2026-04-17T03:00:00Z"), ZoneId.of("Asia/Seoul"))
            val day2Clock = Clock.fixed(Instant.parse("2026-04-18T03:00:00Z"), ZoneId.of("Asia/Seoul"))

            newSchedulerWithClock(day1Clock).checkDailyCost()
            newSchedulerWithClock(day2Clock).checkDailyCost()

            verify { costAlertNotificationStore.tryRegister("2026-04", "DAILY_COST_2026-04-17") }
            verify { costAlertNotificationStore.tryRegister("2026-04", "DAILY_COST_2026-04-18") }
            verify(exactly = 2) {
                opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.COST_THRESHOLD_EXCEEDED), any())
            }
        }

        @Test
        fun `일비가 임계값 이하이면 dedup 저장 없이 알림을 발송하지 않는다`() {
            every { budgetSettingStore.get() } returns BudgetSetting(slackAlertEnabled = true)
            // 소량 사용 — 임계값 미달
            every { llmRunStore.sumCharsBetween(any(), any()) } returns (1000L to 1000L)

            scheduler.checkDailyCost()

            verify(exactly = 0) { costAlertNotificationStore.tryRegister(any(), any()) }
            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }
    }
}
