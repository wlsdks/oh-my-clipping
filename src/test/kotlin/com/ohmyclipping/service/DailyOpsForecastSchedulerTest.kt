package com.ohmyclipping.service

import com.ohmyclipping.service.port.NotificationSeverity

import com.ohmyclipping.model.BudgetSetting
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.CategoryFailureSummary
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.PersonaStore
import com.ohmyclipping.store.PipelineRunStore
import org.junit.jupiter.api.DisplayName
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyOpsForecastSchedulerTest {

    private val pipelineRunStore: PipelineRunStore = mockk(relaxed = true)
    private val notifier: OpsLogNotifier = mockk(relaxed = true)
    private val llmCostService: LlmCostService = mockk(relaxed = true)
    private val categoryStore: CategoryStore = mockk(relaxed = true)
    private val personaStore: PersonaStore = mockk(relaxed = true)

    // 기본 고정 시각: KST 2026-04-15 00:00 (UTC 2026-04-14T15:00Z) — tick 0개
    private val fixedNow = LocalDate.of(2026, 4, 15).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
    private val fixedClock = Clock.fixed(fixedNow, ZoneId.of("Asia/Seoul"))

    private val scheduler = DailyOpsForecastScheduler(
        pipelineRunStore = pipelineRunStore,
        notifier = notifier,
        llmCostService = llmCostService,
        categoryStore = categoryStore,
        personaStore = personaStore,
        clock = fixedClock,
    )

    @BeforeEach
    fun setUp() {
        every { llmCostService.getCurrentMonthCostUsd() } returns 0.0
        every { llmCostService.getBudget() } returns BudgetSetting(monthlyBudgetUsd = 0.0)
        every { pipelineRunStore.findFailureCountsPerCategorySince(any(), any()) } returns emptyList()
        every { categoryStore.countActive() } returns 0L
        every { personaStore.countTotalActiveSubscriptions() } returns 0L
    }

    @Nested
    inner class `runOnce` {

        @Test
        fun `runOnce는 forecast를 조립해 postDailyForecast를 호출한다`() {
            // KST 2026-04-18 08:00 = UTC 2026-04-17T23:00:00Z
            val fixedNow = Instant.parse("2026-04-17T23:00:00Z")
            every { pipelineRunStore.findFailureCountsPerCategorySince(any(), any()) } returns
                listOf(
                    CategoryFailureSummary("c1", "경제뉴스", 5),
                    CategoryFailureSummary("c2", "AI", 3),
                )

            scheduler.runOnce(fixedNow)

            verify {
                notifier.postDailyForecast(withArg { f ->
                    f.forecastDate shouldBe LocalDate.of(2026, 4, 18)
                    f.riskSources shouldHaveSize 2
                    f.riskSources[0].sourceName shouldBe "경제뉴스"
                    f.riskSources[0].failureCount shouldBe 5
                })
            }
        }

        @Test
        fun `riskSources가 없으면 빈 리스트로 조립된다`() {
            val fixedNow = Instant.parse("2026-04-17T23:00:00Z")
            every { pipelineRunStore.findFailureCountsPerCategorySince(any(), any()) } returns emptyList()

            scheduler.runOnce(fixedNow)

            verify {
                notifier.postDailyForecast(withArg { f ->
                    f.riskSources.shouldBeEmpty()
                })
            }
        }
    }

    @Nested
    inner class `buildForecast` {

        @Test
        fun `LLM 비용이 있으면 KRW 변환 및 월말 예측값을 포함한다`() {
            // 월 15일 기준: 100 USD → 135,000 KRW → 월말 예측 = 135,000 * 30 / 15 = 270,000 KRW
            every { llmCostService.getCurrentMonthCostUsd() } returns 100.0
            every { llmCostService.getBudget() } returns BudgetSetting(monthlyBudgetUsd = 200.0)

            val date = LocalDate.of(2026, 4, 15)
            val now = date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
            val forecast = scheduler.buildForecast(date, now)

            forecast.llmMonthlyUsageKrw shouldBe 135_000L
            forecast.llmMonthlyBudgetKrw shouldBe 270_000L
            // 월말 예측: 135,000 * 30 / 15 = 270,000
            forecast.llmProjectedMonthEndKrw shouldBe 270_000L
        }

        @Test
        fun `forecastDate는 Seoul 타임존 기준으로 계산된다`() {
            // UTC 기준 4월 17일 23시 = KST 4월 18일
            val now = Instant.parse("2026-04-17T23:00:00Z")
            val date = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()

            val forecast = scheduler.buildForecast(date, now)

            forecast.forecastDate shouldBe LocalDate.of(2026, 4, 18)
        }
    }

    @Nested
    @DisplayName("expectedRunCount/expectedDigestCount 와이어링")
    inner class WiredCounts {

        @Test
        fun `active category 3개 × 오늘 남은 tick 3개면 expectedRunCount = 9`() {
            // KST 2026-04-15 14:00 (UTC 05:00) → 남은 tick: 15시, 19시, 23시 = 3개
            val clockAt14Kst = Clock.fixed(Instant.parse("2026-04-15T05:00:00Z"), ZoneId.of("Asia/Seoul"))
            val s = DailyOpsForecastScheduler(
                pipelineRunStore = pipelineRunStore,
                notifier = notifier,
                llmCostService = llmCostService,
                categoryStore = categoryStore,
                personaStore = personaStore,
                clock = clockAt14Kst,
            )
            every { categoryStore.countActive() } returns 3L

            val forecast = s.buildForecast(LocalDate.of(2026, 4, 15), clockAt14Kst.instant())

            forecast.expectedRunCount shouldBe 9
        }

        @Test
        fun `active category 0개면 expectedRunCount = 0`() {
            every { categoryStore.countActive() } returns 0L

            val forecast = scheduler.buildForecast(LocalDate.of(2026, 4, 15), fixedNow)

            forecast.expectedRunCount shouldBe 0
        }

        @Test
        fun `PersonaStore가 반환하는 활성 구독 수가 expectedDigestCount에 반영된다`() {
            every { personaStore.countTotalActiveSubscriptions() } returns 42L

            val forecast = scheduler.buildForecast(LocalDate.of(2026, 4, 15), fixedNow)

            forecast.expectedDigestCount shouldBe 42
        }
    }

    @Nested
    @DisplayName("forecast.severity 계산")
    inner class ForecastSeverity {

        private val fixedDate = LocalDate.of(2026, 4, 15)
        private val fixedNow = fixedDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()

        /** usageKrw / budgetKrw 비율로 scheduler가 usagePct를 계산한다. */
        private fun stubBudget(usageKrw: Long, budgetKrw: Long) {
            // 1 USD = 1,350 KRW 고정 환율이므로 역산: usd = krw / 1350
            every { llmCostService.getCurrentMonthCostUsd() } returns (usageKrw / 1_350.0)
            every { llmCostService.getBudget() } returns BudgetSetting(monthlyBudgetUsd = budgetKrw / 1_350.0)
        }

        @Test
        fun `사용률 79퍼센트 리스크 0이면 INFO`() {
            stubBudget(usageKrw = 79_000L, budgetKrw = 100_000L)
            every { pipelineRunStore.findFailureCountsPerCategorySince(any(), any()) } returns emptyList()

            val forecast = scheduler.buildForecast(fixedDate, fixedNow)

            forecast.severity shouldBe NotificationSeverity.INFO
        }

        @Test
        fun `사용률 80퍼센트 경계는 WARN`() {
            stubBudget(usageKrw = 80_000L, budgetKrw = 100_000L)
            every { pipelineRunStore.findFailureCountsPerCategorySince(any(), any()) } returns emptyList()

            val forecast = scheduler.buildForecast(fixedDate, fixedNow)

            forecast.severity shouldBe NotificationSeverity.WARN
        }

        @Test
        fun `사용률 90퍼센트 경계는 CRITICAL`() {
            stubBudget(usageKrw = 90_000L, budgetKrw = 100_000L)
            every { pipelineRunStore.findFailureCountsPerCategorySince(any(), any()) } returns emptyList()

            val forecast = scheduler.buildForecast(fixedDate, fixedNow)

            forecast.severity shouldBe NotificationSeverity.CRITICAL
        }

        @Test
        fun `사용률 92퍼센트는 CRITICAL`() {
            stubBudget(usageKrw = 92_000L, budgetKrw = 100_000L)
            every { pipelineRunStore.findFailureCountsPerCategorySince(any(), any()) } returns emptyList()

            val forecast = scheduler.buildForecast(fixedDate, fixedNow)

            forecast.severity shouldBe NotificationSeverity.CRITICAL
        }

        @Test
        fun `사용률 70퍼센트여도 리스크 소스 있으면 CRITICAL`() {
            stubBudget(usageKrw = 70_000L, budgetKrw = 100_000L)
            every { pipelineRunStore.findFailureCountsPerCategorySince(any(), any()) } returns listOf(
                CategoryFailureSummary("c1", "경제뉴스", 4),
                CategoryFailureSummary("c2", "AI", 3),
            )

            val forecast = scheduler.buildForecast(fixedDate, fixedNow)

            forecast.severity shouldBe NotificationSeverity.CRITICAL
        }
    }
}
