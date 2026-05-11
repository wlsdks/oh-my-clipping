package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.*
import com.clipping.mcpserver.service.dto.clipping.*
import com.clipping.mcpserver.store.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class AdminTrendSnapshotServiceTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val rssItemStore = mockk<RssItemStore>()
    private val rssSourceStore = mockk<RssSourceStore>()
    private val trendSnapshotStore = mockk<TrendSnapshotStore>()
    private val trendVisualCardStore = mockk<TrendVisualCardStore>()
    private val statsStore = mockk<StatsStore>()
    private val summaryFeedbackStore = mockk<SummaryFeedbackStore>()
    private val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()

    private val service = AdminTrendSnapshotService(
        batchSummaryStore,
        categoryStore,
        rssItemStore,
        rssSourceStore,
        trendSnapshotStore,
        trendVisualCardStore,
        statsStore,
        summaryFeedbackStore,
        reviewItemDecisionStore
    )

    private val now = Instant.now()

    private fun makeSummary(
        id: String,
        categoryId: String = "cat1",
        rssItemId: String = "item-$id",
        title: String = "Title $id",
        keywords: List<String> = listOf("AI", "tech"),
        sourceLink: String = "https://example.com/$id",
        importanceScore: Float = 0.8f,
        createdAt: Instant = now
    ) = BatchSummary(
        id = id,
        originalTitle = title,
        summary = "Summary of $id",
        keywords = keywords,
        importanceScore = importanceScore,
        sourceLink = sourceLink,
        categoryId = categoryId,
        rssItemId = rssItemId,
        createdAt = createdAt
    )

    private val sampleSnapshot = TrendSnapshot(
        id = "snap1",
        periodType = TrendPeriodType.WEEKLY,
        snapshotFrom = LocalDate.of(2026, 3, 13),
        snapshotTo = LocalDate.of(2026, 3, 19),
        categoryId = "cat1",
        categoryName = "AI 뉴스",
        regionType = TrendRegionType.ALL,
        title = "주간 트렌드 | AI 뉴스 | 전체",
        summary = "요약 텍스트",
        keySignals = listOf("AI", "GPT"),
        actionItems = listOf("이번 주 AI 트렌드 점검"),
        sourceCount = 3,
        itemCount = 10,
        status = TrendSnapshotStatus.DRAFT,
        templateType = "DETAILED",
        generatedBy = "admin",
        createdAt = now,
        updatedAt = now
    )

    private val sampleVisualCard = TrendVisualCard(
        id = "card1",
        snapshotId = "snap1",
        cardType = TrendVisualCardType.INFO_CARD,
        title = "카드 제목",
        summary = "카드 요약",
        panels = listOf("1컷: AI", "2컷: GPT", "3컷: 트렌드"),
        reviewStatus = TrendVisualReviewStatus.PENDING,
        createdAt = now,
        updatedAt = now
    )

    @Nested
    inner class `스냅샷 발행` {

        @Test
        fun `DRAFT 스냅샷을 PUBLISHED로 전환한다`() {
            every { trendSnapshotStore.findById("snap1") } returns sampleSnapshot
            val savedSlot = slot<TrendSnapshot>()
            every { trendSnapshotStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.publishSnapshot("snap1", "editor")

            result.status shouldBe TrendSnapshotStatus.PUBLISHED
            savedSlot.captured.status shouldBe TrendSnapshotStatus.PUBLISHED
            savedSlot.captured.generatedBy shouldBe "editor"
        }

        @Test
        fun `존재하지 않는 스냅샷 발행 시 NotFoundException`() {
            every { trendSnapshotStore.findById("missing") } returns null

            shouldThrow<NotFoundException> {
                service.publishSnapshot("missing", "admin")
            }.message shouldContain "missing"
        }

        @Test
        fun `publishedBy가 blank이면 기존 generatedBy를 유지한다`() {
            every { trendSnapshotStore.findById("snap1") } returns sampleSnapshot
            val savedSlot = slot<TrendSnapshot>()
            every { trendSnapshotStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            service.publishSnapshot("snap1", "  ")

            savedSlot.captured.generatedBy shouldBe "admin"
        }
    }

    @Nested
    inner class `스냅샷 목록 조회` {

        @Test
        fun `정상적으로 목록을 반환한다`() {
            every { trendSnapshotStore.list(any(), any(), any(), any(), any()) } returns listOf(sampleSnapshot)

            val result = service.listSnapshots(null, null, null, null, 50)

            result shouldHaveSize 1
            result[0].id shouldBe "snap1"
        }

        @Test
        fun `categoryId가 공백이면 전체 카테고리 목록으로 조회한다`() {
            every { trendSnapshotStore.list(null, null, null, null, 50) } returns listOf(sampleSnapshot)

            val result = service.listSnapshots(null, "   ", null, null, 50)

            result shouldHaveSize 1
            verify(exactly = 1) { trendSnapshotStore.list(null, null, null, null, 50) }
        }

        @Test
        fun `limit이 0이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.listSnapshots(null, null, null, null, 0)
            }
        }

        @Test
        fun `limit이 301이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.listSnapshots(null, null, null, null, 301)
            }
        }

        @Test
        fun `잘못된 periodType 문자열이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.listSnapshots("DAILY", null, null, null, 10)
            }
        }
    }

    @Nested
    inner class `비주얼 카드 생성` {

        @Test
        fun `INFO_CARD 타입은 3개 패널을 생성한다`() {
            every { trendSnapshotStore.findById("snap1") } returns sampleSnapshot
            val cardSlot = slot<TrendVisualCard>()
            every { trendVisualCardStore.save(capture(cardSlot)) } answers { cardSlot.captured.copy(id = "new-card") }

            val result = service.generateVisual("snap1", "INFO_CARD", "designer")

            result.panels shouldHaveSize 3
            result.cardType shouldBe TrendVisualCardType.INFO_CARD
        }

        @Test
        fun `COMIC_4 타입은 4개 패널을 생성한다`() {
            every { trendSnapshotStore.findById("snap1") } returns sampleSnapshot
            val cardSlot = slot<TrendVisualCard>()
            every { trendVisualCardStore.save(capture(cardSlot)) } answers { cardSlot.captured.copy(id = "new-card") }

            val result = service.generateVisual("snap1", "COMIC_4", null)

            result.panels shouldHaveSize 4
            result.cardType shouldBe TrendVisualCardType.COMIC_4
        }

        @Test
        fun `COMIC_8 타입은 8개 패널을 생성한다`() {
            every { trendSnapshotStore.findById("snap1") } returns sampleSnapshot
            val cardSlot = slot<TrendVisualCard>()
            every { trendVisualCardStore.save(capture(cardSlot)) } answers { cardSlot.captured.copy(id = "new-card") }

            val result = service.generateVisual("snap1", "COMIC_8", null)

            result.panels shouldHaveSize 8
        }

        @Test
        fun `존재하지 않는 스냅샷 기반 카드 생성 시 NotFoundException`() {
            every { trendSnapshotStore.findById("gone") } returns null

            shouldThrow<NotFoundException> {
                service.generateVisual("gone", "INFO_CARD", null)
            }
        }

        @Test
        fun `잘못된 카드 타입이면 InvalidInputException`() {
            every { trendSnapshotStore.findById("snap1") } returns sampleSnapshot

            shouldThrow<InvalidInputException> {
                service.generateVisual("snap1", "UNKNOWN_TYPE", null)
            }
        }
    }

    @Nested
    inner class `비주얼 카드 검수` {

        @Test
        fun `승인하면 APPROVED 상태로 전환된다`() {
            every { trendVisualCardStore.findById("card1") } returns sampleVisualCard
            val savedSlot = slot<TrendVisualCard>()
            every { trendVisualCardStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reviewVisualCard("card1", approved = true, "좋습니다", "reviewer", publish = false)

            result.reviewStatus shouldBe TrendVisualReviewStatus.APPROVED
            result.published shouldBe false
            savedSlot.captured.reviewNote shouldBe "좋습니다"
            savedSlot.captured.reviewedBy shouldBe "reviewer"
        }

        @Test
        fun `승인 + publish=true이면 published가 true가 된다`() {
            every { trendVisualCardStore.findById("card1") } returns sampleVisualCard
            val savedSlot = slot<TrendVisualCard>()
            every { trendVisualCardStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reviewVisualCard("card1", approved = true, null, null, publish = true)

            result.reviewStatus shouldBe TrendVisualReviewStatus.APPROVED
            result.published shouldBe true
        }

        @Test
        fun `반려하면 REJECTED이고 publish=true여도 published는 false`() {
            every { trendVisualCardStore.findById("card1") } returns sampleVisualCard
            val savedSlot = slot<TrendVisualCard>()
            every { trendVisualCardStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reviewVisualCard("card1", approved = false, "수정 필요", "reviewer", publish = true)

            result.reviewStatus shouldBe TrendVisualReviewStatus.REJECTED
            result.published shouldBe false
        }

        @Test
        fun `존재하지 않는 카드 검수 시 NotFoundException`() {
            every { trendVisualCardStore.findById("no-card") } returns null

            shouldThrow<NotFoundException> {
                service.reviewVisualCard("no-card", true, null, null, null)
            }
        }
    }

    @Nested
    inner class `비주얼 카드 목록 조회` {

        @Test
        fun `snapshotId로 필터링한다`() {
            every { trendVisualCardStore.listBySnapshotId("snap1", 50) } returns listOf(sampleVisualCard)

            val result = service.listVisualCards("snap1", null, 50)

            result shouldHaveSize 1
        }

        @Test
        fun `snapshotId 없이 전체 조회한다`() {
            every { trendVisualCardStore.list(null, 50) } returns listOf(sampleVisualCard)

            val result = service.listVisualCards(null, null, 50)

            result shouldHaveSize 1
        }

        @Test
        fun `limit 범위 밖이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.listVisualCards(null, null, 0)
            }
        }
    }

    @Nested
    inner class `리포트 발간 후보 조회` {

        @Test
        fun `days 범위 밖이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.listReportReleases(0, null, 10)
            }
            shouldThrow<InvalidInputException> {
                service.listReportReleases(91, null, 10)
            }
        }

        @Test
        fun `limit 범위 밖이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.listReportReleases(7, null, 0)
            }
            shouldThrow<InvalidInputException> {
                service.listReportReleases(7, null, 201)
            }
        }
    }

    @Nested
    inner class `운영 품질 요약` {

        @Test
        fun `days가 6이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.qualitySummary(6)
            }
        }

        @Test
        fun `days가 91이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.qualitySummary(91)
            }
        }

        @Test
        fun `정상 호출 시 추천 사항을 반환한다`() {
            // 빈 데이터로 호출 — 모든 store가 빈 결과를 반환
            every { batchSummaryStore.findByDateRange(any(), any(), null) } returns emptyList()
            every { reviewItemDecisionStore.findBySummaryIds(emptyList()) } returns emptyList()
            every { statsStore.findDailyRange(null, any(), any()) } returns emptyList()
            every { summaryFeedbackStore.findWeeklyHot(any(), any(), 30, null) } returns emptyList()

            val result = service.qualitySummary(7)

            result.days shouldBe 7
            result.itemsCollected shouldBe 0
            result.recommendations shouldHaveSize 1
            result.recommendations[0] shouldContain "안정적"
        }
    }
}
