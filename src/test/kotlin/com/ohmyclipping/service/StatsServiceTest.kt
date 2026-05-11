package com.ohmyclipping.service

import com.ohmyclipping.model.Category
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.StatsStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StatsServiceTest {

    @Autowired lateinit var statsService: StatsService
    @Autowired lateinit var statsStore: StatsStore
    @Autowired lateinit var categoryStore: CategoryStore

    private lateinit var category: Category

    @BeforeEach
    fun setup() {
        category = categoryStore.save(Category(id = "", name = "TestCat"))
    }

    @Test
    fun `recordCollection should create stat entry`() {
        statsService.recordCollection(category.id, 10, 2)
        val stats = statsService.getMonthlyStats(category.id, YearMonth.now())
        stats.size shouldBe 1
        stats[0].itemsCollected shouldBe 10
        stats[0].itemsDuplicates shouldBe 2
    }

    @Test
    fun `recordSummarization should create stat entry with keywords`() {
        statsService.recordSummarization(category.id, 5, listOf("AI", "ML"), 0.75f)
        val stats = statsService.getMonthlyStats(category.id, YearMonth.now())
        stats.size shouldBe 1
        stats[0].itemsSummarized shouldBe 5
        stats[0].topKeywords shouldBe listOf("AI", "ML")
    }

    @Test
    fun `recordSent should create stat entry`() {
        statsService.recordSent(category.id, 3)
        val stats = statsService.getMonthlyStats(category.id, YearMonth.now())
        stats.size shouldBe 1
        stats[0].itemsSent shouldBe 3
    }

    @Test
    fun `multiple records on same day should merge`() {
        statsService.recordCollection(category.id, 10, 3)
        statsService.recordSummarization(category.id, 5, listOf("AI"), 0.8f)
        statsService.recordSent(category.id, 3)
        statsService.recordDigestDelivery(category.id, 4, 3)

        val stats = statsService.getMonthlyStats(category.id, YearMonth.now())
        stats.size shouldBe 1
        stats[0].itemsCollected shouldBe 10
        stats[0].itemsDuplicates shouldBe 3
        stats[0].itemsSummarized shouldBe 5
        stats[0].itemsSent shouldBe 3
        stats[0].slackSendAttempts shouldBe 4
        stats[0].slackSendSuccesses shouldBe 3
    }
}
