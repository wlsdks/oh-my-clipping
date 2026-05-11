package com.clipping.mcpserver.tool

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.OriginalContent
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceLegalBasis
import com.clipping.mcpserver.service.source.VerificationResult
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.OriginalContentStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.RssSourceStore
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
