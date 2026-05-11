package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.BudgetSetting
import com.clipping.mcpserver.service.port.OpsLogNotifier
import com.clipping.mcpserver.store.CategoryFailureSummary
import com.clipping.mcpserver.store.PipelineRunStore
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class WeeklyOpsActionReportSchedulerTest {

    private val pipelineRunStore: PipelineRunStore = mockk(relaxed = true)
    private val notifier: OpsLogNotifier = mockk(relaxed = true)
    private val llmCostService: LlmCostService = mockk(relaxed = true)

    private val scheduler = WeeklyOpsActionReportScheduler(
        pipelineRunStore = pipelineRunStore,
        notifier = notifier,
        llmCostService = llmCostService,
    )

    private fun emptyCostSummary(totalEstimatedUsd: Double = 0.0) = LlmCostService.CostSummary(
        from = Instant.EPOCH,
        to = Instant.EPOCH,
        inputCostPerMillionUsd = 0.0,
        outputCostPerMillionUsd = 0.0,
        totalRequestCount = 0,
        totalTokensIn = 0L,
        totalTokensOut = 0L,
        totalEstimatedUsd = totalEstimatedUsd,
        rows = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        every { llmCostService.summarizeByChannel(any(), any()) } returns emptyCostSummary()
        every { llmCostService.getBudget() } returns BudgetSetting(monthlyBudgetUsd = 0.0)
        every { pipelineRunStore.findTopFailingSourcesSince(any(), any()) } returns emptyList()
        every { pipelineRunStore.findDurationsBetween(any(), any()) } returns emptyList()
    }

    @Nested
    inner class `runOnce` {

        @Test
        fun `runOnce는 WeeklyActionReport를 조립해 postWeeklyActionReport를 호출한다`() {
            // KST 2026-04-20 월요일 09:00 = UTC 2026-04-20T00:00:00Z
            val fixedNow = Instant.parse("2026-04-20T00:00:00Z")
            every { pipelineRunStore.findTopFailingSourcesSince(any(), 5) } returns
                listOf(CategoryFailureSummary("c1", "경제뉴스", 15))
            every { pipelineRunStore.findDurationsBetween(any(), any()) } returns
                listOf(100L, 200L, 300L, 400L, 500L)

            scheduler.runOnce(fixedNow)

            verify {
                notifier.postWeeklyActionReport(withArg { r ->
                    r.topFailingSources shouldHaveSize 1
                    r.topFailingSources[0].sourceName shouldBe "경제뉴스"
                    r.latencyMedianMsCurrent shouldBe 300L
                    r.clickDeclineCategories.shouldBeNull()
                })
            }
        }
    }

    @Nested
    inner class `latencyMedian` {

        @Test
        fun `홀수 개 duration 목록의 중앙값을 반환한다`() {
            scheduler.latencyMedian(listOf(100L, 200L, 300L, 400L, 500L)) shouldBe 300L
        }

        @Test
        fun `짝수 개 duration 목록의 중앙값은 두 중간값의 평균이다`() {
            scheduler.latencyMedian(listOf(100L, 200L, 300L, 400L)) shouldBe 250L
        }

        @Test
        fun `빈 목록이면 0을 반환한다`() {
            scheduler.latencyMedian(emptyList()) shouldBe 0L
        }

        @Test
        fun `단일 원소이면 해당 값이 중앙값이다`() {
            scheduler.latencyMedian(listOf(999L)) shouldBe 999L
        }
    }

    @Nested
    inner class `buildReport` {

        @Test
        fun `weekStart는 weekEnd 6일 전이다`() {
            val fixedNow = Instant.parse("2026-04-20T00:00:00Z") // KST 2026-04-20 월요일 09:00
            val report = scheduler.buildReport(fixedNow)

            report.weekEnd shouldBe LocalDate.of(2026, 4, 20)
            report.weekStart shouldBe LocalDate.of(2026, 4, 14)
        }

        @Test
        fun `LLM 주간 비용이 KRW로 변환된다`() {
            every { llmCostService.summarizeByChannel(any(), any()) } returns emptyCostSummary(10.0) // 10 USD
            every { llmCostService.getBudget() } returns BudgetSetting(monthlyBudgetUsd = 40.0) // 40/4=10 USD/주

            val report = scheduler.buildReport(Instant.parse("2026-04-20T00:00:00Z"))

            report.llmWeeklyUsageKrw shouldBe 13_500L  // 10 * 1,350
            report.llmWeeklyBudgetKrw shouldBe 13_500L // 40 * 1,350 / 4
        }

        @Test
        fun `이번 주와 지난 주 레이턴시를 각각 올바르게 집계한다`() {
            var callCount = 0
            every { pipelineRunStore.findDurationsBetween(any(), any()) } answers {
                if (callCount++ == 0) listOf(200L, 400L, 600L) // 이번 주 → 중앙값 400
                else listOf(100L, 300L, 500L)                  // 지난 주 → 중앙값 300
            }

            val report = scheduler.buildReport(Instant.parse("2026-04-20T00:00:00Z"))

            report.latencyMedianMsCurrent shouldBe 400L
            report.latencyMedianMsPrevious shouldBe 300L
        }
    }
}
