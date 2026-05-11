package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.LlmRun
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.RssSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcLlmRunStoreTest {

    @Autowired lateinit var llmRunStore: LlmRunStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var sourceStore: RssSourceStore
    @Autowired lateinit var jdbc: JdbcTemplate

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        val category = categoryStore.save(Category(id = "", name = "LlmRunCat-${System.nanoTime()}"))
        categoryId = category.id
    }

    @Test
    fun `should save llm run`() {
        val saved = llmRunStore.save(
            LlmRun(
                id = "",
                categoryId = categoryId,
                rssItemId = null,
                model = "gemini-2.5-flash",
                promptVersion = "article.v3",
                inputHash = "abc123",
                inputChars = 120,
                outputChars = 420,
                status = "SUCCEEDED",
                durationMs = 230
            )
        )

        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM llm_runs WHERE id = ? AND category_id = ?",
            Int::class.java,
            saved.id,
            categoryId
        ) ?: 0
        count shouldBe 1
    }

    @Test
    fun `rss item 카테고리와 다른 llm run 저장은 거부한다`() {
        val otherCategoryId = categoryStore.save(
            Category(id = "", name = "LlmRunCat-Other-${System.nanoTime()}")
        ).id
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "llm-run-item-mismatch",
                content = "llm-run-content-mismatch",
                link = "https://93.184.216.34/llm-run-mismatch-${System.nanoTime()}",
                categoryId = categoryId
            )
        )

        assertThrows<DataAccessException> {
            llmRunStore.save(
                LlmRun(
                    id = "",
                    categoryId = otherCategoryId,
                    rssItemId = item.id,
                    model = "gemini-2.5-flash",
                    promptVersion = "screening.v1",
                    inputHash = "mismatch-${System.nanoTime()}",
                    inputChars = 42,
                    outputChars = 7,
                    status = "FAILED",
                    durationMs = 11
                )
            )
        }
    }

    @Test
    fun `sumBillableTokensBetween은 토큰이 있으면 토큰을 쓰고 없으면 문자수로 추정한다`() {
        val otherCategoryId = categoryStore.save(
            Category(id = "", name = "LlmRunCat-Other-${System.nanoTime()}")
        ).id
        llmRunStore.save(
            LlmRun(
                id = "",
                categoryId = categoryId,
                rssItemId = null,
                model = "gemini-2.5-flash",
                promptVersion = "article.v3",
                inputHash = "tokens-explicit-${System.nanoTime()}",
                inputChars = 9999,
                outputChars = 9999,
                tokensIn = 1000,
                tokensOut = 2000,
                status = "SUCCEEDED",
                durationMs = 100
            )
        )
        llmRunStore.save(
            LlmRun(
                id = "",
                categoryId = categoryId,
                rssItemId = null,
                model = "gemini-2.5-flash",
                promptVersion = "article.v3",
                inputHash = "tokens-estimated-${System.nanoTime()}",
                inputChars = 400,
                outputChars = 200,
                tokensIn = null,
                tokensOut = null,
                status = "SUCCEEDED",
                durationMs = 100
            )
        )
        llmRunStore.save(
            LlmRun(
                id = "",
                categoryId = otherCategoryId,
                rssItemId = null,
                model = "gemini-2.5-flash",
                promptVersion = "article.v3",
                inputHash = "tokens-other-${System.nanoTime()}",
                inputChars = 400,
                outputChars = 200,
                tokensIn = 5000,
                tokensOut = 5000,
                status = "SUCCEEDED",
                durationMs = 100
            )
        )

        val from = java.time.Instant.now().minusSeconds(60)
        val to = java.time.Instant.now().plusSeconds(60)

        llmRunStore.sumBillableTokensBetween(from, to, categoryId) shouldBe (1100L to 2050L)
    }

    @Test
    fun `sumTokensBySource는 수동 URL 기사처럼 source가 null인 실행 이력을 제외하고 집계한다`() {
        val source = sourceStore.save(
            RssSource(
                id = "",
                name = "LlmRunSource-${System.nanoTime()}",
                url = "https://example.com/llm-source-${System.nanoTime()}.xml",
                categoryId = categoryId
            )
        )
        val sourcedItem = itemStore.save(
            RssItem(
                id = "",
                title = "source-bound-item",
                content = "content",
                link = "https://example.com/source-bound-${System.nanoTime()}",
                categoryId = categoryId,
                rssSourceId = source.id
            )
        )
        val manualItem = itemStore.save(
            RssItem(
                id = "",
                title = "manual-url-item",
                content = "content",
                link = "https://example.com/manual-url-${System.nanoTime()}",
                categoryId = categoryId,
                rssSourceId = null
            )
        )
        llmRunStore.save(
            LlmRun(
                id = "",
                categoryId = categoryId,
                rssItemId = sourcedItem.id,
                model = "gemini-2.5-flash",
                promptVersion = "article.v3",
                inputHash = "source-bound-${System.nanoTime()}",
                inputChars = 100,
                outputChars = 50,
                tokensIn = 11,
                tokensOut = 7,
                status = "SUCCEEDED",
                durationMs = 100
            )
        )
        llmRunStore.save(
            LlmRun(
                id = "",
                categoryId = categoryId,
                rssItemId = manualItem.id,
                model = "gemini-2.5-flash",
                promptVersion = "article.v3",
                inputHash = "manual-url-${System.nanoTime()}",
                inputChars = 100,
                outputChars = 50,
                tokensIn = 99,
                tokensOut = 99,
                status = "SUCCEEDED",
                durationMs = 100
            )
        )

        val result = llmRunStore.sumTokensBySource(java.time.Instant.now().minusSeconds(60))

        result shouldBe mapOf(source.id to Triple(1, 11L, 7L))
    }
}
