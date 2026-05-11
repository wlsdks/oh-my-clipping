package com.ohmyclipping.tool

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.OriginalContent
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.model.SourceLegalBasis
import com.ohmyclipping.service.source.VerificationResult
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.OriginalContentStore
import com.ohmyclipping.store.RssItemStore
import com.ohmyclipping.store.RssSourceStore
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@ActiveProfiles("test")
class ClipExportToolTest {

    @Autowired
    lateinit var toolCallbackProvider: ToolCallbackProvider

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var itemStore: RssItemStore

    @Autowired
    lateinit var sourceStore: RssSourceStore

    @Autowired
    lateinit var summaryStore: BatchSummaryStore

    @Autowired
    lateinit var originalContentStore: OriginalContentStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        val category = categoryStore.save(Category(id = "", name = "ExportCat-${System.nanoTime()}"))
        categoryId = category.id
        sourceStore.save(
            RssSource(
                id = "",
                name = "ExportSource-${System.nanoTime()}",
                url = "https://93.184.216.34/feed.xml",
                categoryId = categoryId,
                crawlApproved = true,
                legalBasis = SourceLegalBasis.OPEN_LICENSE,
                fulltextAllowed = true,
                verificationStatus = VerificationResult.VERIFIED.name
            )
        )

        val oldItem = itemStore.save(
            RssItem(
                id = "",
                title = "Old export title",
                content = "old export content",
                link = "https://93.184.216.34/export-old-${System.nanoTime()}",
                categoryId = categoryId
            )
        )
        val oldSummary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = "Old export title",
                translatedTitle = null,
                summary = "Old export summary",
                keywords = listOf("old"),
                importanceScore = 0.4f,
                sourceLink = oldItem.link,
                categoryId = categoryId,
                rssItemId = oldItem.id
            )
        )
        originalContentStore.save(
            OriginalContent(
                id = "",
                rssItemId = oldItem.id,
                sourceLink = oldItem.link,
                title = "Old export title",
                markdown = "# Old export title\n\nOld export markdown"
            )
        )

        val recentItem = itemStore.save(
            RssItem(
                id = "",
                title = "Recent export title",
                content = "recent export content",
                link = "https://93.184.216.34/export-recent-${System.nanoTime()}",
                categoryId = categoryId
            )
        )
        val recentSummary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = "Recent export title",
                translatedTitle = "Recent translated title",
                summary = "Recent export summary",
                keywords = listOf("recent"),
                importanceScore = 0.95f,
                sourceLink = recentItem.link,
                categoryId = categoryId,
                rssItemId = recentItem.id
            )
        )
        originalContentStore.save(
            OriginalContent(
                id = "",
                rssItemId = recentItem.id,
                sourceLink = recentItem.link,
                title = "Recent export title",
                markdown = "# Recent export title\n\nRecent export markdown"
            )
        )

        val oldTimestamp = Timestamp.from(Instant.now().minus(45, ChronoUnit.DAYS))
        val recentTimestamp = Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS))
        jdbc.update("UPDATE batch_summaries SET created_at = ? WHERE id = ?", oldTimestamp, oldSummary.id)
        jdbc.update("UPDATE batch_summaries SET created_at = ? WHERE id = ?", recentTimestamp, recentSummary.id)
    }

    @Test
    fun `admin_export should return recent summaries with original markdown`() {
        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_export" }
        val result = tool.call(
            """
            {"categoryId":"$categoryId","daysBack":7,"includeOriginal":true,"limit":10}
            """.trimIndent()
        )

        result.shouldContain("\"count\":1")
        result.shouldContain("\"daysBack\":7")
        result.shouldContain("Recent translated title")
        result.shouldContain("Recent export markdown")
    }
}
