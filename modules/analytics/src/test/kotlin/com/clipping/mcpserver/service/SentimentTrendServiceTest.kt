package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.store.BatchSummaryStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class SentimentTrendServiceTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val service = SentimentTrendService(batchSummaryStore)

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private fun makeSummary(
        id: String,
        sentiment: String? = null,
        daysAgo: Long = 0,
        categoryId: String = "cat-1"
    ) = BatchSummary(
        id = id,
        originalTitle = "Title $id",
        summary = "Summary $id",
        keywords = emptyList(),
        sourceLink = "https://example.com/$id",
        categoryId = categoryId,
        rssItemId = "rss-$id",
        sentiment = sentiment,
        createdAt = today.minusDays(daysAgo)
            .atStartOfDay(zone).toInstant().plusSeconds(3600)
    )

    @Nested
    inner class `getSentimentTrend` {

        @Test
        fun `기간 내 일별 논조를 집계한다`() {
            val summaries = listOf(
                makeSummary("s1", "POSITIVE", daysAgo = 0),
                makeSummary("s2", "POSITIVE", daysAgo = 0),
                makeSummary("s3", "NEGATIVE", daysAgo = 0),
                makeSummary("s4", "NEUTRAL", daysAgo = 1),
                makeSummary("s5", "NEGATIVE", daysAgo = 2),
            )

            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns summaries

            val result = service.getSentimentTrend(days = 7, categoryId = null)

            result.period.from shouldBe today.minusDays(6).toString()
            result.period.to shouldBe today.toString()
            result.daily shouldHaveSize 7

            // 오늘(마지막 날짜): positive=2, negative=1
            val todayEntry = result.daily.last()
            todayEntry.date shouldBe today.toString()
            todayEntry.positive shouldBe 2
            todayEntry.negative shouldBe 1
            todayEntry.neutral shouldBe 0
            todayEntry.total shouldBe 3

            // 1일 전: neutral=1
            val yesterday = result.daily[result.daily.size - 2]
            yesterday.neutral shouldBe 1
            yesterday.total shouldBe 1
        }

        @Test
        fun `sentiment가 null인 기사는 제외한다`() {
            val summaries = listOf(
                makeSummary("s1", "POSITIVE", daysAgo = 0),
                makeSummary("s2", null, daysAgo = 0),
                makeSummary("s3", null, daysAgo = 1),
            )

            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns summaries

            val result = service.getSentimentTrend(days = 7, categoryId = null)

            // null sentiment 기사는 제외되어야 한다
            val todayEntry = result.daily.last()
            todayEntry.positive shouldBe 1
            todayEntry.total shouldBe 1

            result.summary.positiveRate shouldBe 1.0
            result.summary.dominantSentiment shouldBe "POSITIVE"
        }

        @Test
        fun `데이터가 없으면 빈 결과를 반환한다`() {
            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns emptyList()

            val result = service.getSentimentTrend(days = 7, categoryId = null)

            result.daily shouldHaveSize 7
            result.daily.all { it.total == 0 } shouldBe true
            result.summary.positiveRate shouldBe 0.0
            result.summary.neutralRate shouldBe 0.0
            result.summary.negativeRate shouldBe 0.0
            result.summary.dominantSentiment shouldBe null
            result.summary.changeFromPrevious shouldBe 0.0
        }

        @Test
        fun `days가 범위 밖이면 안전한 범위로 보정한다`() {
            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns listOf(makeSummary("s1", "POSITIVE", daysAgo = 0))

            val result = service.getSentimentTrend(days = -7, categoryId = null)

            result.period.from shouldBe today.toString()
            result.period.to shouldBe today.toString()
            result.daily shouldHaveSize 1
            result.daily[0].positive shouldBe 1
        }

        @Test
        fun `카테고리 필터를 전달한다`() {
            every {
                batchSummaryStore.findByDateRange(any(), any(), eq("cat-1"))
            } returns listOf(makeSummary("s1", "NEUTRAL", categoryId = "cat-1"))

            val result = service.getSentimentTrend(days = 7, categoryId = "cat-1")

            result.summary.neutralRate shouldBe 1.0
            result.summary.dominantSentiment shouldBe "NEUTRAL"
        }

        @Test
        fun `지배적 논조가 올바르게 판정된다`() {
            val summaries = listOf(
                makeSummary("s1", "NEGATIVE", daysAgo = 0),
                makeSummary("s2", "NEGATIVE", daysAgo = 0),
                makeSummary("s3", "NEGATIVE", daysAgo = 1),
                makeSummary("s4", "POSITIVE", daysAgo = 1),
            )

            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns summaries

            val result = service.getSentimentTrend(days = 7, categoryId = null)

            result.summary.dominantSentiment shouldBe "NEGATIVE"
            result.summary.negativeRate shouldBe 0.75
        }
    }
}
