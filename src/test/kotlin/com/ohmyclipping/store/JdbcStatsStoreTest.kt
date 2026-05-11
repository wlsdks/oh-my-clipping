package com.ohmyclipping.store

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.ClippingStat
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcStatsStoreTest {

    @Autowired lateinit var statsStore: StatsStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var jdbc: JdbcTemplate

    private lateinit var category: Category

    @BeforeEach
    fun setup() {
        category = categoryStore.save(Category(id = "", name = "TestCat"))
    }

    @Test
    fun `should upsert new stat`() {
        val stat = statsStore.upsert(
            ClippingStat(
                id = "",
                categoryId = category.id,
                statDate = LocalDate.of(2026, 2, 14),
                itemsCollected = 10,
                itemsDuplicates = 2,
                topKeywords = listOf("AI", "ML")
            )
        )
        stat.itemsCollected shouldBe 10
        stat.itemsDuplicates shouldBe 2
        stat.topKeywords shouldBe listOf("AI", "ML")
    }

    @Test
    fun `should merge on upsert for same category and date`() {
        statsStore.upsert(
            ClippingStat(
                id = "", categoryId = category.id, statDate = LocalDate.of(2026, 2, 14),
                itemsCollected = 5, itemsDuplicates = 1, slackSendAttempts = 2, slackSendSuccesses = 1, topKeywords = listOf("AI")
            )
        )
        val merged = statsStore.upsert(
            ClippingStat(
                id = "", categoryId = category.id, statDate = LocalDate.of(2026, 2, 14),
                itemsCollected = 3, itemsDuplicates = 2, slackSendAttempts = 3, slackSendSuccesses = 3, topKeywords = listOf("ML")
            )
        )
        merged.itemsCollected shouldBe 8
        merged.itemsDuplicates shouldBe 3
        merged.slackSendAttempts shouldBe 5
        merged.slackSendSuccesses shouldBe 4
        merged.topKeywords shouldBe listOf("AI", "ML")
    }

    @Test
    fun `should find monthly stats`() {
        statsStore.upsert(
            ClippingStat(id = "", categoryId = category.id, statDate = LocalDate.of(2026, 2, 10), itemsCollected = 5)
        )
        statsStore.upsert(
            ClippingStat(id = "", categoryId = category.id, statDate = LocalDate.of(2026, 2, 15), itemsCollected = 3)
        )
        statsStore.upsert(
            ClippingStat(id = "", categoryId = category.id, statDate = LocalDate.of(2026, 3, 1), itemsCollected = 7)
        )

        val febStats = statsStore.findMonthly(category.id, YearMonth.of(2026, 2))
        febStats shouldHaveSize 2

        val allFebStats = statsStore.findMonthly(null, YearMonth.of(2026, 2))
        allFebStats.size shouldBeGreaterThanOrEqual 2
    }

    @Test
    fun `should find by category and date`() {
        statsStore.upsert(
            ClippingStat(id = "", categoryId = category.id, statDate = LocalDate.of(2026, 2, 14), itemsCollected = 10)
        )
        val found = statsStore.findByCategoryAndDate(category.id, LocalDate.of(2026, 2, 14))
        found?.itemsCollected shouldBe 10
    }
}
