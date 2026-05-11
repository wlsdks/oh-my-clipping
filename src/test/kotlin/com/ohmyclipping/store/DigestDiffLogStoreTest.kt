package com.ohmyclipping.store

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DigestDiffLogStoreTest(
    @Autowired private val store: DigestDiffLogStore,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @Test
    fun `insertIfAbsent — 새 행 저장 + 같은 날 중복 호출은 조용히 무시`() {
        val categoryId = insertTestCategory()
        val date = LocalDate.of(2026, 4, 22)

        store.insertIfAbsent(
            categoryId = categoryId, digestDate = date,
            legacySummary = "legacy text", newSummary = "new text", newMode = "DUAL_SECTION",
            sectionsCount = 2, articlesCount = 5, crossMatchCount = 1,
        )

        // 두번째 insert 는 UNIQUE 충돌 — 예외 전파되지 않고 조용히 무시
        store.insertIfAbsent(
            categoryId = categoryId, digestDate = date,
            legacySummary = "should be ignored", newSummary = "new2", newMode = "TOPIC_ONLY",
            sectionsCount = 1, articlesCount = 2, crossMatchCount = 0,
        )

        val entries = store.findByCategoryAndDateRange(categoryId, date, date)
        entries.size shouldBe 1
        entries[0].newMode shouldBe "DUAL_SECTION"
        entries[0].sectionsCount shouldBe 2
    }

    @Test
    fun `findByCategoryAndDateRange — from~to 밖 날짜는 제외, 정렬은 최신 먼저`() {
        val categoryId = insertTestCategory()
        val d1 = LocalDate.of(2026, 4, 20)
        val d2 = LocalDate.of(2026, 4, 21)
        val d3 = LocalDate.of(2026, 4, 22)
        val outOfRange = LocalDate.of(2026, 4, 25)

        listOf(d1, d2, d3, outOfRange).forEach {
            store.insertIfAbsent(categoryId, it, null, null, null, 0, 0, 0)
        }

        val inRange = store.findByCategoryAndDateRange(categoryId, d1, d3)
        inRange.map { it.digestDate } shouldBe listOf(d3, d2, d1)   // DESC
    }

    @Test
    fun `findByCategoryAndDateRange — 다른 카테고리 행은 섞이지 않는다`() {
        val catA = insertTestCategory()
        val catB = insertTestCategory()
        val date = LocalDate.of(2026, 4, 22)

        store.insertIfAbsent(catA, date, null, null, null, 0, 0, 0)
        store.insertIfAbsent(catB, date, null, null, null, 0, 0, 0)

        val forA = store.findByCategoryAndDateRange(catA, date, date)
        forA.size shouldBe 1
        forA[0].categoryId shouldBe catA
    }

    @Test
    fun `findByCategoryAndDateRange with paging — DB limit offset 으로 필요한 페이지만 반환한다`() {
        val categoryId = insertTestCategory()
        val d1 = LocalDate.of(2026, 4, 20)
        val d2 = LocalDate.of(2026, 4, 21)
        val d3 = LocalDate.of(2026, 4, 22)

        listOf(d1, d2, d3).forEach {
            store.insertIfAbsent(categoryId, it, null, null, null, 0, 0, 0)
        }

        val page = store.findByCategoryAndDateRange(categoryId, d1, d3, offset = 1, limit = 1)

        page.map { it.digestDate } shouldBe listOf(d2)
    }

    @Test
    fun `countByCategoryAndDateRange — 범위와 카테고리에 맞는 행 수만 센다`() {
        val catA = insertTestCategory()
        val catB = insertTestCategory()
        val d1 = LocalDate.of(2026, 4, 20)
        val d2 = LocalDate.of(2026, 4, 21)
        val outOfRange = LocalDate.of(2026, 5, 1)

        store.insertIfAbsent(catA, d1, null, null, null, 0, 0, 0)
        store.insertIfAbsent(catA, d2, null, null, null, 0, 0, 0)
        store.insertIfAbsent(catA, outOfRange, null, null, null, 0, 0, 0)
        store.insertIfAbsent(catB, d1, null, null, null, 0, 0, 0)

        store.countByCategoryAndDateRange(catA, d1, d2) shouldBe 2
    }

    private fun insertTestCategory(): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO batch_categories (id, name, is_active, created_at, updated_at, system_updated_at)
               VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            id, "ddl-test-${System.nanoTime()}",
        )
        return id
    }
}
