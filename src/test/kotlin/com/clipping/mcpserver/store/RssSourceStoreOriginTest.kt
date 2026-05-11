package com.clipping.mcpserver.store

import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RssSourceStoreOriginTest(
    @Autowired private val store: RssSourceStore,
    @Autowired private val jdbc: JdbcTemplate
) {
    /** Raw JDBC seed — avoid JPA flush issues within @Transactional test. */
    private fun insertCategory(slug: String): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO batch_categories(id, name, created_at, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            id, "rss-origin-test-$slug"
        )
        return id
    }

    @Test
    fun `findByCategoryIdAndOrigin 필터`() {
        val cid = insertCategory("a")
        store.insert(
            id = UUID.randomUUID().toString(), categoryId = cid,
            sourceUrl = "https://example.com/auto", sourceName = "auto source",
            origin = "auto_generated"
        )
        store.insert(
            id = UUID.randomUUID().toString(), categoryId = cid,
            sourceUrl = "https://example.com/manual", sourceName = "manual source",
            origin = "manual"
        )
        store.findByCategoryIdAndOrigin(cid, "auto_generated") shouldHaveSize 1
        store.findByCategoryIdAndOrigin(cid, "manual") shouldHaveSize 1
    }

    @Test
    fun `existsByCategoryIdAndUrl`() {
        val cid = insertCategory("b")
        store.insert(
            id = UUID.randomUUID().toString(), categoryId = cid,
            sourceUrl = "https://example.com/x", sourceName = "x",
            origin = "manual"
        )
        store.existsByCategoryIdAndUrl(cid, "https://example.com/x") shouldBe true
        store.existsByCategoryIdAndUrl(cid, "https://example.com/nonexistent") shouldBe false
    }

    @Test
    fun `origin default 는 manual (origin 파라미터 생략 시)`() {
        val cid = insertCategory("c")
        // Call insert WITHOUT origin — default should be "manual"
        store.insert(
            id = UUID.randomUUID().toString(), categoryId = cid,
            sourceUrl = "https://example.com/default", sourceName = "default"
        )
        store.findByCategoryIdAndOrigin(cid, "manual") shouldHaveSize 1
    }
}
