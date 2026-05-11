package com.ohmyclipping.service

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.KeywordEntity
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.KeywordEntityStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class KeywordEntityServiceTest {

    private val keywordEntityStore = mockk<KeywordEntityStore>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val service = KeywordEntityService(keywordEntityStore, batchSummaryStore)

    private fun makeSummary(
        id: String,
        keywords: List<String> = emptyList()
    ) = BatchSummary(
        id = id,
        originalTitle = "Article $id",
        summary = "Summary of $id",
        sourceLink = "https://example.com/$id",
        categoryId = "cat-1",
        rssItemId = "rss-$id",
        keywords = keywords,
        createdAt = Instant.now()
    )

    private fun makeEntity(
        keyword: String,
        category: String
    ) = KeywordEntity(
        id = "ke-1",
        keyword = keyword,
        category = category,
        firstSeen = Instant.now()
    )

    @Nested
    inner class `분류된 키워드 조회` {

        @Test
        fun `기간 내 키워드를 엔티티 분류와 매칭한다`() {
            // 기사 2건에서 키워드 수집
            val articles = listOf(
                makeSummary("s1", listOf("MegaCorp", "AI")),
                makeSummary("s2", listOf("MegaCorp", "반도체"))
            )
            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns articles

            // 엔티티 분류 등록 상태
            every {
                keywordEntityStore.findByKeywords(any())
            } returns listOf(
                makeEntity("MegaCorp", "ORG"),
                makeEntity("AI", "TECH")
            )

            val result = service.getClassifiedKeywords(days = 7, categoryId = null)

            result.items shouldHaveSize 3
            // MegaCorp가 2회로 최다 등장
            val topItem = result.items[0]
            topItem.keyword shouldBe "MegaCorp"
            topItem.category shouldBe "ORG"
            topItem.count shouldBe 2

            // AI는 TECH로 분류
            val aiItem = result.items.first { it.keyword == "AI" }
            aiItem.category shouldBe "TECH"
            aiItem.count shouldBe 1
        }

        @Test
        fun `분류 정보가 없는 키워드는 TOPIC으로 폴백한다`() {
            val articles = listOf(
                makeSummary("s1", listOf("미분류키워드"))
            )
            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns articles

            // 분류 정보 없음
            every {
                keywordEntityStore.findByKeywords(any())
            } returns emptyList()

            val result = service.getClassifiedKeywords(days = 7, categoryId = null)

            result.items shouldHaveSize 1
            result.items[0].keyword shouldBe "미분류키워드"
            result.items[0].category shouldBe "TOPIC"
        }

        @Test
        fun `기사가 없으면 빈 목록을 반환한다`() {
            every {
                batchSummaryStore.findByDateRange(any(), any(), isNull())
            } returns emptyList()

            val result = service.getClassifiedKeywords(days = 7, categoryId = null)

            result.items.shouldBeEmpty()
        }

        @Test
        fun `categoryId 필터가 전달되면 해당 카테고리만 조회한다`() {
            every {
                batchSummaryStore.findByDateRange(any(), any(), eq("100"))
            } returns listOf(
                makeSummary("s1", listOf("테스트"))
            )
            every {
                keywordEntityStore.findByKeywords(any())
            } returns emptyList()

            val result = service.getClassifiedKeywords(days = 7, categoryId = 100L)

            result.items shouldHaveSize 1
            // findByDateRange에 categoryId "100"이 전달되었는지 검증
            verify {
                batchSummaryStore.findByDateRange(any(), any(), eq("100"))
            }
        }
    }

    @Nested
    inner class `키워드 수동 분류` {

        @Test
        fun `키워드를 수동 분류할 수 있다`() {
            val entity = makeEntity("MegaCorp", "ORG")
            every {
                keywordEntityStore.upsert("MegaCorp", "ORG")
            } returns entity

            val result = service.classifyKeyword("MegaCorp", "ORG")

            result.keyword shouldBe "MegaCorp"
            result.category shouldBe "ORG"
            result.count shouldBe 0
            verify(exactly = 1) { keywordEntityStore.upsert("MegaCorp", "ORG") }
        }

        @Test
        fun `유효하지 않은 카테고리는 예외를 발생시킨다`() {
            val exception = assertThrows<com.ohmyclipping.error.InvalidInputException> {
                service.classifyKeyword("MegaCorp", "INVALID")
            }
            exception.message shouldBe "유효하지 않은 카테고리: INVALID"
        }
    }
}
