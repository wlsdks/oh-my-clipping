package com.ohmyclipping.service

import com.ohmyclipping.content.ArticleContentExtractor
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.BatchSummaryCompetitor
import com.ohmyclipping.model.CompetitorWatchlist
import com.ohmyclipping.service.competitor.CompetitorOrganizationSynchronizer
import com.ohmyclipping.service.competitor.CompetitorWatchlistService
import com.ohmyclipping.service.port.RssCollectionPort
import com.ohmyclipping.store.BatchSummaryCompetitorStore
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CompetitorRssFeedStore
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.OriginalContentStore
import com.ohmyclipping.store.BookmarkedArticleStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CompetitorSentimentTest {

    private val watchlistStore = mockk<CompetitorWatchlistStore>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val batchSummaryCompetitorStore = mockk<BatchSummaryCompetitorStore>()
    private val competitorRssFeedStore = mockk<CompetitorRssFeedStore>()
    private val originalContentStore = mockk<OriginalContentStore>()
    private val articleContentExtractor = mockk<ArticleContentExtractor>()
    private val rssFeedCollector = mockk<RssCollectionPort>()
    private val bookmarkedArticleStore = mockk<BookmarkedArticleStore>()
    private val organizationSynchronizer = mockk<CompetitorOrganizationSynchronizer>(relaxed = true)
    private val service = CompetitorWatchlistService(
        watchlistStore, batchSummaryStore,
        batchSummaryCompetitorStore, competitorRssFeedStore,
        originalContentStore, articleContentExtractor,
        rssFeedCollector, bookmarkedArticleStore,
        organizationSynchronizer
    )

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private fun makeCompetitor(
        id: String,
        name: String,
        aliases: List<String>
    ) = CompetitorWatchlist(
        id = id, name = name, aliases = aliases,
        tier = "DIRECT", isActive = true
    )

    private fun makeSummary(
        id: String,
        title: String,
        sentiment: String?,
        daysAgo: Long = 0
    ) = BatchSummary(
        id = id,
        originalTitle = title,
        summary = "Summary of $title",
        sourceLink = "https://example.com/$id",
        importanceScore = 0.5f,
        categoryId = "cat-1",
        rssItemId = "rss-$id",
        sentiment = sentiment,
        createdAt = today.minusDays(daysAgo)
            .atStartOfDay(zone).toInstant().plusSeconds(3600)
    )

    @Nested
    inner class `경쟁사 논조 집계` {

        @Test
        fun `경쟁사 2개와 다양한 sentiment 기사를 올바르게 집계한다`() {
            val compA = makeCompetitor("c1", "회사A", listOf("회사A"))
            val compB = makeCompetitor("c2", "회사B", listOf("회사B"))

            every { watchlistStore.findActive() } returns listOf(compA, compB)
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1", "s2", "s3", "s4", "s5")
            every { batchSummaryStore.findByIds(any()) } returns listOf(
                makeSummary("s1", "회사A 신제품 출시", "POSITIVE"),
                makeSummary("s2", "회사A 실적 하락", "NEGATIVE"),
                makeSummary("s3", "회사A 일반 뉴스", "NEUTRAL"),
                makeSummary("s4", "회사B 성장세", "POSITIVE"),
                makeSummary("s5", "회사B 논란", "NEGATIVE"),
            )
            // s1~s3 -> c1, s4~s5 -> c2
            every { batchSummaryCompetitorStore.findBySummaryIds(any()) } returns listOf(
                BatchSummaryCompetitor("s1", "c1"),
                BatchSummaryCompetitor("s2", "c1"),
                BatchSummaryCompetitor("s3", "c1"),
                BatchSummaryCompetitor("s4", "c2"),
                BatchSummaryCompetitor("s5", "c2")
            )

            val result = service.getCompetitorSentiment(7)

            result.competitors shouldHaveSize 2

            val itemA = result.competitors.first { it.competitorId == "c1" }
            itemA.positive shouldBe 1
            itemA.neutral shouldBe 1
            itemA.negative shouldBe 1
            itemA.total shouldBe 3
            itemA.positiveRate shouldBe (1.0 / 3)

            val itemB = result.competitors.first { it.competitorId == "c2" }
            itemB.positive shouldBe 1
            itemB.negative shouldBe 1
            itemB.total shouldBe 2
            itemB.positiveRate shouldBe 0.5
        }

        @Test
        fun `경쟁사가 없으면 빈 응답을 반환한다`() {
            every { watchlistStore.findActive() } returns emptyList()

            val result = service.getCompetitorSentiment(7)

            result.competitors.shouldBeEmpty()
        }

        @Test
        fun `days가 범위 밖이면 안전한 기간으로 보정한다`() {
            val comp = makeCompetitor("c1", "회사A", listOf("회사A"))
            every { watchlistStore.findActive() } returns listOf(comp)
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns emptyList()

            val result = service.getCompetitorSentiment(-7)

            result.competitors.shouldBeEmpty()
            verify(exactly = 1) {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    listOf("c1"),
                    today.atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant(),
                    500
                )
            }
        }

        @Test
        fun `모든 기사의 sentiment가 null이면 경쟁사가 결과에서 제외된다`() {
            val comp = makeCompetitor("c1", "회사A", listOf("회사A"))

            every { watchlistStore.findActive() } returns listOf(comp)
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1", "s2")
            every { batchSummaryStore.findByIds(any()) } returns listOf(
                makeSummary("s1", "회사A 기사1", null),
                makeSummary("s2", "회사A 기사2", null),
            )
            every { batchSummaryCompetitorStore.findBySummaryIds(any()) } returns listOf(
                BatchSummaryCompetitor("s1", "c1"),
                BatchSummaryCompetitor("s2", "c1")
            )

            val result = service.getCompetitorSentiment(7)

            result.competitors.shouldBeEmpty()
        }

        @Test
        fun `positiveRate는 0_0에서 1_0 사이의 값을 반환한다`() {
            val comp = makeCompetitor("c1", "회사A", listOf("회사A"))

            every { watchlistStore.findActive() } returns listOf(comp)
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1", "s2", "s3")
            every { batchSummaryStore.findByIds(any()) } returns listOf(
                makeSummary("s1", "회사A 좋은 뉴스1", "POSITIVE"),
                makeSummary("s2", "회사A 좋은 뉴스2", "POSITIVE"),
                makeSummary("s3", "회사A 나쁜 뉴스", "NEGATIVE"),
            )
            every { batchSummaryCompetitorStore.findBySummaryIds(any()) } returns listOf(
                BatchSummaryCompetitor("s1", "c1"),
                BatchSummaryCompetitor("s2", "c1"),
                BatchSummaryCompetitor("s3", "c1")
            )

            val result = service.getCompetitorSentiment(7)

            result.competitors shouldHaveSize 1
            val item = result.competitors.first()
            item.positiveRate shouldBeGreaterThan 0.0
            item.positiveRate shouldBeLessThanOrEqual 1.0
        }
    }
}
