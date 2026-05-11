package com.ohmyclipping.service

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.store.BatchSummaryStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class KeywordTrendServiceTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val service = KeywordTrendService(batchSummaryStore)

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private fun makeSummary(
        id: String,
        keywords: List<String>,
        daysAgo: Long = 0,
        categoryId: String = "cat-1"
    ) = BatchSummary(
        id = id,
        originalTitle = "Title $id",
        summary = "Summary $id",
        keywords = keywords,
        sourceLink = "https://example.com/$id",
        categoryId = categoryId,
        rssItemId = "rss-$id",
        createdAt = today.minusDays(daysAgo)
            .atStartOfDay(zone).toInstant().plusSeconds(3600)
    )

    @Nested
    inner class `키워드 트렌드 조회` {

        @Test
        fun `키워드가 있으면 트렌드 목록을 반환한다`() {
            val summaries = listOf(
                makeSummary("s1", listOf("AI", "교육"), daysAgo = 0),
                makeSummary("s2", listOf("AI", "규제"), daysAgo = 1),
                makeSummary("s3", listOf("AI", "교육", "투자"), daysAgo = 2)
            )

            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns summaries

            val result = service.getKeywordTrend(days = 7, top = 10, categoryId = null)

            result.period.from shouldBe today.minusDays(6).toString()
            result.period.to shouldBe today.toString()
            result.keywords shouldHaveSize 4
            // AI가 3회로 가장 많다
            result.keywords[0].keyword shouldBe "ai"
            result.keywords[0].totalCount shouldBe 3
        }

        @Test
        fun `데이터가 없으면 빈 키워드 목록을 반환한다`() {
            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns emptyList()

            val result = service.getKeywordTrend(days = 7, top = 10, categoryId = null)

            result.keywords.shouldBeEmpty()
        }

        @Test
        fun `top 파라미터로 키워드 수를 제한한다`() {
            val summaries = listOf(
                makeSummary("s1", listOf("AI", "교육", "규제"), daysAgo = 0),
                makeSummary("s2", listOf("AI", "교육"), daysAgo = 1),
                makeSummary("s3", listOf("AI"), daysAgo = 2)
            )

            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns summaries

            val result = service.getKeywordTrend(days = 7, top = 2, categoryId = null)

            result.keywords shouldHaveSize 2
        }

        @Test
        fun `days와 top이 범위 밖이면 안전한 범위로 보정한다`() {
            val summaries = listOf(
                makeSummary("s1", listOf("AI", "교육"), daysAgo = 0),
                makeSummary("s2", listOf("규제"), daysAgo = 0)
            )

            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns summaries

            val result = service.getKeywordTrend(days = -7, top = -1, categoryId = null)

            result.period.from shouldBe today.toString()
            result.period.to shouldBe today.toString()
            result.keywords shouldHaveSize 1
            result.keywords[0].keyword shouldBe "ai"
        }

        @Test
        fun `카테고리 필터를 전달한다`() {
            every {
                batchSummaryStore.findByDateRange(any(), any(), eq("cat-1"))
            } returns listOf(makeSummary("s1", listOf("AI"), categoryId = "cat-1"))

            val result = service.getKeywordTrend(days = 7, top = 10, categoryId = "cat-1")

            result.keywords shouldHaveSize 1
            result.keywords[0].keyword shouldBe "ai"
        }
    }
}
