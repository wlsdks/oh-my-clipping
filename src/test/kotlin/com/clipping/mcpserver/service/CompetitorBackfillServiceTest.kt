package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.CompetitorWatchlist
import com.clipping.mcpserver.service.competitor.CompetitorBackfillService
import com.clipping.mcpserver.store.BatchSummaryCompetitorStore
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CompetitorWatchlistStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class CompetitorBackfillServiceTest {

    private val watchlistStore = mockk<CompetitorWatchlistStore>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val junctionStore = mockk<BatchSummaryCompetitorStore>(relaxed = true)
    private val service = CompetitorBackfillService(
        watchlistStore, batchSummaryStore, junctionStore
    )

    private fun makeCompetitor(
        id: String = "comp-1",
        name: String = "AlphaEd",
        aliases: List<String> = listOf("FastCampus")
    ) = CompetitorWatchlist(
        id = id, name = name, aliases = aliases, tier = "DIRECT", isActive = true
    )

    private fun makeSummary(
        id: String,
        originalTitle: String,
        summary: String = "본문 요약"
    ) = BatchSummary(
        id = id,
        originalTitle = originalTitle,
        summary = summary,
        sourceLink = "https://example.com/$id",
        categoryId = "cat-1",
        rssItemId = "rss-$id",
        createdAt = Instant.now()
    )

    @Nested
    inner class `활성 경쟁사가 없는 경우` {

        @Test
        fun `활성 경쟁사가 없으면 0을 반환한다`() {
            every { watchlistStore.findActive() } returns emptyList()

            val result = service.backfill()

            result shouldBe 0
            verify(exactly = 0) { batchSummaryStore.findByKeywordsInRange(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { junctionStore.link(any(), any()) }
        }
    }

    @Nested
    inner class `키워드 매칭으로 junction 생성` {

        @Test
        fun `키워드 매칭으로 junction을 생성한다`() {
            val competitor = makeCompetitor(id = "comp-1", name = "AlphaEd")
            val summary = makeSummary(
                id = "sum-1",
                originalTitle = "AlphaEd, 2024년 AI 강의 대폭 확대"
            )

            every { watchlistStore.findActive() } returns listOf(competitor)
            every {
                batchSummaryStore.findByKeywordsInRange(any(), any(), any(), any(), any())
            } returns listOf(summary)

            val result = service.backfill()

            result shouldBe 1
            verify(exactly = 1) { junctionStore.link("sum-1", "comp-1") }
        }

        @Test
        fun `키워드가 매칭되지 않으면 junction을 생성하지 않는다`() {
            val competitor = makeCompetitor(id = "comp-1", name = "GammaLearn", aliases = emptyList())
            val summary = makeSummary(
                id = "sum-1",
                originalTitle = "AlphaEd, 2024년 AI 강의 대폭 확대"
            )

            every { watchlistStore.findActive() } returns listOf(competitor)
            every {
                batchSummaryStore.findByKeywordsInRange(any(), any(), any(), any(), any())
            } returns listOf(summary)

            val result = service.backfill()

            result shouldBe 0
            verify(exactly = 0) { junctionStore.link(any(), any()) }
        }
    }

    @Nested
    inner class `한 기사가 여러 경쟁사에 매칭되는 경우` {

        @Test
        fun `하나의 기사가 여러 경쟁사에 매칭되면 모두 연결한다`() {
            val competitor1 = makeCompetitor(id = "comp-1", name = "AlphaEd", aliases = emptyList())
            val competitor2 = makeCompetitor(id = "comp-2", name = "GammaLearn", aliases = emptyList())
            // summary 제목에 두 경쟁사 키워드가 모두 포함됨
            val summary = makeSummary(
                id = "sum-1",
                originalTitle = "AlphaEd와 GammaLearn, AI 교육 시장 경쟁 격화"
            )

            every { watchlistStore.findActive() } returns listOf(competitor1, competitor2)
            every {
                batchSummaryStore.findByKeywordsInRange(any(), any(), any(), any(), any())
            } returns listOf(summary)

            val result = service.backfill()

            result shouldBe 2
            verify(exactly = 1) { junctionStore.link("sum-1", "comp-1") }
            verify(exactly = 1) { junctionStore.link("sum-1", "comp-2") }
        }
    }
}
