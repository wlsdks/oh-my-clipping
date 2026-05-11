package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.InvalidStateException
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryStatus
import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceCrawlLog
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerErrorNotifier
import com.clipping.mcpserver.service.collection.ManualUrlCollectionService
import com.clipping.mcpserver.service.collection.OriginalContentArchiver
import com.clipping.mcpserver.service.collection.RobotsPolicyClient
import com.clipping.mcpserver.service.collection.RssSourceCollectionService
import com.clipping.mcpserver.service.collection.SourceCrawlLogRecorder
import com.clipping.mcpserver.service.collection.toRssCollectedItem
import com.clipping.mcpserver.service.collection.toRssCollectionSource
import com.clipping.mcpserver.service.port.CollectionArticleExtractorPort
import com.clipping.mcpserver.service.port.CollectionExtractedArticle
import com.clipping.mcpserver.service.port.CollectionRuntimeSettings
import com.clipping.mcpserver.service.port.CollectionRuntimeSettingsPort
import com.clipping.mcpserver.service.port.CollectionUrlSafetyPort
import com.clipping.mcpserver.service.port.RssCollectionPort
import com.clipping.mcpserver.service.source.VerificationResult
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.OriginalContentStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.RssSourceStore
import com.clipping.mcpserver.store.SourceCrawlLogStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.SocketTimeoutException

/**
 * CollectionService 가 RSS 크롤 시도마다 source_crawl_log 에 기록을 남기는지 검증한다.
 * 관리자 대시보드의 가동률 계산은 이 레코드에 전적으로 의존한다.
 */
class CollectionServiceCrawlLogTest {

    private val categoryStore = mockk<CategoryStore>(relaxed = true)
    private val sourceStore = mockk<RssSourceStore>(relaxed = true)
    private val itemStore = mockk<RssItemStore>(relaxed = true)
    private val originalContentStore = mockk<OriginalContentStore>(relaxed = true)
    private val originalContentArchiver = OriginalContentArchiver(sourceStore, originalContentStore)
    private val collector = mockk<RssCollectionPort>()
    private val crawlLogStore = mockk<SourceCrawlLogStore>(relaxed = true)
    private val crawlLogRecorder = SourceCrawlLogRecorder(crawlLogStore)
    private val statsService = mockk<StatsService>(relaxed = true)
    private val metrics = mockk<ClippingMetrics>(relaxed = true)
    private val collectionRuntimeSettingsPort = mockk<CollectionRuntimeSettingsPort>()
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val urlSafetyValidator = mockk<CollectionUrlSafetyPort>(relaxed = true)
    private val robotsPolicyClient = mockk<RobotsPolicyClient>(relaxed = true)
    private val articleContentExtractor = mockk<CollectionArticleExtractorPort>(relaxed = true)
    private val schedulerErrorNotifier = mockk<SchedulerErrorNotifier>(relaxed = true)
    private val manualUrlCollectionService = ManualUrlCollectionService(
        categoryStore = categoryStore,
        sourceStore = sourceStore,
        itemStore = itemStore,
        originalContentArchiver = originalContentArchiver,
        statsService = statsService,
        metrics = metrics,
        runtimeSettingsPort = collectionRuntimeSettingsPort,
        urlSafetyValidator = urlSafetyValidator,
        robotsPolicyClient = robotsPolicyClient,
        articleContentExtractor = articleContentExtractor
    )
    private val rssSourceCollectionService = RssSourceCollectionService(
        sourceStore = sourceStore,
        itemStore = itemStore,
        collector = collector,
        originalContentArchiver = originalContentArchiver,
        crawlLogRecorder = crawlLogRecorder,
        metrics = metrics,
        schedulerErrorNotifier = schedulerErrorNotifier
    )

    private val service = CollectionService(
        categoryStore = categoryStore,
        sourceStore = sourceStore,
        itemStore = itemStore,
        rssSourceCollectionService = rssSourceCollectionService,
        manualUrlCollectionService = manualUrlCollectionService,
        statsService = statsService,
        runtimeSettingService = runtimeSettingService
    )

    private val category = Category(id = "cat-1", name = "Tech", status = CategoryStatus.ACTIVE)
    private val source = RssSource(
        id = "src-1",
        name = "Example",
        url = "https://example.com/feed",
        categoryId = "cat-1"
    )

    @BeforeEach
    fun setUp() {
        every { runtimeSettingService.current() } returns mockk<RuntimeSettingService.RuntimeSettings>(relaxed = true) {
            every { defaultHoursBack } returns 24
        }
        every { categoryStore.findById("cat-1") } returns category
        every { sourceStore.listApproved("cat-1") } returns listOf(source)
        every { itemStore.findRecentTitles(any(), any(), any()) } returns emptyList()
        every { itemStore.findExistingLinks(any(), any()) } returns emptySet()
        every { collector.enrichShortContent(any()) } answers { firstArg() }
    }

    @Test
    fun `collect는 외부 RSS 호출 중 DB 트랜잭션을 열지 않는다`() {
        val method = CollectionService::class.java.getDeclaredMethod(
            "collect",
            String::class.java,
            Int::class.javaObjectType
        )

        method.getAnnotation(Transactional::class.java) shouldBe null
    }

    @Test
    fun `성공적인 크롤은 success=true 와 수집 개수를 source_crawl_log 에 기록한다`() {
        val items = listOf(
            RssItem(
                id = "i-1", title = "t1", link = "https://example.com/a1",
                content = "c", categoryId = "cat-1", rssSourceId = "src-1"
            ),
            RssItem(
                id = "i-2", title = "t2", link = "https://example.com/a2",
                content = "c", categoryId = "cat-1", rssSourceId = "src-1"
            )
        )
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } returns items.map { it.toRssCollectedItem() }
        every { itemStore.save(any()) } answers { firstArg() }

        val captured = slot<SourceCrawlLog>()
        every { crawlLogStore.save(capture(captured)) } returns Unit

        service.collect("cat-1", null)

        verify(exactly = 1) { crawlLogStore.save(any()) }
        val log = captured.captured
        log.sourceId shouldBe "src-1"
        log.success shouldBe true
        log.errorMessage shouldBe null
        log.articlesFound shouldBe 2
        log.responseTimeMs shouldNotBe null
        log.responseTimeMs!! shouldBeGreaterThanOrEqual 0
    }

    @Test
    fun `크롤 실패는 success=false 와 분류된 에러 메시지를 기록한다`() {
        every {
            collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false)
        } throws SocketTimeoutException("connect timed out")

        val captured = slot<SourceCrawlLog>()
        every { crawlLogStore.save(capture(captured)) } returns Unit

        service.collect("cat-1", null)

        verify(exactly = 1) { crawlLogStore.save(any()) }
        val log = captured.captured
        log.sourceId shouldBe "src-1"
        log.success shouldBe false
        log.articlesFound shouldBe 0
        log.errorMessage!! shouldContain "TIMEOUT"
    }

    @Test
    fun `crawlLogStore 저장 실패는 수집 루프를 중단시키지 않는다`() {
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } returns emptyList()
        every { crawlLogStore.save(any()) } throws
            DataAccessResourceFailureException("connection refused")

        // DB 로그 저장이 실패해도 collect()는 정상 결과를 반환해야 한다.
        val result = service.collect("cat-1", null)

        result.newItems shouldBe 0
        verify(exactly = 1) { crawlLogStore.save(any()) }
    }

    @TestFactory
    fun `collect는 non-positive hoursBack을 수집 시작 전에 거부한다`(): List<DynamicTest> =
        listOf(0, -1, -24).map { invalidHours ->
            DynamicTest.dynamicTest("hoursBack=$invalidHours") {
                clearAllMocks()
                every { runtimeSettingService.current() } returns mockk<RuntimeSettingService.RuntimeSettings>(relaxed = true) {
                    every { defaultHoursBack } returns 24
                }

                shouldThrow<InvalidInputException> {
                    service.collect("cat-1", invalidHours)
                }

                verify(exactly = 0) { categoryStore.findById(any()) }
                verify(exactly = 0) { sourceStore.listApproved(any()) }
                verify(exactly = 0) { collector.collect(any(), any(), any()) }
            }
        }

    @Test
    fun `collect는 기존 링크 중복이면 본문 보강 없이 건너뛴다`() {
        val duplicateItem = RssItem(
            id = "i-duplicate",
            title = "이미 저장된 기사",
            link = "https://example.com/already-saved",
            content = "short",
            categoryId = "cat-1",
            rssSourceId = "src-1"
        )
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } returns listOf(duplicateItem).map { it.toRssCollectedItem() }
        every { itemStore.findExistingLinks(listOf(duplicateItem.link), "cat-1") } returns setOf(duplicateItem.link)

        val result = service.collect("cat-1", null)

        result.newItems shouldBe 0
        result.duplicateSkipped shouldBe 1
        verify(exactly = 0) { collector.enrichShortContent(any()) }
        verify(exactly = 0) { itemStore.save(any()) }
    }

    @Test
    fun `collect는 RSS 결과가 비어 있으면 중복 조회와 본문 보강을 호출하지 않는다`() {
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } returns emptyList()

        val result = service.collect("cat-1", null)

        result.totalCollected shouldBe 0
        result.newItems shouldBe 0
        verify(exactly = 0) { itemStore.findExistingLinks(any(), any()) }
        verify(exactly = 0) { collector.enrichShortContent(any()) }
        verify(exactly = 0) { itemStore.save(any()) }
    }

    @Test
    fun `collect는 같은 RSS 응답 안의 동일 링크를 본문 보강 없이 한 번만 저장한다`() {
        val first = RssItem(
            id = "i-1",
            title = "첫 번째 제목",
            link = "https://example.com/same-link",
            content = "short",
            categoryId = "cat-1",
            rssSourceId = "src-1"
        )
        val second = first.copy(id = "i-2", title = "다른 제목")
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } returns listOf(first, second).map { it.toRssCollectedItem() }
        every { itemStore.findExistingLinks(listOf(first.link, second.link), "cat-1") } returns emptySet()
        every { itemStore.save(first) } returns first

        val result = service.collect("cat-1", null)

        result.newItems shouldBe 1
        result.duplicateSkipped shouldBe 1
        verify(exactly = 1) { collector.enrichShortContent(first.toRssCollectedItem()) }
        verify(exactly = 0) { collector.enrichShortContent(second.toRssCollectedItem()) }
        verify(exactly = 1) { itemStore.save(any()) }
    }

    @Test
    fun `collect는 같은 RSS 응답 안의 유사 제목을 본문 보강 없이 건너뛴다`() {
        val first = RssItem(
            id = "i-1",
            title = "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 발표",
            link = "https://example.com/title-1",
            content = "short",
            categoryId = "cat-1",
            rssSourceId = "src-1"
        )
        val second = RssItem(
            id = "i-2",
            title = "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 속보",
            link = "https://example.com/title-2",
            content = "short",
            categoryId = "cat-1",
            rssSourceId = "src-1"
        )
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } returns listOf(first, second).map { it.toRssCollectedItem() }
        every { itemStore.findExistingLinks(listOf(first.link, second.link), "cat-1") } returns emptySet()
        every { itemStore.save(first) } returns first

        val result = service.collect("cat-1", null)

        result.newItems shouldBe 1
        result.duplicateSkipped shouldBe 1
        verify(exactly = 1) { collector.enrichShortContent(first.toRssCollectedItem()) }
        verify(exactly = 0) { collector.enrichShortContent(second.toRssCollectedItem()) }
        verify(exactly = 1) { itemStore.save(any()) }
    }

    @Test
    fun `collect는 저장 시점 중복 키 경합이면 원본 아카이빙 없이 duplicate로 센다`() {
        val item = RssItem(
            id = "i-race",
            title = "경합 기사",
            link = "https://example.com/race",
            content = "short",
            categoryId = "cat-1",
            rssSourceId = "src-1"
        )
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } returns listOf(item).map { it.toRssCollectedItem() }
        every { itemStore.findExistingLinks(listOf(item.link), "cat-1") } returns emptySet()
        every { itemStore.save(item) } throws DuplicateKeyException("uq_rss_items_link_category")

        val result = service.collect("cat-1", null)

        result.newItems shouldBe 0
        result.duplicateSkipped shouldBe 1
        verify(exactly = 1) { collector.enrichShortContent(item.toRssCollectedItem()) }
        verify(exactly = 0) { originalContentStore.save(any()) }
    }

    @Test
    fun `collect는 한 소스 실패가 같은 카테고리의 다음 소스 수집을 막지 않는다`() {
        val secondSource = source.copy(id = "src-2", name = "Second", url = "https://example.com/feed-2")
        val secondItem = RssItem(
            id = "i-2",
            title = "두 번째 소스 기사",
            link = "https://example.com/from-second-source",
            content = "content",
            categoryId = "cat-1",
            rssSourceId = "src-2"
        )
        every { sourceStore.listApproved("cat-1") } returns listOf(source, secondSource)
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } throws SocketTimeoutException("timeout")
        every { collector.collect(secondSource.toRssCollectionSource(), 24, enrichShortContent = false) } returns listOf(secondItem).map { it.toRssCollectedItem() }
        every { itemStore.findExistingLinks(listOf(secondItem.link), "cat-1") } returns emptySet()
        every { itemStore.save(secondItem) } returns secondItem

        val result = service.collect("cat-1", null)

        result.totalCollected shouldBe 1
        result.newItems shouldBe 1
        verify(exactly = 1) { sourceStore.incrementFailCount("src-1", any()) }
        verify(exactly = 1) { itemStore.save(secondItem) }
    }

    @Test
    fun `collect는 같은 카테고리의 여러 소스가 같은 링크를 내보내면 두 번째 소스는 본문 보강 없이 건너뛴다`() {
        val secondSource = source.copy(id = "src-2", name = "Second", url = "https://example.com/feed-2")
        val firstItem = RssItem(
            id = "i-1",
            title = "공유 기사",
            link = "https://example.com/shared",
            content = "short",
            categoryId = "cat-1",
            rssSourceId = "src-1"
        )
        val secondItem = firstItem.copy(id = "i-2", rssSourceId = "src-2")
        every { sourceStore.listApproved("cat-1") } returns listOf(source, secondSource)
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } returns listOf(firstItem).map { it.toRssCollectedItem() }
        every { collector.collect(secondSource.toRssCollectionSource(), 24, enrichShortContent = false) } returns listOf(secondItem).map { it.toRssCollectedItem() }
        every { itemStore.findExistingLinks(listOf(firstItem.link), "cat-1") } returns emptySet()
        every { itemStore.save(firstItem) } returns firstItem

        val result = service.collect("cat-1", null)

        result.totalCollected shouldBe 2
        result.newItems shouldBe 1
        result.duplicateSkipped shouldBe 1
        verify(exactly = 1) { collector.enrichShortContent(firstItem.toRssCollectedItem()) }
        verify(exactly = 0) { collector.enrichShortContent(secondItem.toRssCollectedItem()) }
        verify(exactly = 1) { itemStore.save(any()) }
    }

    @Test
    fun `collect는 같은 카테고리의 여러 소스가 유사 제목을 내보내면 두 번째 소스는 본문 보강 없이 건너뛴다`() {
        val secondSource = source.copy(id = "src-2", name = "Second", url = "https://example.com/feed-2")
        val firstItem = RssItem(
            id = "i-1",
            title = "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 발표",
            link = "https://example.com/title-source-1",
            content = "short",
            categoryId = "cat-1",
            rssSourceId = "src-1"
        )
        val secondItem = RssItem(
            id = "i-2",
            title = "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 속보",
            link = "https://example.com/title-source-2",
            content = "short",
            categoryId = "cat-1",
            rssSourceId = "src-2"
        )
        every { sourceStore.listApproved("cat-1") } returns listOf(source, secondSource)
        every { collector.collect(source.toRssCollectionSource(), 24, enrichShortContent = false) } returns listOf(firstItem).map { it.toRssCollectedItem() }
        every { collector.collect(secondSource.toRssCollectionSource(), 24, enrichShortContent = false) } returns listOf(secondItem).map { it.toRssCollectedItem() }
        every { itemStore.findExistingLinks(listOf(firstItem.link), "cat-1") } returns emptySet()
        every { itemStore.findExistingLinks(listOf(secondItem.link), "cat-1") } returns emptySet()
        every { itemStore.save(firstItem) } returns firstItem

        val result = service.collect("cat-1", null)

        result.totalCollected shouldBe 2
        result.newItems shouldBe 1
        result.duplicateSkipped shouldBe 1
        verify(exactly = 1) { collector.enrichShortContent(firstItem.toRssCollectedItem()) }
        verify(exactly = 0) { collector.enrichShortContent(secondItem.toRssCollectedItem()) }
        verify(exactly = 1) { itemStore.save(any()) }
    }

    @Test
    fun `addUrl은 저장 경합으로 중복 키가 발생하면 duplicate 결과를 반환한다`() {
        val safeUrl = "https://example.com/articles/1"
        every { urlSafetyValidator.validatePublicHttpUrl(safeUrl) } returns URI(safeUrl)
        every {
            sourceStore.listApproved("cat-1")
        } returns listOf(source.copy(verificationStatus = VerificationResult.VERIFIED.name))
        every { robotsPolicyClient.isAllowed(URI(safeUrl)) } returns true
        every { itemStore.findByLink(safeUrl, "cat-1") } returns null
        every { articleContentExtractor.extract(safeUrl) } returns CollectionExtractedArticle(
            title = "중복 기사",
            content = "본문",
            language = Language.KOREAN.name
        )
        every { collectionRuntimeSettingsPort.currentCollectionSettings() } returns CollectionRuntimeSettings(2000)
        every { itemStore.save(any()) } throws DuplicateKeyException("uq_rss_items_link_category")

        val result = service.addUrl("cat-1", safeUrl)

        result.added shouldBe false
        result.duplicate shouldBe true
        result.sourceLink shouldBe safeUrl
        verify(exactly = 0) { originalContentStore.save(any()) }
    }

    @Test
    fun `addUrl은 추적 파라미터를 제거한 canonical URL로 중복을 확인하고 저장한다`() {
        val rawUrl = "https://example.com/articles/1?utm_source=slack&id=42#comments"
        val canonicalUrl = "https://example.com/articles/1?id=42"
        every { urlSafetyValidator.validatePublicHttpUrl(rawUrl) } returns URI(rawUrl)
        every {
            sourceStore.listApproved("cat-1")
        } returns listOf(source.copy(verificationStatus = VerificationResult.VERIFIED.name))
        every { robotsPolicyClient.isAllowed(URI(canonicalUrl)) } returns true
        every { itemStore.findByLink(canonicalUrl, "cat-1") } returns null
        every { articleContentExtractor.extract(canonicalUrl) } returns CollectionExtractedArticle(
            title = "정규화 기사",
            content = "본문",
            language = Language.KOREAN.name
        )
        every { collectionRuntimeSettingsPort.currentCollectionSettings() } returns CollectionRuntimeSettings(2000)
        val savedSlot = slot<RssItem>()
        every { itemStore.save(capture(savedSlot)) } answers {
            savedSlot.captured.copy(id = "item-canonical")
        }

        val result = service.addUrl("cat-1", rawUrl)

        result.added shouldBe true
        result.sourceLink shouldBe canonicalUrl
        savedSlot.captured.link shouldBe canonicalUrl
    }

    @Test
    fun `addUrl은 canonical URL이 이미 저장되어 있으면 본문 추출 없이 duplicate를 반환한다`() {
        val rawUrl = "https://example.com/articles/1?utm_source=slack&id=42#comments"
        val canonicalUrl = "https://example.com/articles/1?id=42"
        val existingItem = RssItem(
            id = "existing-1",
            title = "기존 기사",
            content = "이미 저장된 본문",
            link = canonicalUrl,
            categoryId = "cat-1",
            rssSourceId = null
        )
        every { urlSafetyValidator.validatePublicHttpUrl(rawUrl) } returns URI(rawUrl)
        every {
            sourceStore.listApproved("cat-1")
        } returns listOf(source.copy(verificationStatus = VerificationResult.VERIFIED.name))
        every { robotsPolicyClient.isAllowed(URI(canonicalUrl)) } returns true
        every { itemStore.findByLink(canonicalUrl, "cat-1") } returns existingItem

        val result = service.addUrl("cat-1", rawUrl)

        result.added shouldBe false
        result.duplicate shouldBe true
        result.sourceLink shouldBe canonicalUrl
        verify(exactly = 0) { articleContentExtractor.extract(any()) }
        verify(exactly = 0) { itemStore.save(any()) }
        verify(exactly = 0) { originalContentStore.save(any()) }
    }

    @Test
    fun `addUrl은 extractor가 null을 반환하면 저장하지 않고 InvalidStateException을 던진다`() {
        val safeUrl = "https://example.com/articles/null-extract"
        every { urlSafetyValidator.validatePublicHttpUrl(safeUrl) } returns URI(safeUrl)
        every {
            sourceStore.listApproved("cat-1")
        } returns listOf(source.copy(verificationStatus = VerificationResult.VERIFIED.name))
        every { robotsPolicyClient.isAllowed(URI(safeUrl)) } returns true
        every { itemStore.findByLink(safeUrl, "cat-1") } returns null
        every { articleContentExtractor.extract(safeUrl) } returns null

        shouldThrow<InvalidStateException> {
            service.addUrl("cat-1", safeUrl)
        }

        verify(exactly = 0) { itemStore.save(any()) }
        verify(exactly = 0) { originalContentStore.save(any()) }
    }

    @Test
    fun `addUrl은 extractor 예외를 InvalidStateException으로 바꾸고 저장하지 않는다`() {
        val safeUrl = "https://example.com/articles/extractor-fail"
        every { urlSafetyValidator.validatePublicHttpUrl(safeUrl) } returns URI(safeUrl)
        every {
            sourceStore.listApproved("cat-1")
        } returns listOf(source.copy(verificationStatus = VerificationResult.VERIFIED.name))
        every { robotsPolicyClient.isAllowed(URI(safeUrl)) } returns true
        every { itemStore.findByLink(safeUrl, "cat-1") } returns null
        every { articleContentExtractor.extract(safeUrl) } throws RuntimeException("network failed")

        shouldThrow<InvalidStateException> {
            service.addUrl("cat-1", safeUrl)
        }

        verify(exactly = 0) { itemStore.save(any()) }
        verify(exactly = 0) { originalContentStore.save(any()) }
    }

    @Test
    fun `addUrl은 robots 차단 URL이면 본문 추출 전에 거부한다`() {
        val safeUrl = "https://example.com/articles/blocked-by-robots"
        every { urlSafetyValidator.validatePublicHttpUrl(safeUrl) } returns URI(safeUrl)
        every {
            sourceStore.listApproved("cat-1")
        } returns listOf(source.copy(verificationStatus = VerificationResult.VERIFIED.name))
        every { robotsPolicyClient.isAllowed(URI(safeUrl)) } returns false

        shouldThrow<InvalidInputException> {
            service.addUrl("cat-1", safeUrl)
        }

        verify(exactly = 0) { itemStore.findByLink(any(), any()) }
        verify(exactly = 0) { articleContentExtractor.extract(any()) }
        verify(exactly = 0) { itemStore.save(any()) }
    }

    @TestFactory
    fun `addUrl allowlist host bad case matrix`(): List<DynamicTest> {
        data class HostCase(
            val name: String,
            val sourceUrl: String,
            val rawUrl: String,
            val allowed: Boolean,
            val canonicalUrl: String = rawUrl
        )

        val cases = listOf(
            HostCase("exact host is allowed", "https://example.com/feed", "https://example.com/news/1", true),
            HostCase("subdomain is allowed", "https://example.com/feed", "https://news.example.com/news/1", true),
            HostCase("uppercase target host is canonicalized", "https://example.com/feed", "https://EXAMPLE.COM/news/1", true, "https://example.com/news/1"),
            HostCase("uppercase source host still matches", "https://EXAMPLE.com/feed", "https://example.com/news/1", true),
            HostCase("default https port is allowed and canonicalized", "https://example.com/feed", "https://example.com:443/news/1", true, "https://example.com/news/1"),
            HostCase("non default target port keeps same host allowed", "https://example.com/feed", "https://example.com:8443/news/1", true),
            HostCase("nested subdomain is allowed", "https://example.com/feed", "https://a.b.example.com/news/1", true),
            HostCase("sibling domain is rejected", "https://example.com/feed", "https://evil-example.com/news/1", false),
            HostCase("suffix attack domain is rejected", "https://example.com/feed", "https://example.com.evil.com/news/1", false),
            HostCase("prefix attack domain is rejected", "https://example.com/feed", "https://notexample.com/news/1", false),
            HostCase("different root domain is rejected", "https://example.com/feed", "https://example.org/news/1", false),
            HostCase("lookalike with extra label before root mismatch is rejected", "https://news.example.com/feed", "https://example.com/news/1", false),
            HostCase("allowed subdomain source allows deeper subdomain", "https://news.example.com/feed", "https://a.news.example.com/news/1", true),
            HostCase("allowed subdomain source rejects parent domain", "https://news.example.com/feed", "https://example.com/news/1", false),
            HostCase("tracking params canonicalized before duplicate check", "https://example.com/feed", "https://example.com/news/1?utm_source=x&id=42#frag", true, "https://example.com/news/1?id=42")
        )

        return cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                clearAllMocks()
                setUp()
                every { urlSafetyValidator.validatePublicHttpUrl(case.rawUrl) } returns URI(case.rawUrl)
                every {
                    sourceStore.listApproved("cat-1")
                } returns listOf(source.copy(url = case.sourceUrl, verificationStatus = VerificationResult.VERIFIED.name))
                every { robotsPolicyClient.isAllowed(URI(case.canonicalUrl)) } returns true
                every { itemStore.findByLink(case.canonicalUrl, "cat-1") } returns null
                every { articleContentExtractor.extract(case.canonicalUrl) } returns CollectionExtractedArticle(
                    title = "허용 기사",
                    content = "본문",
                    language = Language.KOREAN.name
                )
                every { collectionRuntimeSettingsPort.currentCollectionSettings() } returns CollectionRuntimeSettings(2000)
                every { itemStore.save(any()) } answers {
                    firstArg<RssItem>().copy(id = "saved-${case.name.hashCode()}")
                }

                if (case.allowed) {
                    val result = service.addUrl("cat-1", case.rawUrl)
                    result.added shouldBe true
                    result.sourceLink shouldBe case.canonicalUrl
                    verify(exactly = 1) { articleContentExtractor.extract(case.canonicalUrl) }
                    verify(exactly = 1) { itemStore.save(any()) }
                } else {
                    shouldThrow<InvalidInputException> {
                        service.addUrl("cat-1", case.rawUrl)
                    }
                    verify(exactly = 0) { articleContentExtractor.extract(any()) }
                    verify(exactly = 0) { itemStore.save(any()) }
                }
            }
        }
    }
}
