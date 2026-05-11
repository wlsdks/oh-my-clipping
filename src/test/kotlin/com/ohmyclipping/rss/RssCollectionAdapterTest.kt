package com.ohmyclipping.rss

import com.ohmyclipping.model.Language
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.model.SourceLegalBasis
import com.ohmyclipping.model.SourceRegionType
import com.ohmyclipping.service.collection.toRssCollectedItem
import com.ohmyclipping.service.collection.toRssCollectionSource
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import java.time.Instant

class RssCollectionAdapterTest {

    private val rssFeedCollector = mockk<RssFeedCollector>()
    private val adapter = RssCollectionAdapter(rssFeedCollector)

    @Test
    fun `collect maps port source to app source and returns collected item dto`() {
        val source = rssSource(
            legalBasis = SourceLegalBasis.LICENSED,
            sourceRegion = SourceRegionType.DOMESTIC
        )
        val collected = rssItem(language = Language.KOREAN)
        val sourceSlot = slot<com.ohmyclipping.model.RssSource>()

        every { rssFeedCollector.collect(capture(sourceSlot), 12, false) } returns listOf(collected)

        val result = adapter.collect(source.toRssCollectionSource(), hoursBack = 12, enrichShortContent = false)

        sourceSlot.captured.id shouldBe source.id
        sourceSlot.captured.legalBasis shouldBe SourceLegalBasis.LICENSED
        sourceSlot.captured.sourceRegion shouldBe SourceRegionType.DOMESTIC
        result shouldContainExactly listOf(collected.toRssCollectedItem())
        result.single().language shouldBe Language.KOREAN.name
    }

    @Test
    fun `collectByUrl maps collector items to collected item dto`() {
        val item = rssItem(id = "item-url", link = "https://example.com/url")

        every { rssFeedCollector.collectByUrl("https://example.com/rss", 48) } returns listOf(item)

        val result = adapter.collectByUrl("https://example.com/rss", 48)

        result shouldContainExactly listOf(item.toRssCollectedItem())
    }

    @Test
    fun `enrichShortContent maps collected item dto through existing collector behavior`() {
        val original = rssItem(content = "short")
        val enriched = original.copy(content = "expanded content")
        val itemSlot = slot<RssItem>()

        every { rssFeedCollector.enrichShortContent(capture(itemSlot)) } returns enriched

        val result = adapter.enrichShortContent(original.toRssCollectedItem())

        itemSlot.captured.id shouldBe original.id
        itemSlot.captured.content shouldBe "short"
        result shouldBe enriched.toRssCollectedItem()
    }

    private fun rssSource(
        legalBasis: SourceLegalBasis = SourceLegalBasis.QUOTATION_ONLY,
        sourceRegion: SourceRegionType = SourceRegionType.UNKNOWN
    ) = com.ohmyclipping.model.RssSource(
        id = "src-1",
        name = "Example",
        url = "https://example.com/rss",
        categoryId = "cat-1",
        legalBasis = legalBasis,
        sourceRegion = sourceRegion,
        createdAt = Instant.parse("2026-05-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-05-01T00:00:01Z"),
        systemUpdatedAt = Instant.parse("2026-05-01T00:00:02Z")
    )

    private fun rssItem(
        id: String = "item-1",
        link: String = "https://example.com/article",
        content: String? = "content",
        language: Language = Language.FOREIGN
    ) = RssItem(
        id = id,
        title = "Article",
        content = content,
        link = link,
        publishedAt = Instant.parse("2026-05-01T00:00:00Z"),
        language = language,
        categoryId = "cat-1",
        rssSourceId = "src-1",
        createdAt = Instant.parse("2026-05-01T00:00:03Z")
    )
}
