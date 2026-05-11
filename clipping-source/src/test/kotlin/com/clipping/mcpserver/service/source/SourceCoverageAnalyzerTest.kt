package com.clipping.mcpserver.service.source

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceRegionType
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssSourceStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SourceCoverageAnalyzerTest {
    private val rssSourceStore = mockk<RssSourceStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val analyzer = SourceCoverageAnalyzer(rssSourceStore, categoryStore)

    private fun category(id: String, name: String) = Category(
        id = id,
        name = name,
    )

    private fun source(
        id: String = "src-1",
        categoryId: String = "cat-1",
        region: SourceRegionType = SourceRegionType.DOMESTIC,
        isActive: Boolean = true,
        crawlApproved: Boolean = true,
    ) = RssSource(
        id = id,
        name = "Source $id",
        url = "https://example.com/$id/rss",
        categoryId = categoryId,
        sourceRegion = region,
        isActive = isActive,
        crawlApproved = crawlApproved,
    )

    @Nested
    inner class `analyze 메서드` {

        @Test
        fun `카테고리에 활성 소스가 2개 미만이면 LOW_SOURCE_COUNT HIGH 갭을 반환한다`() {
            every { categoryStore.list() } returns listOf(category("cat-1", "기술"))
            every { rssSourceStore.listByCategoryId("cat-1") } returns listOf(
                source(id = "s1", categoryId = "cat-1")
            )

            val gaps = analyzer.analyze()

            gaps shouldHaveSize 1
            gaps[0].type shouldBe "LOW_SOURCE_COUNT"
            gaps[0].severity shouldBe "HIGH"
            gaps[0].categoryId shouldBe "cat-1"
            gaps[0].categoryName shouldBe "기술"
        }

        @Test
        fun `카테고리에 활성 소스가 0개이면 LOW_SOURCE_COUNT를 반환한다`() {
            every { categoryStore.list() } returns listOf(category("cat-1", "금융"))
            every { rssSourceStore.listByCategoryId("cat-1") } returns emptyList()

            val gaps = analyzer.analyze()

            gaps shouldHaveSize 1
            gaps[0].type shouldBe "LOW_SOURCE_COUNT"
            gaps[0].severity shouldBe "HIGH"
        }

        @Test
        fun `비활성 소스와 미승인 소스는 카운트에서 제외된다`() {
            every { categoryStore.list() } returns listOf(category("cat-1", "기술"))
            every { rssSourceStore.listByCategoryId("cat-1") } returns listOf(
                source(id = "s1", categoryId = "cat-1", isActive = false),
                source(id = "s2", categoryId = "cat-1", crawlApproved = false),
                source(id = "s3", categoryId = "cat-1"),
            )

            val gaps = analyzer.analyze()

            gaps shouldHaveSize 1
            gaps[0].type shouldBe "LOW_SOURCE_COUNT"
        }

        @Test
        fun `3개 이상 소스가 모두 국내이면 REGION_IMBALANCE MEDIUM 갭을 반환한다`() {
            every { categoryStore.list() } returns listOf(category("cat-1", "경제"))
            every { rssSourceStore.listByCategoryId("cat-1") } returns listOf(
                source(id = "s1", region = SourceRegionType.DOMESTIC),
                source(id = "s2", region = SourceRegionType.DOMESTIC),
                source(id = "s3", region = SourceRegionType.DOMESTIC),
            )

            val gaps = analyzer.analyze()

            gaps shouldHaveSize 1
            gaps[0].type shouldBe "REGION_IMBALANCE"
            gaps[0].severity shouldBe "MEDIUM"
        }

        @Test
        fun `3개 이상 소스가 모두 해외이면 REGION_IMBALANCE를 반환한다`() {
            every { categoryStore.list() } returns listOf(category("cat-1", "과학"))
            every { rssSourceStore.listByCategoryId("cat-1") } returns listOf(
                source(id = "s1", region = SourceRegionType.GLOBAL),
                source(id = "s2", region = SourceRegionType.GLOBAL),
                source(id = "s3", region = SourceRegionType.GLOBAL),
            )

            val gaps = analyzer.analyze()

            gaps shouldHaveSize 1
            gaps[0].type shouldBe "REGION_IMBALANCE"
        }

        @Test
        fun `소스가 2개 이상이고 지역이 섞여있으면 갭이 없다`() {
            every { categoryStore.list() } returns listOf(category("cat-1", "기술"))
            every { rssSourceStore.listByCategoryId("cat-1") } returns listOf(
                source(id = "s1", region = SourceRegionType.DOMESTIC),
                source(id = "s2", region = SourceRegionType.GLOBAL),
                source(id = "s3", region = SourceRegionType.DOMESTIC),
            )

            val gaps = analyzer.analyze()

            gaps.shouldBeEmpty()
        }

        @Test
        fun `여러 카테고리에서 갭이 동시에 발생하면 severity 순서로 정렬한다`() {
            every { categoryStore.list() } returns listOf(
                category("cat-1", "기술"),
                category("cat-2", "경제"),
            )
            // cat-1: 소스 1개 → LOW_SOURCE_COUNT (HIGH)
            every { rssSourceStore.listByCategoryId("cat-1") } returns listOf(
                source(id = "s1", categoryId = "cat-1")
            )
            // cat-2: 소스 3개 모두 국내 → REGION_IMBALANCE (MEDIUM)
            every { rssSourceStore.listByCategoryId("cat-2") } returns listOf(
                source(id = "s2", categoryId = "cat-2", region = SourceRegionType.DOMESTIC),
                source(id = "s3", categoryId = "cat-2", region = SourceRegionType.DOMESTIC),
                source(id = "s4", categoryId = "cat-2", region = SourceRegionType.DOMESTIC),
            )

            val gaps = analyzer.analyze()

            gaps shouldHaveSize 2
            gaps[0].severity shouldBe "HIGH"
            gaps[1].severity shouldBe "MEDIUM"
        }

        @Test
        fun `카테고리가 없으면 빈 목록을 반환한다`() {
            every { categoryStore.list() } returns emptyList()

            val gaps = analyzer.analyze()

            gaps.shouldBeEmpty()
        }

        @Test
        fun `소스가 정확히 2개이면 LOW_SOURCE_COUNT 갭이 없다`() {
            every { categoryStore.list() } returns listOf(category("cat-1", "기술"))
            every { rssSourceStore.listByCategoryId("cat-1") } returns listOf(
                source(id = "s1", region = SourceRegionType.DOMESTIC),
                source(id = "s2", region = SourceRegionType.GLOBAL),
            )

            val gaps = analyzer.analyze()

            gaps.shouldBeEmpty()
        }
    }
}
