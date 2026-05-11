package com.clipping.mcpserver.service

import com.clipping.mcpserver.store.pipeline.CategoryDeliveryStat
import com.clipping.mcpserver.store.pipeline.CategoryOwner
import com.clipping.mcpserver.store.pipeline.DeliveryLogStatus
import com.clipping.mcpserver.store.pipeline.LlmRunStatus
import com.clipping.mcpserver.service.pipeline.PipelineAnalyticsService
import com.clipping.mcpserver.store.pipeline.PipelineAnalyticsStore

import com.clipping.mcpserver.model.BudgetSetting
import com.clipping.mcpserver.service.dto.CostReliabilityData
import com.clipping.mcpserver.service.dto.DailyReliabilityData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PipelineAnalyticsServiceTest {

    private val store = mockk<PipelineAnalyticsStore>()
    private val llmCostService = mockk<LlmCostService>()
    private val operationalKpiService = mockk<OperationalKpiService>()

    private val service = PipelineAnalyticsService(
        store = store,
        llmCostService = llmCostService,
        operationalKpiService = operationalKpiService
    )

    private val seoulZone = ZoneId.of("Asia/Seoul")
    private val today: LocalDate = LocalDate.now(seoulZone)

    @Nested
    inner class `getPipelineSummary 오늘 요약` {

        @Test
        fun `정상 조회 시 오늘의 파이프라인 요약을 반환한다`() {
            // given: 오늘 KPI에 수집 20건/중복 3건
            val todayKpi = DailyOperationalKpi(
                statDate = today,
                categoryId = null,
                itemsCollected = 20,
                excludedCount = 0,
                itemsDuplicates = 3,
                noiseRate = 0.0,
                duplicateRate = 0.15,
                reviewLeadTimeHours = 0.0,
                llmEstimatedCostUsd = 0.05,
                sendAttempts = 10,
                sendSuccesses = 9,
                sendSuccessRate = 0.9
            )
            every {
                operationalKpiService.getDailyKpis(null, today, today)
            } returns listOf(todayKpi)

            // LLM 상태별 건수
            every {
                store.queryLlmStatusCounts(any<Instant>(), any<Instant>())
            } returns mapOf(
                LlmRunStatus.SUCCEEDED to 15,
                LlmRunStatus.EMPTY_RESULT to 4,
                LlmRunStatus.FAILED to 1
            )

            // delivery_log 상태별 건수
            every {
                store.queryDeliveryStatusCounts(today, today)
            } returns mapOf(
                DeliveryLogStatus.SENT to 8,
                DeliveryLogStatus.SKIPPED to 1,
                DeliveryLogStatus.FAILED to 1
            )

            // 비용/예산
            every { llmCostService.getBudget() } returns BudgetSetting(
                monthlyBudgetUsd = 10.0
            )
            every { llmCostService.getCurrentMonthCostUsd() } returns 3.5

            // when
            val result = service.getPipelineSummary()

            // then
            result.todayCollected shouldBe 20
            result.todayDuplicateSkipped shouldBe 3
            result.todaySummarized shouldBe 15
            result.todayRejected shouldBe 4
            result.todayFailed shouldBe 1
            result.todayDeliverySent shouldBe 8
            result.todayDeliverySkipped shouldBe 1
            result.todayDeliveryFailed shouldBe 1
            result.todayCostUsd shouldBe 0.05
            result.monthlyBudgetUsagePercent shouldBe 35.0
        }

        @Test
        fun `예산이 0이면 사용률 0퍼센트를 반환한다`() {
            // given: 빈 KPI
            every {
                operationalKpiService.getDailyKpis(null, today, today)
            } returns emptyList()

            every {
                store.queryLlmStatusCounts(any<Instant>(), any<Instant>())
            } returns emptyMap()

            every {
                store.queryDeliveryStatusCounts(today, today)
            } returns emptyMap()

            every { llmCostService.getBudget() } returns BudgetSetting(
                monthlyBudgetUsd = 0.0
            )
            every { llmCostService.getCurrentMonthCostUsd() } returns 0.0

            // when
            val result = service.getPipelineSummary()

            // then
            result.monthlyBudgetUsagePercent shouldBe 0.0
            result.todayCollected shouldBe 0
        }
    }

    @Nested
    inner class `getPipelineDaily 일간 추이` {

        @Test
        fun `7일 요청 시 7개 행을 반환한다`() {
            // given: 7일간 KPI
            val from = today.minusDays(6)
            val kpis = (0L until 7).map { i ->
                DailyOperationalKpi(
                    statDate = from.plusDays(i),
                    categoryId = null,
                    itemsCollected = 10 + i.toInt(),
                    excludedCount = 0,
                    itemsDuplicates = i.toInt(),
                    noiseRate = 0.0,
                    duplicateRate = 0.0,
                    reviewLeadTimeHours = 0.0,
                    llmEstimatedCostUsd = 0.01,
                    sendAttempts = 5,
                    sendSuccesses = 5,
                    sendSuccessRate = 1.0
                )
            }
            every {
                operationalKpiService.getDailyKpis(null, from, today)
            } returns kpis

            // reliability 데이터
            val dailyReliability = (0L until 7).map { i ->
                DailyReliabilityData(
                    date = from.plusDays(i),
                    succeeded = 8,
                    emptyResult = 1,
                    failed = 0,
                    avgDurationMs = 500,
                    p50DurationMs = 450,
                    p95DurationMs = 900
                )
            }
            every {
                llmCostService.getReliability(from, today, null)
            } returns CostReliabilityData(
                from = from,
                to = today,
                successRate = 0.9,
                emptyResultRate = 0.08,
                failureRate = 0.02,
                avgDurationMs = 500,
                p50DurationMs = 450,
                p95DurationMs = 900,
                dailyBreakdown = dailyReliability,
                topErrors = emptyList()
            )

            // delivery_log 일별 집계 + 거절 사유 모두 빈 결과
            every { store.queryDeliveryDailyMap(from, today) } returns emptyMap()
            every { store.queryRejectReasons(any<Instant>(), any<Instant>()) } returns emptyMap()

            // when
            val result = service.getPipelineDaily(7)

            // then
            result.days shouldHaveSize 7
            result.days.first().date shouldBe from.toString()
            result.days.last().date shouldBe today.toString()
            result.days.first().collected shouldBe 10
            result.days.first().summarizeSucceeded shouldBe 8
        }

        @Test
        fun `거절 사유가 분포에 포함된다`() {
            // given
            val from = today.minusDays(2)
            every {
                operationalKpiService.getDailyKpis(null, from, today)
            } returns emptyList()

            every {
                llmCostService.getReliability(from, today, null)
            } returns CostReliabilityData(
                from = from, to = today,
                successRate = 0.0, emptyResultRate = 0.0, failureRate = 0.0,
                avgDurationMs = 0, p50DurationMs = 0, p95DurationMs = 0,
                dailyBreakdown = emptyList(), topErrors = emptyList()
            )

            every { store.queryDeliveryDailyMap(from, today) } returns emptyMap()
            every {
                store.queryRejectReasons(any<Instant>(), any<Instant>())
            } returns mapOf(
                "CHARS_TOO_SHORT" to 5,
                "OTHER" to 2
            )

            // when
            val result = service.getPipelineDaily(3)

            // then
            result.periodSummary.rejectReasons["CHARS_TOO_SHORT"] shouldBe 5
            result.periodSummary.rejectReasons["OTHER"] shouldBe 2
        }
    }

    @Nested
    inner class `getDeliveryMatrix 발송 매트릭스` {

        @Test
        fun `카테고리별 발송 통계를 사용자 소유 기준으로 그룹핑하여 반환한다`() {
            // given: 카테고리별 발송 통계
            every {
                store.queryDeliveryMatrixByCategory(any<LocalDate>(), any<LocalDate>())
            } returns listOf(
                CategoryDeliveryStat("c1", "AI/테크", 5, 1, 0),
                CategoryDeliveryStat("c2", "금융", 3, 0, 1)
            )

            // 소유자 맵: alice→c1,c2, bob→c1
            every {
                store.queryCategoryOwners(listOf("c1", "c2"))
            } returns mapOf(
                "c1" to listOf(
                    CategoryOwner("u1", "alice"),
                    CategoryOwner("u2", "bob")
                ),
                "c2" to listOf(
                    CategoryOwner("u1", "alice")
                )
            )

            // when
            val result = service.getDeliveryMatrix(1)

            // then
            result.users shouldHaveSize 2

            val alice = result.users.first { it.userId == "u1" }
            alice.username shouldBe "alice"
            alice.categories shouldHaveSize 2
            // 카테고리 건수는 delivery_log 기준이므로 팬아웃 없음
            alice.categories.first { it.categoryId == "c1" }.sent shouldBe 5
            alice.categories.first { it.categoryId == "c2" }.failed shouldBe 1

            val bob = result.users.first { it.userId == "u2" }
            bob.categories shouldHaveSize 1
            bob.categories[0].sent shouldBe 5
        }

        @Test
        fun `발송 내역이 없으면 빈 사용자 목록을 반환한다`() {
            // given
            every {
                store.queryDeliveryMatrixByCategory(any<LocalDate>(), any<LocalDate>())
            } returns emptyList()

            every {
                store.queryCategoryOwners(emptyList())
            } returns emptyMap()

            // when
            val result = service.getDeliveryMatrix(7)

            // then
            result.users shouldHaveSize 0
        }

        @Test
        fun `다중 소유 카테고리의 발송 건수가 팬아웃되지 않는다`() {
            // given: c1 카테고리 발송 10건, 소유자 3명
            every {
                store.queryDeliveryMatrixByCategory(any<LocalDate>(), any<LocalDate>())
            } returns listOf(
                CategoryDeliveryStat("c1", "공용", 10, 0, 0)
            )

            every {
                store.queryCategoryOwners(listOf("c1"))
            } returns mapOf(
                "c1" to listOf(
                    CategoryOwner("u1", "alice"),
                    CategoryOwner("u2", "bob"),
                    CategoryOwner("u3", "charlie")
                )
            )

            // when
            val result = service.getDeliveryMatrix(1)

            // then: 3명 모두 같은 카테고리 통계를 보되, 건수는 10으로 동일 (팬아웃 아님)
            result.users shouldHaveSize 3
            result.users.forEach { user ->
                user.categories shouldHaveSize 1
                user.categories[0].sent shouldBe 10
            }
        }
    }
}
