package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.CompetitorRssFeed
import com.clipping.mcpserver.model.CompetitorWatchlist
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.service.competitor.CompetitorCollectionService
import com.clipping.mcpserver.service.competitor.CompetitorCollectionService.Companion.COMPETITOR_CATEGORY_ID
import com.clipping.mcpserver.service.collection.toRssCollectedItem
import com.clipping.mcpserver.service.port.RssCollectionPort
import com.clipping.mcpserver.store.BatchSummaryCompetitorStore
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CompetitorRssFeedStore
import com.clipping.mcpserver.store.CompetitorWatchlistStore
import com.clipping.mcpserver.store.RssItemStore
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class CompetitorCollectionServiceTest {

    private val watchlistStore = mockk<CompetitorWatchlistStore>()
    private val rssFeedStore = mockk<CompetitorRssFeedStore>()
    private val rssFeedCollector = mockk<RssCollectionPort>()
    private val rssItemStore = mockk<RssItemStore>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val batchSummaryCompetitorStore = mockk<BatchSummaryCompetitorStore>()

    private val jdbc = mockk<org.springframework.jdbc.core.JdbcTemplate>()

    private val service = CompetitorCollectionService(
        watchlistStore, rssFeedStore, rssFeedCollector,
        rssItemStore, batchSummaryStore, batchSummaryCompetitorStore, jdbc
    )

    private fun makeCompetitor(
        id: String = "comp-1",
        name: String = "테스트경쟁사",
        aliases: List<String> = listOf("키워드1", "키워드2"),
        isActive: Boolean = true
    ) = CompetitorWatchlist(
        id = id, name = name, aliases = aliases, isActive = isActive
    )

    private fun makeRssItem(
        id: String = "item-1",
        link: String = "https://example.com/article-1",
        title: String = "테스트 기사",
        content: String? = "기사 본문 내용"
    ) = RssItem(
        id = id, title = title, link = link,
        content = content, categoryId = "", rssSourceId = null
    )

    private fun makeSummary(
        id: String = "summary-1",
        sourceLink: String = "https://example.com/article-1"
    ) = BatchSummary(
        id = id, originalTitle = "기존 기사", summary = "요약",
        sourceLink = sourceLink, categoryId = "cat-1",
        rssItemId = "rss-1", importanceScore = 0.5f
    )

    private fun makeManualFeed(
        id: String = "feed-1",
        competitorId: String = "comp-1",
        feedUrl: String = "https://blog.example.com/rss"
    ) = CompetitorRssFeed(
        id = id, competitorId = competitorId, feedUrl = feedUrl
    )

    /** RssItem 저장 스텁. 저장 시 categoryId를 __competitor__로 덮어쓴 결과를 반환한다. */
    private fun stubRssItemSave() {
        every { rssItemStore.save(any()) } answers { firstArg() }
    }

    /** __competitor__ 카테고리 존재 mock */
    private fun stubCompetitorCategoryExists() {
        every { jdbc.queryForObject("SELECT COUNT(*) FROM batch_categories WHERE id = ?", Int::class.java, "__competitor__") } returns 1
    }

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        io.mockk.clearAllMocks()
        stubCompetitorCategoryExists()
    }

    @Nested
    inner class `collectAll 수집` {

        @Test
        fun `활성 경쟁사의 키워드로 수집한다`() {
            val competitor = makeCompetitor()
            val items = listOf(makeRssItem())
            stubRssItemSave()

            every { watchlistStore.findActive() } returns listOf(competitor)
            every { rssFeedStore.findByCompetitorId("comp-1") } returns emptyList()
            every { rssFeedCollector.collectByUrl(any(), any()) } returns items.map { it.toRssCollectedItem() }
            every { batchSummaryStore.findBySourceLinkAndCategoryId(any(), any()) } returns null
            every { batchSummaryStore.save(any()) } answers { firstArg() }
            every { batchSummaryCompetitorStore.link(any(), any()) } returns Unit

            val results = service.collectAll()

            results shouldHaveSize 1
            results[0].isSuccess shouldBe true
            results[0].newItemCount shouldBe 1

            // Google News URL로 collectByUrl이 호출되었는지 확인한다.
            verify { rssFeedCollector.collectByUrl(match { it.contains("news.google.com") }, any()) }
        }

        @Test
        fun `수동 RSS URL도 함께 수집한다`() {
            val competitor = makeCompetitor()
            val googleItems = listOf(makeRssItem(id = "g-1", link = "https://example.com/google-1"))
            val manualItems = listOf(makeRssItem(id = "m-1", link = "https://example.com/manual-1"))
            val manualFeed = makeManualFeed()
            stubRssItemSave()

            every { watchlistStore.findActive() } returns listOf(competitor)
            every { rssFeedStore.findByCompetitorId("comp-1") } returns listOf(manualFeed)
            every { rssFeedCollector.collectByUrl(match { it.contains("news.google.com") }, any()) } returns googleItems.map { it.toRssCollectedItem() }
            every { rssFeedCollector.collectByUrl(match { it.contains("blog.example.com") }, any()) } returns manualItems.map { it.toRssCollectedItem() }
            every { batchSummaryStore.findBySourceLinkAndCategoryId(any(), any()) } returns null
            every { batchSummaryStore.save(any()) } answers { firstArg() }
            every { batchSummaryCompetitorStore.link(any(), any()) } returns Unit

            val results = service.collectAll()

            results[0].newItemCount shouldBe 2

            // Google News와 수동 RSS 두 번 모두 호출되었는지 확인한다.
            verify(exactly = 1) { rssFeedCollector.collectByUrl(match { it.contains("news.google.com") }, any()) }
            verify(exactly = 1) { rssFeedCollector.collectByUrl(match { it.contains("blog.example.com") }, any()) }
        }

        @Test
        fun `Google News와 수동 RSS가 같은 링크를 반환하면 한 번만 저장하고 연결한다`() {
            val competitor = makeCompetitor()
            val duplicateLink = "https://example.com/duplicate-competitor"
            val googleItems = listOf(makeRssItem(id = "g-1", link = duplicateLink, title = "Google 기사"))
            val manualItems = listOf(makeRssItem(id = "m-1", link = duplicateLink, title = "Manual 기사"))
            val manualFeed = makeManualFeed()
            stubRssItemSave()

            every { watchlistStore.findActive() } returns listOf(competitor)
            every { rssFeedStore.findByCompetitorId("comp-1") } returns listOf(manualFeed)
            every { rssFeedCollector.collectByUrl(match { it.contains("news.google.com") }, any()) } returns googleItems.map { it.toRssCollectedItem() }
            every { rssFeedCollector.collectByUrl(match { it.contains("blog.example.com") }, any()) } returns manualItems.map { it.toRssCollectedItem() }
            every { batchSummaryStore.findBySourceLinkAndCategoryId(duplicateLink, COMPETITOR_CATEGORY_ID) } returns null
            every { batchSummaryStore.save(any()) } answers { firstArg() }
            every { batchSummaryCompetitorStore.link(any(), any()) } returns Unit

            val results = service.collectAll()

            results[0].newItemCount shouldBe 1
            results[0].linkedItemCount shouldBe 0
            verify(exactly = 1) { rssItemStore.save(match { it.link == duplicateLink }) }
            verify(exactly = 1) { batchSummaryStore.save(match { it.sourceLink == duplicateLink }) }
            verify(exactly = 1) { batchSummaryCompetitorStore.link(any(), "comp-1") }
        }

        @Test
        fun `동일 실행 안의 기존 기사 중복 링크는 junction을 한 번만 연결한다`() {
            val competitor = makeCompetitor()
            val duplicateLink = "https://example.com/existing-duplicate"
            val existingSummary = makeSummary(id = "sum-existing", sourceLink = duplicateLink)

            every { watchlistStore.findActive() } returns listOf(competitor)
            every { rssFeedStore.findByCompetitorId("comp-1") } returns listOf(makeManualFeed())
            every { rssFeedCollector.collectByUrl(match { it.contains("news.google.com") }, any()) } returns
                listOf(makeRssItem(id = "g-1", link = duplicateLink)).map { it.toRssCollectedItem() }
            every { rssFeedCollector.collectByUrl(match { it.contains("blog.example.com") }, any()) } returns
                listOf(makeRssItem(id = "m-1", link = duplicateLink)).map { it.toRssCollectedItem() }
            every { batchSummaryStore.findBySourceLinkAndCategoryId(duplicateLink, COMPETITOR_CATEGORY_ID) } returns existingSummary
            every { batchSummaryCompetitorStore.link(any(), any()) } returns Unit

            val results = service.collectAll()

            results[0].newItemCount shouldBe 0
            results[0].linkedItemCount shouldBe 1
            verify(exactly = 0) { rssItemStore.save(any()) }
            verify(exactly = 0) { batchSummaryStore.save(any()) }
            verify(exactly = 1) { batchSummaryCompetitorStore.link("sum-existing", "comp-1") }
        }

        @Test
        fun `비활성 경쟁사는 건너뛴다`() {
            every { watchlistStore.findActive() } returns emptyList()

            val results = service.collectAll()

            results shouldHaveSize 0
            verify(exactly = 0) { rssFeedCollector.collectByUrl(any(), any()) }
        }

        @Test
        fun `기존 기사는 junction만 추가한다`() {
            val competitor = makeCompetitor()
            val existingLink = "https://example.com/existing"
            val items = listOf(makeRssItem(link = existingLink))
            val existingSummary = makeSummary(id = "sum-1", sourceLink = existingLink)
            stubRssItemSave()

            every { watchlistStore.findActive() } returns listOf(competitor)
            every { rssFeedStore.findByCompetitorId("comp-1") } returns emptyList()
            every { rssFeedCollector.collectByUrl(any(), any()) } returns items.map { it.toRssCollectedItem() }
            every { batchSummaryStore.findBySourceLinkAndCategoryId(existingLink, COMPETITOR_CATEGORY_ID) } returns existingSummary
            every { batchSummaryCompetitorStore.link(any(), any()) } returns Unit

            val results = service.collectAll()

            results[0].linkedItemCount shouldBe 1
            results[0].newItemCount shouldBe 0

            // junction link가 호출되었는지 확인한다.
            verify(exactly = 1) { batchSummaryCompetitorStore.link("sum-1", "comp-1") }
            // BatchSummary 저장은 호출되지 않아야 한다.
            verify(exactly = 0) { batchSummaryStore.save(any()) }
        }

        @Test
        fun `새 기사는 RssItem을 먼저 저장하고 BatchSummary를 생성한다`() {
            val competitor = makeCompetitor()
            val items = listOf(makeRssItem(
                link = "https://example.com/new-article",
                title = "새 경쟁사 기사",
                content = "기사 본문 내용입니다"
            ))

            val rssItemSlot = slot<RssItem>()
            every { rssItemStore.save(capture(rssItemSlot)) } answers { firstArg() }
            every { watchlistStore.findActive() } returns listOf(competitor)
            every { rssFeedStore.findByCompetitorId("comp-1") } returns emptyList()
            every { rssFeedCollector.collectByUrl(any(), any()) } returns items.map { it.toRssCollectedItem() }
            every { batchSummaryStore.findBySourceLinkAndCategoryId(any(), any()) } returns null

            val savedSlot = slot<BatchSummary>()
            every { batchSummaryStore.save(capture(savedSlot)) } answers { firstArg() }
            every { batchSummaryCompetitorStore.link(any(), any()) } returns Unit

            val results = service.collectAll()

            results[0].newItemCount shouldBe 1

            // RssItem이 __competitor__ 카테고리로 먼저 저장되었는지 확인한다.
            verify(exactly = 1) { rssItemStore.save(any()) }
            rssItemSlot.captured.categoryId shouldBe "__competitor__"

            // BatchSummary가 RSS 데이터로 올바르게 생성되었는지 확인한다.
            val saved = savedSlot.captured
            saved.originalTitle shouldBe "새 경쟁사 기사"
            saved.summary shouldBe "기사 본문 내용입니다"
            saved.sourceLink shouldBe "https://example.com/new-article"
            saved.categoryId shouldBe "__competitor__"
            saved.importanceScore shouldBe 0.5f
            saved.isSentToSlack shouldBe false
            saved.id.shouldNotBeBlank()

            // junction link도 호출되었는지 확인한다.
            verify(exactly = 1) { batchSummaryCompetitorStore.link(any(), "comp-1") }
        }

        @Test
        fun `항상 경쟁사 전용 카테고리 ID를 사용한다`() {
            val competitor = makeCompetitor()
            val items = listOf(makeRssItem())

            stubRssItemSave()
            every { watchlistStore.findActive() } returns listOf(competitor)
            every { rssFeedStore.findByCompetitorId("comp-1") } returns emptyList()
            every { rssFeedCollector.collectByUrl(any(), any()) } returns items.map { it.toRssCollectedItem() }
            every { batchSummaryStore.findBySourceLinkAndCategoryId(any(), any()) } returns null

            val savedSlot = slot<BatchSummary>()
            every { batchSummaryStore.save(capture(savedSlot)) } answers { firstArg() }
            every { batchSummaryCompetitorStore.link(any(), any()) } returns Unit

            service.collectAll()

            // 항상 __competitor__ 카테고리를 사용해야 한다.
            savedSlot.captured.categoryId shouldBe "__competitor__"
        }

        @Test
        fun `RSS content가 null이면 빈 summary를 저장한다`() {
            val competitor = makeCompetitor()
            val items = listOf(makeRssItem(content = null))
            stubRssItemSave()

            every { watchlistStore.findActive() } returns listOf(competitor)
            every { rssFeedStore.findByCompetitorId("comp-1") } returns emptyList()
            every { rssFeedCollector.collectByUrl(any(), any()) } returns items.map { it.toRssCollectedItem() }
            every { batchSummaryStore.findBySourceLinkAndCategoryId(any(), any()) } returns null

            val savedSlot = slot<BatchSummary>()
            every { batchSummaryStore.save(capture(savedSlot)) } answers { firstArg() }
            every { batchSummaryCompetitorStore.link(any(), any()) } returns Unit

            service.collectAll()

            savedSlot.captured.summary shouldBe ""
        }

        @Test
        fun `수집 실패 시 다른 경쟁사는 계속 처리한다`() {
            val failing = makeCompetitor(id = "fail-1", name = "실패경쟁사", aliases = listOf("실패"))
            val succeeding = makeCompetitor(id = "ok-1", name = "성공경쟁사", aliases = listOf("성공"))
            val okItems = listOf(makeRssItem(id = "ok-item", link = "https://example.com/ok"))
            stubRssItemSave()

            every { watchlistStore.findActive() } returns listOf(failing, succeeding)
            every { rssFeedStore.findByCompetitorId("fail-1") } returns emptyList()
            every { rssFeedStore.findByCompetitorId("ok-1") } returns emptyList()
            // 첫 번째 경쟁사 수집은 예외를 던진다.
            every {
                rssFeedCollector.collectByUrl(match { it.contains("%EC%8B%A4%ED%8C%A8") }, any())
            } throws RuntimeException("네트워크 오류")
            // 두 번째 경쟁사 수집은 성공한다.
            every {
                rssFeedCollector.collectByUrl(match { it.contains("%EC%84%B1%EA%B3%B5") }, any())
            } returns okItems.map { it.toRssCollectedItem() }
            every { batchSummaryStore.findBySourceLinkAndCategoryId(any(), any()) } returns null
            every { batchSummaryStore.save(any()) } answers { firstArg() }
            every { batchSummaryCompetitorStore.link(any(), any()) } returns Unit

            val results = service.collectAll()

            results shouldHaveSize 2
            results[0].isSuccess shouldBe false
            results[0].error shouldBe "네트워크 오류"
            results[1].isSuccess shouldBe true
            results[1].newItemCount shouldBe 1
        }

        @Test
        fun `시스템 카테고리 COUNT 결과가 null이어도 카테고리를 생성하고 수집을 계속한다`() {
            every {
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM batch_categories WHERE id = ?",
                    Int::class.java,
                    COMPETITOR_CATEGORY_ID
                )
            } returns null
            every { jdbc.update(any<String>(), COMPETITOR_CATEGORY_ID) } returns 1
            every { watchlistStore.findActive() } returns listOf(makeCompetitor())
            every { rssFeedStore.findByCompetitorId("comp-1") } returns emptyList()
            every { rssFeedCollector.collectByUrl(any(), any()) } returns emptyList()

            val results = service.collectAll()

            results shouldHaveSize 1
            results[0].isSuccess shouldBe true
            val sqlSlot = slot<String>()
            verify(exactly = 1) { jdbc.update(capture(sqlSlot), COMPETITOR_CATEGORY_ID) }
            sqlSlot.captured.contains("INSERT INTO batch_categories") shouldBe true
        }

        @TestFactory
        fun `non-positive hoursBack은 경쟁사 조회 전에 거부한다`(): List<DynamicTest> =
            listOf(0, -1, -24).map { invalidHours ->
                DynamicTest.dynamicTest("hoursBack=$invalidHours") {
                    val thrown = assertThrows<InvalidInputException> {
                        service.collectAll(invalidHours)
                    }

                    thrown.message shouldBe "hoursBack must be greater than 0"
                    verify(exactly = 0) { watchlistStore.findActive() }
                    verify(exactly = 0) { rssFeedCollector.collectByUrl(any(), any()) }
                    verify(exactly = 0) { jdbc.queryForObject(any<String>(), eq(Int::class.java), *anyVararg()) }
                }
            }
    }
}
