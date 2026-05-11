package com.ohmyclipping.store

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class CategorySectionSilenceLogStoreTest(
    @Autowired private val store: CategorySectionSilenceLogStore,
    @Autowired private val jdbc: JdbcTemplate
) {
    /** Use raw JDBC insert for category to avoid JPA flush issues within @Transactional test. */
    private fun insertCategory(slug: String): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO batch_categories(id, name, is_active, created_at, updated_at, system_updated_at)
            VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            id, "silence-test-$slug"
        )
        return id
    }

    @Test
    fun `incrementAndGet 은 첫 호출 시 1 반환`() {
        val cat = insertCategory("a")
        store.incrementAndGet(cat, "topic") shouldBe 1
    }

    @Test
    fun `incrementAndGet 두 번째는 2`() {
        val cat = insertCategory("b")
        store.incrementAndGet(cat, "topic")
        store.incrementAndGet(cat, "topic") shouldBe 2
    }

    @Test
    fun `reset 0 으로 돌림`() {
        val cat = insertCategory("c")
        store.incrementAndGet(cat, "topic")
        store.reset(cat, "topic")
        store.getConsecutiveEmptyDays(cat, "topic") shouldBe 0
    }

    @Test
    fun `다른 sectionKey 는 독립 카운트`() {
        val cat = insertCategory("d")
        store.incrementAndGet(cat, "topic")
        store.incrementAndGet(cat, "account")
        store.getConsecutiveEmptyDays(cat, "topic") shouldBe 1
        store.getConsecutiveEmptyDays(cat, "account") shouldBe 1
    }

    @Test
    fun `getConsecutiveEmptyDays 미저장 0 반환`() {
        val cat = insertCategory("e")
        store.getConsecutiveEmptyDays(cat, "topic") shouldBe 0
    }
}
