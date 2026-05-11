package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.OriginalContent
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.OriginalContentStore
import com.clipping.mcpserver.store.RssItemStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 수동 purge가 rss_items와 연결된 original_contents를 함께 정리하는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClippingServicePurgeIntegrationTest {

    @Autowired lateinit var clippingService: ClippingService
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var originalContentStore: OriginalContentStore
    @Autowired lateinit var jdbc: JdbcTemplate

    private lateinit var category: Category
    private lateinit var oldItem: RssItem
    private lateinit var recentItem: RssItem

    @BeforeEach
    fun setup() {
        category = categoryStore.save(Category(id = "", name = "PurgeIntegrationCat"))
        oldItem = seedItem("old")
        recentItem = seedItem("recent")

        seedOriginal(oldItem, "old")
        seedOriginal(recentItem, "recent")
        backdateItemAndOriginal(oldItem.id, daysAgo = 45)
    }

    @Test
    fun `dry run은 삭제 후보 수만 반환하고 실제 데이터는 유지한다`() {
        val result = clippingService.purge(category.id, keepDays = 30, dryRun = true)

        result.dryRun shouldBe true
        result.deletedItems shouldBe 1
        result.deletedOriginals shouldBe 1
        itemStore.findByLink(oldItem.link, category.id) shouldNotBe null
        originalContentStore.findByRssItemId(oldItem.id) shouldNotBe null
    }

    @Test
    fun `수동 purge는 오래된 rss item과 연결된 original content를 cascade로 함께 삭제한다`() {
        val result = clippingService.purge(category.id, keepDays = 30, dryRun = false)

        result.dryRun shouldBe false
        result.deletedItems shouldBe 1
        result.deletedOriginals shouldBe 1
        itemStore.findByLink(oldItem.link, category.id) shouldBe null
        originalContentStore.findByRssItemId(oldItem.id) shouldBe null
        itemStore.findByLink(recentItem.link, category.id) shouldNotBe null
        originalContentStore.findByRssItemId(recentItem.id) shouldNotBe null
    }

    private fun seedItem(label: String): RssItem =
        itemStore.save(
            RssItem(
                id = "",
                title = "Purge $label item",
                content = "Purge $label content",
                link = "https://93.184.216.34/purge-$label-${System.nanoTime()}",
                categoryId = category.id
            )
        )

    private fun seedOriginal(item: RssItem, label: String) {
        originalContentStore.save(
            OriginalContent(
                id = "",
                rssItemId = item.id,
                sourceLink = item.link,
                title = "Original $label",
                markdown = "# Original $label\n\nbody"
            )
        )
    }

    private fun backdateItemAndOriginal(itemId: String, daysAgo: Long) {
        val cutoff = Instant.now().minus(daysAgo, ChronoUnit.DAYS)
        val timestamp = Timestamp.from(cutoff)
        jdbc.update(
            "UPDATE rss_items SET created_at = ? WHERE id = ?",
            timestamp,
            itemId
        )
        jdbc.update(
            "UPDATE original_contents SET created_at = ?, updated_at = ? WHERE rss_item_id = ?",
            timestamp,
            timestamp,
            itemId
        )
    }
}
