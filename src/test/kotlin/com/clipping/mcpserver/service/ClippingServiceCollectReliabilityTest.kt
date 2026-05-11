package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.OriginalContent
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceLegalBasis
import com.clipping.mcpserver.service.collection.toRssCollectedItem
import com.clipping.mcpserver.service.collection.toRssCollectionSource
import com.clipping.mcpserver.service.port.RssCollectedItem
import com.clipping.mcpserver.service.port.RssCollectionPort
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.OriginalContentStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.RssSourceStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ClippingServiceCollectReliabilityTest {

    @Autowired
    lateinit var clippingService: ClippingService

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var sourceStore: RssSourceStore

    @Autowired
    lateinit var itemStore: RssItemStore

    @MockitoBean
    lateinit var collector: RssCollectionPort

    @MockitoBean
    lateinit var originalContentStore: OriginalContentStore

    @Test
    fun `collect should record source failure reason when collector throws`() {
        val category = categoryStore.save(Category(id = "", name = "CollectFail-${System.nanoTime()}"))
        val source = seedApprovedSource(category.id, "FailingSource")
        val approvedSource = sourceStore.findById(source.id)!!
        doThrow(IllegalStateException("connection timeout"))
            .`when`(collector)
            .collect(approvedSource.toRssCollectionSource(), 24, false)

        val result = clippingService.collect(category.id, 24)

        result.newItems shouldBe 0
        val updated = sourceStore.findById(source.id)!!
        updated.crawlFailCount shouldBe 1
        updated.lastCrawlError.shouldContain("connection timeout")
    }

    @Test
    fun `collect should reset source failure counters after successful collection`() {
        val category = categoryStore.save(Category(id = "", name = "CollectReset-${System.nanoTime()}"))
        val source = seedApprovedSource(category.id, "RecoveringSource")
        val approvedSource = sourceStore.findById(source.id)!!
        sourceStore.incrementFailCount(source.id, "previous failure")

        doReturn(emptyList<RssCollectedItem>())
            .`when`(collector)
            .collect(approvedSource.toRssCollectionSource(), 24, false)

        clippingService.collect(category.id, 24)

        val updated = sourceStore.findById(source.id)!!
        updated.crawlFailCount shouldBe 0
        updated.lastCrawlError shouldBe null
    }

    @Test
    fun `collect should keep saved item even when original content archive fails`() {
        val category = categoryStore.save(Category(id = "", name = "CollectArchive-${System.nanoTime()}"))
        val source = seedApprovedSource(
            categoryId = category.id,
            name = "ArchiveWarningSource",
            fulltextAllowed = true
        )
        val approvedSource = sourceStore.findById(source.id)!!
        val link = "https://example.com/archive-${System.nanoTime()}"
        doReturn(
            listOf(
                RssItem(
                    id = "",
                    title = "Archive resilient item",
                    content = "content",
                    link = link,
                    categoryId = category.id,
                    rssSourceId = source.id
                )
            ).map { it.toRssCollectedItem() }
        )
            .`when`(collector)
            .collect(approvedSource.toRssCollectionSource(), 24, false)
        doAnswer { invocation -> invocation.arguments[0] }
            .`when`(collector)
            .enrichShortContent(
                any(RssCollectedItem::class.java) ?: RssItem(
                    id = "dummy",
                    title = "dummy",
                    link = "https://example.com/dummy",
                    categoryId = category.id
                ).toRssCollectedItem()
            )
        doThrow(IllegalStateException("archive down"))
            .`when`(originalContentStore)
            .save(
                any(OriginalContent::class.java) ?: OriginalContent(
                    id = "",
                    rssItemId = "dummy",
                    sourceLink = "https://example.com/dummy",
                    title = "dummy",
                    markdown = "dummy"
                )
            )

        val result = clippingService.collect(category.id, 24)

        result.newItems shouldBe 1
        result.duplicateSkipped shouldBe 0
        itemStore.findByLink(link, category.id).shouldNotBeNull()
        val updated = sourceStore.findById(source.id)!!
        updated.crawlFailCount shouldBe 0
    }

    /**
     * listApproved 조건을 만족하는 테스트용 소스를 생성한다.
     */
    private fun seedApprovedSource(categoryId: String, name: String, fulltextAllowed: Boolean = false): RssSource {
        val saved = sourceStore.save(
            RssSource(
                id = "",
                name = name,
                url = "https://example.com/${name.lowercase()}-${System.nanoTime()}/rss",
                categoryId = categoryId,
                legalBasis = SourceLegalBasis.LICENSED,
                summaryAllowed = true,
                fulltextAllowed = fulltextAllowed
            )
        )
        sourceStore.updateApproval(saved.id, true, "test-admin")
        sourceStore.updateVerificationStatus(saved.id, "VERIFIED")
        return sourceStore.findById(saved.id)!!
    }
}
