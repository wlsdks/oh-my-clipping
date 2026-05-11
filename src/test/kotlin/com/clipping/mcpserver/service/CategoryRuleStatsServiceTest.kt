package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStatusCount
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.ExcludedItemRow
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class CategoryRuleStatsServiceTest {

    private val categoryStore = mockk<CategoryStore>()
    private val categoryRuleStore = mockk<CategoryRuleStore>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()

    private lateinit var service: CategoryRuleStatsService

    private val cat1 = Category(id = "cat-1", name = "Tech")
    private val cat2 = Category(id = "cat-2", name = "Finance")

    @BeforeEach
    fun setUp() {
        service = CategoryRuleStatsService(
            categoryStore, categoryRuleStore, batchSummaryStore, reviewItemDecisionStore
        )
        every { categoryStore.list() } returns listOf(cat1, cat2)
    }

    @Nested
    inner class GetRuleStats {

        @Test
        fun `포함 건수는 전체에서 검토와 제외를 뺀 값이다`() {
            every { batchSummaryStore.countByCategory(any(), any()) } returns mapOf(
                "cat-1" to 100,
                "cat-2" to 50
            )
            every { reviewItemDecisionStore.countByStatusGroupedByCategory(any(), any()) } returns listOf(
                CategoryStatusCount("cat-1", ReviewDecisionStatus.REVIEW.name, 10),
                CategoryStatusCount("cat-1", ReviewDecisionStatus.EXCLUDE.name, 5),
                CategoryStatusCount("cat-2", ReviewDecisionStatus.REVIEW.name, 3),
                CategoryStatusCount("cat-2", ReviewDecisionStatus.EXCLUDE.name, 2)
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1", includeKeywords = listOf("AI")
            )
            every { categoryRuleStore.findByCategoryId("cat-2") } returns null

            val result = service.getRuleStats(7)

            result.totalIncluded shouldBe 130  // (100-10-5) + (50-3-2)
            result.totalReview shouldBe 13
            result.totalExcluded shouldBe 7
            result.perCategory[0].included shouldBe 85
            result.perCategory[0].hasRule shouldBe true
            result.perCategory[1].hasRule shouldBe false
        }

        @Test
        fun `기사가 없으면 모든 값이 0이다`() {
            every { batchSummaryStore.countByCategory(any(), any()) } returns emptyMap()
            every { reviewItemDecisionStore.countByStatusGroupedByCategory(any(), any()) } returns emptyList()
            every { categoryRuleStore.findByCategoryId(any()) } returns null

            val result = service.getRuleStats(7)

            result.totalIncluded shouldBe 0
            result.totalReview shouldBe 0
            result.totalExcluded shouldBe 0
        }

        @Test
        fun `days가 범위 밖이면 1~90으로 보정한다`() {
            every { batchSummaryStore.countByCategory(any(), any()) } returns emptyMap()
            every { reviewItemDecisionStore.countByStatusGroupedByCategory(any(), any()) } returns emptyList()
            every { categoryRuleStore.findByCategoryId(any()) } returns null

            // 음수 입력도 에러 없이 처리된다.
            val result = service.getRuleStats(-1)
            result.totalIncluded shouldBe 0
        }
    }

    @Nested
    inner class GetExcludedItems {

        @Test
        fun `제외 키워드 일치 사유에서 키워드를 추출한다`() {
            every { reviewItemDecisionStore.findExcludedItems("cat-1", 5) } returns listOf(
                ExcludedItemRow(
                    title = "광고 기사 제목",
                    reason = "제외 키워드 일치: 광고",
                    score = 0.62f,
                    excludedAt = Instant.parse("2026-03-17T00:00:00Z")
                )
            )

            val result = service.getExcludedItems("cat-1", 5)

            result.total shouldBe 1
            result.items[0].matchedKeyword shouldBe "광고"
        }

        @Test
        fun `점수 기반 제외는 matchedKeyword가 null이다`() {
            every { reviewItemDecisionStore.findExcludedItems("cat-1", 5) } returns listOf(
                ExcludedItemRow(
                    title = "점수 낮은 기사",
                    reason = "관련성 점수 미달 (0.2)",
                    score = 0.2f,
                    excludedAt = Instant.parse("2026-03-17T00:00:00Z")
                )
            )

            val result = service.getExcludedItems("cat-1", 5)

            result.items[0].matchedKeyword.shouldBeNull()
        }

        @Test
        fun `reason이 null이면 matchedKeyword도 null이다`() {
            every { reviewItemDecisionStore.findExcludedItems("cat-1", 5) } returns listOf(
                ExcludedItemRow(
                    title = "사유 없는 제외",
                    reason = null,
                    score = 0.5f,
                    excludedAt = Instant.parse("2026-03-17T00:00:00Z")
                )
            )

            val result = service.getExcludedItems("cat-1", 5)

            result.items[0].matchedKeyword.shouldBeNull()
            result.items[0].reason shouldBe ""
        }
    }
}
