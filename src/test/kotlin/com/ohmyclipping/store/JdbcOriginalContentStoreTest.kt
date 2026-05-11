package com.ohmyclipping.store

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.OriginalContent
import com.ohmyclipping.model.RssItem
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcOriginalContentStoreTest {

    @Autowired lateinit var originalContentStore: OriginalContentStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var itemStore: RssItemStore

    private lateinit var itemId: String

    @BeforeEach
    fun setup() {
        val category = categoryStore.save(Category(id = "", name = "ArchiveStoreCat"))
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "Store test item",
                content = "Store test content",
                link = "https://93.184.216.34/archive-store-${System.nanoTime()}",
                categoryId = category.id
            )
        )
        itemId = item.id
    }

    @Test
    fun `should save and find original content by source link`() {
        val saved = originalContentStore.save(
            OriginalContent(
                id = "",
                rssItemId = itemId,
                sourceLink = "https://93.184.216.34/archive-store-link",
                title = "Archive title",
                markdown = "# Archive title\\n\\nbody"
            )
        )

        val found = originalContentStore.findBySourceLink(saved.sourceLink)
        found?.markdown shouldBe "# Archive title\\n\\nbody"
    }

    @Test
    fun `should find original contents by rss item ids in one store call`() {
        val saved = originalContentStore.save(
            OriginalContent(
                id = "",
                rssItemId = itemId,
                sourceLink = "https://93.184.216.34/archive-store-bulk-rss",
                title = "Archive title",
                markdown = "# Bulk rss"
            )
        )

        val result = originalContentStore.findByRssItemIds(listOf(itemId, "missing"))

        result.keys shouldBe setOf(itemId)
        result[itemId]?.id shouldBe saved.id
    }

    @Test
    fun `should find original contents by source links in one store call`() {
        val saved = originalContentStore.save(
            OriginalContent(
                id = "",
                rssItemId = itemId,
                sourceLink = "https://93.184.216.34/archive-store-bulk-link",
                title = "Archive title",
                markdown = "# Bulk link"
            )
        )

        val result = originalContentStore.findBySourceLinks(listOf(saved.sourceLink, "https://example.com/missing"))

        result.keys shouldBe setOf(saved.sourceLink)
        result[saved.sourceLink]?.id shouldBe saved.id
    }
}
