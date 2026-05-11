package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.ClippingStat
import com.ohmyclipping.model.LlmRun
import com.ohmyclipping.model.ReviewDecisionStatus
import com.ohmyclipping.model.ReviewItemDecision
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.LlmRunStore
import com.ohmyclipping.store.ReviewItemDecisionStore
import com.ohmyclipping.store.StatsStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class OperationalKpiServiceTest {

    private val categoryStore = mockk<CategoryStore>()
    private val statsStore = mockk<StatsStore>()
    private val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val llmRunStore = mockk<LlmRunStore>()
    private val properties = ClippingMcpServerProperties(
        llmInputCostPerMillionUsd = 0.30,
        llmOutputCostPerMillionUsd = 2.50
    )

    private val service = OperationalKpiService(
        categoryStore = categoryStore,
        statsStore = statsStore,
        reviewItemDecisionStore = reviewItemDecisionStore,
        batchSummaryStore = batchSummaryStore,
        llmRunStore = llmRunStore,
        properties = properties
    )

    @Nested
    inner class `getDailyKpis 일별 KPI 조회` {

        @Test
        fun `정상 조회 시 일별 KPI를 반환한다`() {
            // given: 2일간의 통계 + 리뷰 + LLM 데이터
            val from = LocalDate.of(2026, 3, 14)
            val to = LocalDate.of(2026, 3, 15)

            val stat = ClippingStat(
                id = "stat-1",
                categoryId = "cat-1",
                statDate = LocalDate.of(2026, 3, 14),
                itemsCollected = 20,
                itemsDuplicates = 3,
                slackSendAttempts = 10,
                slackSendSuccesses = 9
            )

            every { statsStore.findDailyRange(null, from, to) } returns listOf(stat)
            every {
                reviewItemDecisionStore.findReviewedBetween(any(), any(), null)
            } returns emptyList()
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            // when
            val result = service.getDailyKpis(null, from, to)

            // then: 2일분 결과
            result shouldHaveSize 2
            val day1 = result.first { it.statDate == LocalDate.of(2026, 3, 14) }
            day1.itemsCollected shouldBe 20
            day1.itemsDuplicates shouldBe 3
            day1.sendAttempts shouldBe 10
            day1.sendSuccesses shouldBe 9
            day1.sendSuccessRate shouldBe 0.9
            day1.duplicateRate shouldBe 3.0 / 20.0

            // 데이터 없는 날은 0
            val day2 = result.first { it.statDate == LocalDate.of(2026, 3, 15) }
            day2.itemsCollected shouldBe 0
        }

        @Test
        fun `리뷰 리드 타임을 시간 단위로 정확히 계산한다`() {
            // given: summaryCreatedAt -> reviewedAt 간 3600초 (1시간) 차이
            val from = LocalDate.of(2026, 3, 15)
            val to = LocalDate.of(2026, 3, 15)

            val summaryCreatedAt = Instant.parse("2026-03-15T00:00:00Z")
            val reviewedAt = Instant.parse("2026-03-15T01:00:00Z") // 1시간 후

            val decision = ReviewItemDecision(
                summaryId = "sum-1",
                categoryId = "cat-1",
                status = ReviewDecisionStatus.EXCLUDE,
                reviewedAt = reviewedAt
            )

            val summary = BatchSummary(
                id = "sum-1",
                originalTitle = "Test",
                summary = "Test summary",
                sourceLink = "https://example.com",
                categoryId = "cat-1",
                rssItemId = "rss-1",
                createdAt = summaryCreatedAt
            )

            every { statsStore.findDailyRange(null, from, to) } returns emptyList()
            every {
                reviewItemDecisionStore.findReviewedBetween(any(), any(), null)
            } returns listOf(decision)
            every { batchSummaryStore.findByIds(listOf("sum-1")) } returns listOf(summary)
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            // when
            val result = service.getDailyKpis(null, from, to)

            // then: 리뷰 리드타임이 1시간
            result shouldHaveSize 1
            val kpi = result[0]
            kpi.reviewLeadTimeHours shouldBe 1.0
            kpi.excludedCount shouldBe 1
            verify(exactly = 1) { batchSummaryStore.findByIds(listOf("sum-1")) }
            verify(exactly = 0) { batchSummaryStore.findById(any()) }
        }

        @Test
        fun `LLM 비용을 일별로 집계한다`() {
            // given
            val from = LocalDate.of(2026, 3, 15)
            val to = LocalDate.of(2026, 3, 15)

            val run = LlmRun(
                id = "run-1",
                categoryId = "cat-1",
                model = "gpt-4o",
                promptVersion = "v1",
                inputHash = "hash",
                inputChars = 1_000_000,
                outputChars = 100_000,
                tokensIn = 1_000,
                tokensOut = 2_000,
                status = "SUCCEEDED",
                durationMs = 1200,
                createdAt = Instant.parse("2026-03-15T05:00:00Z") // KST 14:00
            )

            every { statsStore.findDailyRange(null, from, to) } returns emptyList()
            every {
                reviewItemDecisionStore.findReviewedBetween(any(), any(), null)
            } returns emptyList()
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns listOf(run)

            // when
            val result = service.getDailyKpis(null, from, to)

            // then: LLM 비용이 0보다 크다
            result shouldHaveSize 1
            result[0].llmEstimatedCostUsd shouldBe 0.0053
        }
    }

    @Nested
    inner class `getDailyKpis 입력 검증` {

        @Test
        fun `to가 from보다 이전이면 InvalidInputException을 던진다`() {
            // given
            val from = LocalDate.of(2026, 3, 15)
            val to = LocalDate.of(2026, 3, 14) // from보다 이전

            // when & then
            val ex = shouldThrow<InvalidInputException> {
                service.getDailyKpis(null, from, to)
            }
            ex.message shouldBe "to must be greater than or equal to from"
        }

        @Test
        fun `기간이 180일을 초과하면 InvalidInputException을 던진다`() {
            // given: 181일 범위
            val from = LocalDate.of(2025, 9, 1)
            val to = LocalDate.of(2026, 3, 15) // 약 196일

            // when & then
            shouldThrow<InvalidInputException> {
                service.getDailyKpis(null, from, to)
            }
        }

        @Test
        fun `존재하지 않는 카테고리 ID를 지정하면 NotFoundException을 던진다`() {
            // given
            val from = LocalDate.of(2026, 3, 14)
            val to = LocalDate.of(2026, 3, 15)

            every { categoryStore.findById("nonexistent") } returns null

            // when & then
            val ex = shouldThrow<NotFoundException> {
                service.getDailyKpis("nonexistent", from, to)
            }
            ex.message shouldBe "Category not found: nonexistent"
        }
    }

    @Nested
    inner class `getDailyKpis 엣지 케이스` {

        @Test
        fun `from과 to가 같은 날이면 1일 분량 KPI를 반환한다`() {
            // given
            val date = LocalDate.of(2026, 3, 15)

            every { statsStore.findDailyRange(null, date, date) } returns emptyList()
            every {
                reviewItemDecisionStore.findReviewedBetween(any(), any(), null)
            } returns emptyList()
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            // when
            val result = service.getDailyKpis(null, date, date)

            // then
            result shouldHaveSize 1
            result[0].statDate shouldBe date
        }

        @Test
        fun `수집 건수가 0이면 noiseRate와 duplicateRate가 0이다`() {
            // given
            val date = LocalDate.of(2026, 3, 15)

            every { statsStore.findDailyRange(null, date, date) } returns emptyList()
            every {
                reviewItemDecisionStore.findReviewedBetween(any(), any(), null)
            } returns emptyList()
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            // when
            val result = service.getDailyKpis(null, date, date)

            // then
            result[0].noiseRate shouldBe 0.0
            result[0].duplicateRate shouldBe 0.0
            result[0].sendSuccessRate shouldBe 0.0
        }

        @Test
        fun `리뷰 리드타임 샘플이 없으면 reviewLeadTimeHours가 0이다`() {
            // given
            val date = LocalDate.of(2026, 3, 15)

            every { statsStore.findDailyRange(null, date, date) } returns emptyList()
            every {
                reviewItemDecisionStore.findReviewedBetween(any(), any(), null)
            } returns emptyList()
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            // when
            val result = service.getDailyKpis(null, date, date)

            // then
            result[0].reviewLeadTimeHours shouldBe 0.0
        }
    }
}
