package com.clipping.mcpserver.service

import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.model.BudgetSetting
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.LlmRun
import com.clipping.mcpserver.store.BudgetSettingStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.CostAlertNotificationStore
import com.clipping.mcpserver.store.LlmRunStore
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate

class LlmCostServiceTest {

    private val jdbc = mockk<JdbcTemplate>()
    private val properties = ClippingMcpServerProperties(
        llmInputCostPerMillionUsd = 0.30,
        llmOutputCostPerMillionUsd = 2.50
    )
    private val llmRunStore = mockk<LlmRunStore>()
    private val budgetSettingStore = mockk<BudgetSettingStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val costAlertNotificationStore = mockk<CostAlertNotificationStore>()

    private val service = LlmCostService(
        jdbc = jdbc,
        properties = properties,
        llmRunStore = llmRunStore,
        budgetSettingStore = budgetSettingStore,
        categoryStore = categoryStore,
        costAlertNotificationStore = costAlertNotificationStore,
    )

    init {
        every { llmRunStore.sumBillableTokensBetween(any(), any(), any()) } returns (0L to 0L)
    }

    // 테스트용 LlmRun 생성 헬퍼
    private fun llmRun(
        id: String = "run-1",
        categoryId: String = "cat-1",
        model: String = "gpt-4o",
        promptVersion: String = "v1",
        inputChars: Int = 4000,
        outputChars: Int = 2000,
        tokensIn: Int? = 1000,
        tokensOut: Int? = 500,
        status: String = "SUCCEEDED",
        errorMessage: String? = null,
        durationMs: Long = 1200,
        createdAt: Instant = Instant.parse("2026-03-15T10:00:00Z")
    ) = LlmRun(
        id = id,
        categoryId = categoryId,
        model = model,
        promptVersion = promptVersion,
        inputHash = "hash-$id",
        inputChars = inputChars,
        outputChars = outputChars,
        tokensIn = tokensIn,
        tokensOut = tokensOut,
        status = status,
        errorMessage = errorMessage,
        durationMs = durationMs,
        createdAt = createdAt
    )

    @Nested
    inner class `summarizeByChannel 채널별 비용 요약` {

        @Test
        fun `채널별 요약은 DB 집계 row로 비용을 계산한다`() {
            // given: DB가 채널/카테고리별 집계 row만 반환한다
            val from = Instant.parse("2026-03-01T00:00:00Z")
            val to = Instant.parse("2026-03-02T00:00:00Z")

            every {
                jdbc.queryForList(match { it.contains("GROUP BY") }, *anyVararg())
            } returns listOf(
                mapOf(
                    "channel_id" to "",
                    "category_id" to "cat-1",
                    "category_name" to "AI 뉴스",
                    "request_count" to 3L,
                    "tokens_in" to 1_000_000L,
                    "tokens_out" to 100_000L
                )
            )

            // when
            val result = service.summarizeByChannel(from, to)

            // then: 원본 run 목록을 읽지 않고 집계 row만 사용한다
            result.rows shouldHaveSize 1
            result.totalRequestCount shouldBe 3
            result.totalTokensIn shouldBe 1_000_000L
            result.totalTokensOut shouldBe 100_000L
            result.rows[0].channelId shouldBe "(기본 채널)"
            result.rows[0].estimatedUsd shouldBe 0.55
            verify(exactly = 0) { llmRunStore.findByCreatedAtBetween(any(), any(), any()) }
        }
    }

    @Nested
    inner class `getOverview 비용 개요 조회` {

        @Test
        fun `정상 조회 시 일별 비용 분포와 총 비용을 반환한다`() {
            // given: 2일간 각 1건의 LLM 호출 데이터
            val from = LocalDate.of(2026, 3, 14)
            val to = LocalDate.of(2026, 3, 15)

            val run1 = llmRun(
                id = "run-1",
                tokensIn = 1_000_000,
                tokensOut = 100_000,
                createdAt = Instant.parse("2026-03-14T05:00:00Z")
            )
            val run2 = llmRun(
                id = "run-2",
                tokensIn = 500_000,
                tokensOut = 50_000,
                createdAt = Instant.parse("2026-03-15T05:00:00Z")
            )

            // 현재 기간 조회
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns listOf(run1, run2)
            // 예산 설정
            every { budgetSettingStore.get() } returns BudgetSetting(monthlyBudgetUsd = 100.0)

            // when
            val result = service.getOverview(from, to, null)

            // then: 2일간의 일별 분포
            result.dailyBreakdown shouldHaveSize 2
            result.totalRequests shouldBe 2
            result.totalCostUsd shouldBeGreaterThan 0.0
            result.budgetUsd shouldBe 100.0
        }

        @Test
        fun `데이터가 없는 기간은 비용 0으로 채운다`() {
            // given: 빈 데이터
            val from = LocalDate.of(2026, 3, 14)
            val to = LocalDate.of(2026, 3, 16)

            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()
            every { budgetSettingStore.get() } returns BudgetSetting(monthlyBudgetUsd = 0.0)

            // when
            val result = service.getOverview(from, to, null)

            // then: 3일간 모두 비용 0
            result.dailyBreakdown shouldHaveSize 3
            result.totalCostUsd shouldBe 0.0
            result.totalRequests shouldBe 0
            result.budgetUsd shouldBe null
            result.budgetUsedPercent shouldBe null
        }

        @Test
        fun `예산이 0이면 budgetUsd는 null을 반환한다`() {
            // given
            val from = LocalDate.of(2026, 3, 15)
            val to = LocalDate.of(2026, 3, 15)

            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()
            every { budgetSettingStore.get() } returns BudgetSetting(monthlyBudgetUsd = 0.0)

            // when
            val result = service.getOverview(from, to, null)

            // then
            result.budgetUsd shouldBe null
            result.budgetUsedPercent shouldBe null
        }
    }

    @Nested
    inner class `getHourlyBreakdown 시간대별 비용` {

        @Test
        fun `24시간 모든 시간대를 빈 값 포함하여 반환한다`() {
            // given: 특정 시간대에만 데이터가 있는 경우
            val date = LocalDate.of(2026, 3, 15)
            val run = llmRun(
                createdAt = Instant.parse("2026-03-15T01:30:00Z") // KST 10:30
            )

            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns listOf(run)

            // when
            val result = service.getHourlyBreakdown(date, null)

            // then: 0~23시 모두 포함
            result.hours shouldHaveSize 24
            result.date shouldBe date
        }

        @Test
        fun `데이터가 없는 날은 모든 시간대 비용 0이다`() {
            // given
            val date = LocalDate.of(2026, 3, 15)

            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            // when
            val result = service.getHourlyBreakdown(date, null)

            // then
            result.hours shouldHaveSize 24
            result.hours.forEach { hour ->
                hour.totalCostUsd shouldBe 0.0
                hour.requestCount shouldBe 0
            }
        }
    }

    @Nested
    inner class `getModels 모델별 비용 분석` {

        @Test
        fun `여러 모델의 비용 비중을 정확히 계산한다`() {
            // given: 두 모델의 LLM 실행 데이터
            val from = LocalDate.of(2026, 3, 14)
            val to = LocalDate.of(2026, 3, 15)

            val run1 = llmRun(
                id = "run-1", model = "gpt-4o",
                tokensIn = 1_000_000, tokensOut = 100_000,
                createdAt = Instant.parse("2026-03-15T01:00:00Z")
            )
            val run2 = llmRun(
                id = "run-2", model = "gpt-4o-mini",
                tokensIn = 500_000, tokensOut = 50_000,
                createdAt = Instant.parse("2026-03-15T02:00:00Z")
            )

            // 현재 기간 조회
            every {
                llmRunStore.findByCreatedAtBetween(any(), any(), null)
            } returns listOf(run1, run2)
            every { categoryStore.findById(any()) } returns Category(
                id = "cat-1",
                name = "AI 뉴스"
            )

            // when
            val result = service.getModels(from, to, null)

            // then: 2개 모델 존재
            result.modelCount shouldBe 2
            result.models shouldHaveSize 2
            // 비중 합은 100%
            val totalPercent = result.models.sumOf { it.costPercent }
            totalPercent shouldBeGreaterThan 99.0
            totalPercent shouldBeLessThan 101.0
        }

        @Test
        fun `실행 데이터가 없으면 빈 결과를 반환한다`() {
            // given
            val from = LocalDate.of(2026, 3, 14)
            val to = LocalDate.of(2026, 3, 15)

            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            // when
            val result = service.getModels(from, to, null)

            // then
            result.modelCount shouldBe 0
            result.models shouldHaveSize 0
            result.costPerArticleUsd shouldBe 0.0
        }
    }

    @Nested
    inner class `getReliability 안정성 분석` {

        @Test
        fun `성공률과 실패율을 정확히 계산한다`() {
            // given: 성공 3건, 실패 1건, 빈 결과 1건
            val from = LocalDate.of(2026, 3, 15)
            val to = LocalDate.of(2026, 3, 15)

            val runs = listOf(
                llmRun(id = "r1", status = "SUCCEEDED", durationMs = 1000,
                    createdAt = Instant.parse("2026-03-15T01:00:00Z")),
                llmRun(id = "r2", status = "SUCCEEDED", durationMs = 2000,
                    createdAt = Instant.parse("2026-03-15T02:00:00Z")),
                llmRun(id = "r3", status = "SUCCEEDED", durationMs = 3000,
                    createdAt = Instant.parse("2026-03-15T03:00:00Z")),
                llmRun(id = "r4", status = "FAILED", durationMs = 500,
                    errorMessage = "Rate limit exceeded",
                    createdAt = Instant.parse("2026-03-15T04:00:00Z")),
                llmRun(id = "r5", status = "EMPTY_RESULT", durationMs = 800,
                    createdAt = Instant.parse("2026-03-15T05:00:00Z"))
            )

            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns runs
            every { categoryStore.findById(any()) } returns Category(id = "cat-1", name = "AI 뉴스")

            // when
            val result = service.getReliability(from, to, null)

            // then
            result.successRate shouldBe 0.6     // 3/5
            result.failureRate shouldBe 0.2     // 1/5
            result.emptyResultRate shouldBe 0.2 // 1/5
            result.topErrors shouldHaveSize 1
            result.topErrors[0].errorPattern shouldBe "Rate limit exceeded"
            result.topErrors[0].count shouldBe 1
        }

        @Test
        fun `데이터가 없으면 모든 비율이 0이다`() {
            // given
            val from = LocalDate.of(2026, 3, 15)
            val to = LocalDate.of(2026, 3, 15)

            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            // when
            val result = service.getReliability(from, to, null)

            // then
            result.successRate shouldBe 0.0
            result.failureRate shouldBe 0.0
            result.emptyResultRate shouldBe 0.0
            result.avgDurationMs shouldBe 0L
            result.p50DurationMs shouldBe 0L
            result.p95DurationMs shouldBe 0L
            result.topErrors shouldHaveSize 0
        }

        @Test
        fun `에러 메시지가 null인 실패 건은 topErrors에 포함하지 않는다`() {
            // given
            val from = LocalDate.of(2026, 3, 15)
            val to = LocalDate.of(2026, 3, 15)

            val runs = listOf(
                llmRun(id = "r1", status = "FAILED", errorMessage = null,
                    createdAt = Instant.parse("2026-03-15T01:00:00Z")),
                llmRun(id = "r2", status = "FAILED", errorMessage = "",
                    createdAt = Instant.parse("2026-03-15T02:00:00Z"))
            )

            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns runs

            // when
            val result = service.getReliability(from, to, null)

            // then: null 또는 빈 에러 메시지는 topErrors에 포함되지 않는다
            result.topErrors shouldHaveSize 0
            result.failureRate shouldBe 1.0
        }
    }

    @Nested
    inner class `getBudget 예산 조회` {

        @Test
        fun `예산 설정을 정상적으로 조회한다`() {
            // given
            val expected = BudgetSetting(monthlyBudgetUsd = 50.0, alertThresholdPercent = 90)
            every { budgetSettingStore.get() } returns expected

            // when
            val result = service.getBudget()

            // then
            result.monthlyBudgetUsd shouldBe 50.0
            result.alertThresholdPercent shouldBe 90
            verify(exactly = 1) { budgetSettingStore.get() }
        }
    }

    @Nested
    inner class `saveBudget 예산 저장` {

        @Test
        fun `예산 설정을 저장하고 결과를 반환한다`() {
            // given
            val setting = BudgetSetting(monthlyBudgetUsd = 75.0, alertThresholdPercent = 85)
            every { budgetSettingStore.save(setting) } returns setting

            // when
            val result = service.saveBudget(setting)

            // then
            result.monthlyBudgetUsd shouldBe 75.0
            verify(exactly = 1) { budgetSettingStore.save(setting) }
        }
    }
}
