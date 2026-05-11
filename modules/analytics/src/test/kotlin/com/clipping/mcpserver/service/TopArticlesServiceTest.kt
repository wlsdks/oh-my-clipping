package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.store.BatchSummaryStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class TopArticlesServiceTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val service = TopArticlesService(batchSummaryStore)

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private fun makeSummary(
        id: String,
        title: String,
        importanceScore: Float = 0.5f,
        categoryId: String = "cat-1",
        keywords: List<String> = emptyList(),
        sentiment: String? = null,
        eventType: String? = null,
        daysAgo: Long = 0
    ) = BatchSummary(
        id = id,
        originalTitle = title,
        summary = "Summary of $title",
        sourceLink = "https://example.com/$id",
        importanceScore = importanceScore,
        categoryId = categoryId,
        rssItemId = "rss-$id",
        keywords = keywords,
        sentiment = sentiment,
        eventType = eventType,
        createdAt = today.minusDays(daysAgo)
            .atStartOfDay(zone).toInstant().plusSeconds(3600)
    )

    @Nested
    inner class `상위 기사 조회` {

        @Test
        fun `중요도 순으로 정렬된 기사를 반환한다`() {
            val summaries = listOf(
                makeSummary("s2", "High importance", importanceScore = 0.9f),
                makeSummary("s3", "Mid importance", importanceScore = 0.6f),
                makeSummary("s1", "Low importance", importanceScore = 0.3f)
            )
            every {
                batchSummaryStore.findTopArticles(any(), any(), isNull(), any(), any(), any(), any())
            } returns summaries

            val result = service.getTopArticles(days = 7, limit = 10)

            result.items shouldHaveSize 3
            result.items[0].importanceScore shouldBe 0.9f
            result.items[1].importanceScore shouldBe 0.6f
            result.items[2].importanceScore shouldBe 0.3f
        }

        @Test
        fun `limit로 결과 수를 제한한다`() {
            val summaries = (1..10).map { i ->
                makeSummary("s$i", "Article $i", importanceScore = i / 10f)
            }
            every {
                batchSummaryStore.findTopArticles(any(), any(), isNull(), any(), any(), any(), any())
            } returns summaries.take(3)

            val result = service.getTopArticles(days = 7, limit = 3)

            result.items shouldHaveSize 3
            verify(exactly = 1) {
                batchSummaryStore.findTopArticles(any(), any(), null, null, null, null, 3)
            }
        }

        @Test
        fun `categoryId 필터를 전달한다`() {
            every {
                batchSummaryStore.findTopArticles(any(), any(), eq("cat-2"), any(), any(), any(), any())
            } returns listOf(
                makeSummary(
                    "s1", "Filtered article",
                    importanceScore = 0.8f, categoryId = "cat-2"
                )
            )

            val result = service.getTopArticles(
                days = 7, limit = 10, categoryId = "cat-2"
            )

            result.items shouldHaveSize 1
            result.items[0].summaryId shouldBe "s1"
        }

        @Test
        fun `기사가 없으면 빈 목록을 반환한다`() {
            every {
                batchSummaryStore.findTopArticles(any(), any(), isNull(), any(), any(), any(), any())
            } returns emptyList()

            val result = service.getTopArticles(days = 7, limit = 10)

            result.items.shouldBeEmpty()
        }

        @Test
        fun `sentiment 필터가 적용된다`() {
            val summaries = listOf(
                makeSummary("s1", "긍정 기사", sentiment = "POSITIVE"),
                makeSummary("s2", "부정 기사", sentiment = "NEGATIVE"),
                makeSummary("s3", "중립 기사", sentiment = "NEUTRAL")
            )
            every {
                batchSummaryStore.findTopArticles(any(), any(), isNull(), eq("POSITIVE"), any(), any(), any())
            } returns summaries.filter { it.sentiment == "POSITIVE" }

            val result = service.getTopArticles(
                days = 7, limit = 10, sentiment = "POSITIVE"
            )

            result.items shouldHaveSize 1
            result.items[0].sentiment shouldBe "POSITIVE"
        }

        @Test
        fun `eventType 필터가 적용된다`() {
            val summaries = listOf(
                makeSummary("s1", "제품 출시", eventType = "PRODUCT_LAUNCH"),
                makeSummary("s2", "투자 유치", eventType = "FUNDING")
            )
            every {
                batchSummaryStore.findTopArticles(any(), any(), isNull(), any(), eq("FUNDING"), any(), any())
            } returns summaries.filter { it.eventType == "FUNDING" }

            val result = service.getTopArticles(
                days = 7, limit = 10, eventType = "FUNDING"
            )

            result.items shouldHaveSize 1
            result.items[0].eventType shouldBe "FUNDING"
        }

        @Test
        fun `keyword 필터가 적용된다`() {
            val summaries = listOf(
                makeSummary(
                    "s1", "AI 기사",
                    keywords = listOf("AI", "머신러닝")
                ),
                makeSummary(
                    "s2", "블록체인 기사",
                    keywords = listOf("블록체인", "암호화폐")
                )
            )
            every {
                batchSummaryStore.findTopArticles(any(), any(), isNull(), any(), any(), eq("ai"), any())
            } returns summaries.filter { it.keywords.any { keyword -> keyword.contains("ai", ignoreCase = true) } }

            val result = service.getTopArticles(
                days = 7, limit = 10, keyword = "ai"
            )

            result.items shouldHaveSize 1
            result.items[0].summaryId shouldBe "s1"
        }

        @Test
        fun `date 필터가 적용된다`() {
            val summaries = listOf(
                makeSummary("s1", "오늘 기사", daysAgo = 0),
                makeSummary("s2", "어제 기사", daysAgo = 1),
                makeSummary("s3", "그제 기사", daysAgo = 2)
            )
            every {
                batchSummaryStore.findTopArticles(any(), any(), isNull(), any(), any(), any(), any())
            } returns listOf(summaries[1])

            val targetDate = today.minusDays(1).toString()
            val result = service.getTopArticles(
                days = 7, limit = 10, date = targetDate
            )

            result.items shouldHaveSize 1
            result.items[0].summaryId shouldBe "s2"
        }

        @Test
        fun `여러 필터를 동시에 적용한다`() {
            val summaries = listOf(
                makeSummary(
                    "s1", "긍정 AI 기사",
                    sentiment = "POSITIVE", eventType = "PRODUCT_LAUNCH",
                    keywords = listOf("AI")
                ),
                makeSummary(
                    "s2", "부정 AI 기사",
                    sentiment = "NEGATIVE", eventType = "PRODUCT_LAUNCH",
                    keywords = listOf("AI")
                ),
                makeSummary(
                    "s3", "긍정 블록체인 기사",
                    sentiment = "POSITIVE", eventType = "FUNDING",
                    keywords = listOf("블록체인")
                )
            )
            every {
                batchSummaryStore.findTopArticles(
                    any(), any(), isNull(), eq("POSITIVE"), eq("PRODUCT_LAUNCH"), any(), any()
                )
            } returns summaries.filter { it.sentiment == "POSITIVE" && it.eventType == "PRODUCT_LAUNCH" }

            val result = service.getTopArticles(
                days = 7, limit = 10,
                sentiment = "POSITIVE", eventType = "PRODUCT_LAUNCH"
            )

            result.items shouldHaveSize 1
            result.items[0].summaryId shouldBe "s1"
        }

        @Test
        fun `translatedTitle이 있으면 우선 사용한다`() {
            val summary = makeSummary(
                "s1", "Original Title", importanceScore = 0.8f
            ).copy(translatedTitle = "번역된 제목")

            every {
                batchSummaryStore.findTopArticles(any(), any(), isNull(), any(), any(), any(), any())
            } returns listOf(summary)

            val result = service.getTopArticles(days = 7, limit = 10)

            result.items[0].title shouldBe "번역된 제목"
        }

        @Test
        fun `날짜 범위가 역전되면 도메인 입력 예외를 던진다`() {
            shouldThrow<InvalidInputException> {
                service.getTopArticlesByRange(
                    fromDate = LocalDate.parse("2026-04-26"),
                    toDate = LocalDate.parse("2026-04-25"),
                )
            }
        }
    }
}
