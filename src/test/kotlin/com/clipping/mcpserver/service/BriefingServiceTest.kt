package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.DailySummary
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.DailySummaryStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BriefingServiceTest {

    private val dailySummaryStore = mockk<DailySummaryStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val service = BriefingService(dailySummaryStore, categoryStore)

    private val today = LocalDate.now()
    private val cat1 = Category(id = "cat-1", name = "AI/테크")
    private val cat2 = Category(id = "cat-2", name = "정책/규제")

    private val summary1 = DailySummary(
        id = "ds-1",
        title = "오늘의 AI/테크 핵심 요약",
        totalItems = 12,
        summaryDate = today,
        topicKeywords = listOf("AI교육", "리스킬링"),
        overallSummary = "정부가 2026년 디지털 인재 양성 계획을 발표했다.",
        categoryId = "cat-1"
    )

    private val summary2 = DailySummary(
        id = "ds-2",
        title = "오늘의 정책/규제 핵심 요약",
        totalItems = 8,
        summaryDate = today,
        topicKeywords = listOf("규제", "데이터"),
        overallSummary = "데이터 보호법 개정안이 국회에 제출되었다.",
        categoryId = "cat-2"
    )

    @Nested
    inner class `오늘의 브리핑 조회` {

        @Test
        fun `오늘 데이터가 있으면 브리핑 목록을 반환한다`() {
            every { dailySummaryStore.findByDate(today) } returns listOf(summary1, summary2)
            every { categoryStore.list() } returns listOf(cat1, cat2)

            val result = service.getTodayBriefings(null)

            result.briefings shouldHaveSize 2
            result.briefings[0].categoryName shouldBe "AI/테크"
            result.briefings[0].totalItems shouldBe 12
            result.briefings[1].categoryName shouldBe "정책/규제"
        }

        @Test
        fun `데이터가 없으면 빈 목록을 반환한다`() {
            every { dailySummaryStore.findByDate(today) } returns emptyList()
            every { categoryStore.list() } returns listOf(cat1)

            val result = service.getTodayBriefings(null)

            result.briefings.shouldBeEmpty()
        }

        @Test
        fun `카테고리 필터가 있으면 해당 카테고리만 반환한다`() {
            every { dailySummaryStore.findByCategoryAndDate("cat-1", today) } returns summary1
            every { categoryStore.list() } returns listOf(cat1, cat2)

            val result = service.getTodayBriefings("cat-1")

            result.briefings shouldHaveSize 1
            result.briefings[0].categoryId shouldBe "cat-1"
            result.briefings[0].categoryName shouldBe "AI/테크"
        }

        @Test
        fun `카테고리 필터인데 해당 카테고리 데이터가 없으면 빈 목록을 반환한다`() {
            every { dailySummaryStore.findByCategoryAndDate("cat-99", today) } returns null
            every { categoryStore.list() } returns listOf(cat1)

            val result = service.getTodayBriefings("cat-99")

            result.briefings.shouldBeEmpty()
        }
    }
}
