package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.error.InvalidStateException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.*
import com.clipping.mcpserver.service.dto.clipping.*
import com.clipping.mcpserver.store.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * ClippingService의 digest() 코디네이터 메서드와
 * 리팩터링된 하위 메서드들(fetchAndFilterCandidates, selectDigestItems,
 * buildDigestText, sendDigestToSlack 등)을 간접 검증하는 유닛 테스트.
 */
class ClippingServiceTest {

    private val collectionService = mockk<CollectionService>()
    private val digestService = mockk<DigestService>()
    private val dataLifecycleService = mockk<DataLifecycleService>()
    private val categoryStore = mockk<CategoryStore>()
    private val summaryStore = mockk<BatchSummaryStore>()
    private val categoryOverviewStatsStore = mockk<CategoryOverviewStatsStore>()
    private val summarySearchStore = mockk<SummarySearchStore>()
    private val dailySummaryStore = mockk<DailySummaryStore>()
    private val originalContentStore = mockk<OriginalContentStore>()
    private val personaStore = mockk<PersonaStore>()
    private val itemStore = mockk<RssItemStore>()
    private val summarizer = mockk<LlmSummarizationPort>()
    private val statsService = mockk<StatsService>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val itemSummarizationService = mockk<ItemSummarizationService>()

    private val service = ClippingService(
        collectionService = collectionService,
        digestService = digestService,
        dataLifecycleService = dataLifecycleService,
        categoryStore = categoryStore,
        summaryStore = summaryStore,
        categoryOverviewStatsStore = categoryOverviewStatsStore,
        summarySearchStore = summarySearchStore,
        dailySummaryStore = dailySummaryStore,
        originalContentStore = originalContentStore,
        personaStore = personaStore,
        itemStore = itemStore,
        summarizer = summarizer,
        statsService = statsService,
        runtimeSettingService = runtimeSettingService,
        itemSummarizationService = itemSummarizationService
    )

    // Slack 채널 ID 형식: C/G + 대문자영숫자 8자 이상
    private val validChannelId = "C0123456789"
    private val validOverrideChannelId = "C9876543210"

    private val defaultRuntime = RuntimeSettingService.RuntimeSettings(
        defaultHoursBack = 24,
        summaryInputMaxChars = 5000,
        digestMinImportanceScore = 0.3f,
        digestDefaultMaxItems = 5,
        digestMaxMessageChars = 3500,
        digestItemSummaryMaxChars = 960,
        digestKeywordMaxCount = 6,
        jobWorkerBatchSize = 5,
        jobMaxAttempts = 3,
        jobInitialBackoffSeconds = 30,
        slackBotToken = "xoxb-test-token",
        slackDigestBlockKitTemplate = "",
        slackAutoDigestEnabled = false,
        slackDigestCron = "-",
        slackAutoDigestMaxItems = 5,
        slackAutoDigestUnsentOnly = true,
        slackDailyChannelMessageLimit = 50,
        updatedAt = null
    )

    @BeforeEach
    fun setUp() {
        every { runtimeSettingService.current() } returns defaultRuntime
    }

    // ── 공통 테스트 데이터 팩토리 ──

    private fun testCategory(
        id: String = "cat-1",
        name: String = "Tech News",
        slackChannelId: String? = validChannelId,
        maxItems: Int = 5
    ) = Category(
        id = id,
        name = name,
        slackChannelId = slackChannelId,
        maxItems = maxItems
    )

    /**
     * 중복 제거 로직에 걸리지 않도록 제목/요약/키워드를 모두 고유하게 생성한다.
     */
    private fun testSummary(
        id: String,
        categoryId: String = "cat-1",
        importanceScore: Float = 0.7f,
        createdAt: Instant = Instant.now()
    ): BatchSummary {
        val uniqueTopics = listOf(
            "quantum computing", "blockchain regulation", "solar energy",
            "autonomous vehicles", "gene therapy", "space exploration",
            "cybersecurity trends", "5g infrastructure", "robotic surgery",
            "sustainable agriculture"
        )
        val hash = id.hashCode().toUInt()
        val topicIdx = (hash % uniqueTopics.size.toUInt()).toInt()
        val topic = uniqueTopics[topicIdx]

        return BatchSummary(
            id = id,
            originalTitle = "Unique topic $topic for $id article research",
            translatedTitle = "$topic 관련 $id 기사 연구 분석 결과",
            summary = "$topic 에 대한 심층 분석 보고서입니다. $id 고유의 관점에서 작성되었습니다.",
            keywords = listOf(topic.split(" ").first(), id, "unique-kw-$id"),
            importanceScore = importanceScore,
            sourceLink = "https://example-${id}.com/articles/$id",
            categoryId = categoryId,
            rssItemId = "item-$id",
            createdAt = createdAt
        )
    }

    // ════════════════════════════════════════════
    // digest()
    // ════════════════════════════════════════════

    @Nested
    inner class `digest 메서드 — DigestService 위임 검증` {

        @Test
        fun `digest 호출은 DigestService에 위임한다`() {
            val expected = DigestResult(
                categoryId = "cat-1",
                categoryName = "Test",
                unsentOnly = true,
                totalCandidates = 0,
                selectedCount = 0,
                postedToSlack = false,
                slackChannelId = null,
                slackMessageTs = null,
                markedSentCount = 0,
                digestText = "empty",
                items = emptyList()
            )
            every { digestService.digest("cat-1", null, null, false, null) } returns expected

            val result = service.digest("cat-1", null, null, false, null)

            result shouldBe expected
            verify(exactly = 1) { digestService.digest("cat-1", null, null, false, null) }
        }

        @Test
        fun `sendPreparedDigest 호출은 DigestService에 위임한다`() {
            val preparedDigest = DigestResult(
                categoryId = "cat-1",
                categoryName = "Test",
                unsentOnly = true,
                totalCandidates = 1,
                selectedCount = 1,
                postedToSlack = false,
                slackChannelId = null,
                slackMessageTs = null,
                markedSentCount = 0,
                digestText = "prepared",
                items = listOf(
                    DigestItemResult(
                        summaryId = "sum-1",
                        title = "제목",
                        summary = "요약",
                        keywords = listOf("AI"),
                        importanceScore = 0.9f,
                        whyImportant = "중요",
                        sourceLink = "https://example.com",
                        createdAt = "2026-03-29T09:00:00Z"
                    )
                )
            )
            val expected = preparedDigest.copy(postedToSlack = true, slackMessageTs = "ts-1")
            every { digestService.sendPreparedDigest("cat-1", preparedDigest, validChannelId) } returns expected

            val result = service.sendPreparedDigest("cat-1", preparedDigest, validChannelId)

            result shouldBe expected
            verify(exactly = 1) { digestService.sendPreparedDigest("cat-1", preparedDigest, validChannelId) }
        }

        @Test
        fun `finalizePreparedDigest 호출은 DigestService에 위임한다`() {
            val preparedDigest = DigestResult(
                categoryId = "cat-1",
                categoryName = "Test",
                unsentOnly = true,
                totalCandidates = 1,
                selectedCount = 1,
                postedToSlack = true,
                slackChannelId = validChannelId,
                slackMessageTs = "ts-1",
                markedSentCount = 1,
                digestText = "finalized",
                items = emptyList()
            )
            every { digestService.finalizePreparedDigest("cat-1", preparedDigest) } returns 1

            val count = service.finalizePreparedDigest("cat-1", preparedDigest)

            count shouldBe 1
            verify(exactly = 1) { digestService.finalizePreparedDigest("cat-1", preparedDigest) }
        }
    }

    // ════════════════════════════════════════════
    // getSummaries()
    // ════════════════════════════════════════════

    @Nested
    inner class `getSummaries 메서드` {

        @Test
        fun `존재하지 않는 카테고리이면 NotFoundException을 던진다`() {
            every { categoryStore.findById("no-cat") } returns null

            shouldThrow<NotFoundException> {
                service.getSummaries("no-cat", true)
            }
        }

        @Test
        fun `unsentOnly가 true이면 미발송 요약만 조회한다`() {
            val cat = testCategory()
            every { categoryStore.findById("cat-1") } returns cat
            every { summaryStore.findUnsent("cat-1") } returns emptyList()

            val result = service.getSummaries("cat-1", true)

            result.totalCount shouldBe 0
            verify(exactly = 1) { summaryStore.findUnsent("cat-1") }
            verify(exactly = 0) { summaryStore.findByCategoryId(any()) }
        }

        @Test
        fun `unsentOnly가 false이면 전체 요약을 조회한다`() {
            val cat = testCategory()
            every { categoryStore.findById("cat-1") } returns cat
            every { summaryStore.findByCategoryId("cat-1") } returns emptyList()

            val result = service.getSummaries("cat-1", false)

            result.totalCount shouldBe 0
            verify(exactly = 1) { summaryStore.findByCategoryId("cat-1") }
        }

        @Test
        fun `maxItems에 따라 상위 항목만 반환한다`() {
            val cat = testCategory(maxItems = 2)
            val summaries = (1..5).map {
                testSummary("sum-$it", importanceScore = (it * 0.1f))
            }
            every { categoryStore.findById("cat-1") } returns cat
            every { summaryStore.findUnsent("cat-1") } returns summaries

            val result = service.getSummaries("cat-1", true)

            result.summaries shouldHaveSize 2
            result.totalCount shouldBe 5
        }
    }

    // ════════════════════════════════════════════
    // markSent()
    // ════════════════════════════════════════════

    @Nested
    inner class `markSent 메서드` {

        @Test
        fun `빈 리스트이면 예외를 던진다`() {
            shouldThrow<Exception> {
                service.markSent(emptyList())
            }
        }

        @Test
        fun `정상적인 ID 리스트이면 store에 위임한다`() {
            every { summaryStore.markSent(any()) } just runs

            service.markSent(listOf("id-1", "id-2"))

            verify(exactly = 1) { summaryStore.markSent(listOf("id-1", "id-2")) }
        }
    }

    // ════════════════════════════════════════════
    // searchSummaries()
    // ════════════════════════════════════════════

    @Nested
    inner class `searchSummaries 메서드` {

        @Test
        fun `빈 쿼리이면 예외를 던진다`() {
            shouldThrow<Exception> {
                service.searchSummaries("cat-1", "   ", 10)
            }
        }

        @Test
        fun `존재하지 않는 카테고리이면 NotFoundException을 던진다`() {
            every { categoryStore.findById("no-cat") } returns null

            shouldThrow<NotFoundException> {
                service.searchSummaries("no-cat", "query", 10)
            }
        }

        @Test
        fun `정상 검색 시 결과를 반환한다`() {
            val cat = testCategory()
            val summary = testSummary("sum-search")
            every { categoryStore.findById("cat-1") } returns cat
            every { summarySearchStore.search("cat-1", "tech", 10) } returns listOf(summary)

            val result = service.searchSummaries("cat-1", "tech", 10)

            result.totalCount shouldBe 1
            result.summaries.first().id shouldBe "sum-search"
        }

        @Test
        fun `limit가 100을 초과하면 100으로 제한된다`() {
            val cat = testCategory()
            every { categoryStore.findById("cat-1") } returns cat
            every { summarySearchStore.search("cat-1", "test", 100) } returns emptyList()

            service.searchSummaries("cat-1", "test", 200)

            verify(exactly = 1) { summarySearchStore.search("cat-1", "test", 100) }
        }

        @Test
        fun `날짜 범위 검색은 키워드 조건을 store 쿼리로 함께 전달한다`() {
            val from = LocalDate.of(2026, 4, 1)
            val to = LocalDate.of(2026, 4, 30)
            val summary = testSummary("sum-date-search")
            every {
                summarySearchStore.searchInDateRange(
                    categoryId = null,
                    query = "semiconductor",
                    from = from,
                    to = to,
                    limit = 100
                )
            } returns listOf(summary)

            val result = service.searchSummaries(
                categoryId = null,
                query = "semiconductor",
                fromDate = from,
                toDate = to,
                limit = 200
            )

            result.totalCount shouldBe 1
            result.summaries.first().id shouldBe "sum-date-search"
            verify(exactly = 1) {
                summarySearchStore.searchInDateRange(null, "semiconductor", from, to, 100)
            }
            verify(exactly = 0) {
                summarySearchStore.searchAcrossCategories(any(), any())
            }
        }
    }

    // ════════════════════════════════════════════
    // setRetentionPolicy()
    // ════════════════════════════════════════════

    @Nested
    inner class `setRetentionPolicy 메서드 — DataLifecycleService 위임 검증` {

        @Test
        fun `setRetentionPolicy 호출은 DataLifecycleService에 위임한다`() {
            val expected = RetentionPolicyResult(
                categoryId = "cat-1",
                keepDays = 30,
                isEnabled = true,
                source = "category_policy"
            )
            every { dataLifecycleService.setRetentionPolicy("cat-1", 30, null) } returns expected

            val result = service.setRetentionPolicy("cat-1", 30, null)

            result shouldBe expected
            verify(exactly = 1) { dataLifecycleService.setRetentionPolicy("cat-1", 30, null) }
        }
    }

    @Nested
    inner class `purge 메서드 — DataLifecycleService 위임 검증` {

        @Test
        fun `purge 호출은 DataLifecycleService에 위임한다`() {
            val expected = PurgeResult(
                dryRun = true,
                categoryId = "cat-1",
                keepDays = 14,
                cutoffDate = "2026-03-20",
                deletedSummaries = 2,
                deletedItems = 3,
                deletedOriginals = 1,
                deletedDailySummaries = 4,
                deletedStats = 5
            )
            every { dataLifecycleService.purge("cat-1", null, null) } returns expected

            val result = service.purge("cat-1", keepDays = null, dryRun = null)

            result shouldBe expected
            verify(exactly = 1) { dataLifecycleService.purge("cat-1", null, null) }
        }
    }
}
