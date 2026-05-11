package com.ohmyclipping.store

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.RssItem
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RssItemStoreDedupIntegrationTest {

    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var entityManager: EntityManager

    private lateinit var categoryA: String
    private lateinit var categoryB: String

    @BeforeEach
    fun setUp() {
        categoryA = categoryStore.save(Category(id = "", name = "Dedup-A-${System.nanoTime()}")).id
        categoryB = categoryStore.save(Category(id = "", name = "Dedup-B-${System.nanoTime()}")).id
    }

    @Test
    fun `same link can be saved independently per category`() {
        val link = "https://example.com/shared-${System.nanoTime()}"

        val first = itemStore.save(item(title = "카테고리 A 기사", link = link, categoryId = categoryA))
        val second = itemStore.save(item(title = "카테고리 B 기사", link = link, categoryId = categoryB))

        first.link shouldBe link
        second.link shouldBe link
        itemStore.findByLink(link, categoryA)?.id shouldBe first.id
        itemStore.findByLink(link, categoryB)?.id shouldBe second.id
    }

    @Test
    fun `same link in same category is rejected by database uniqueness`() {
        val link = "https://example.com/duplicate-${System.nanoTime()}"
        itemStore.save(item(title = "첫 번째 기사", link = link, categoryId = categoryA))

        shouldThrowAny {
            itemStore.save(item(title = "두 번째 기사", link = link, categoryId = categoryA))
            entityManager.flush()
        }
    }

    @Test
    fun `findExistingLinks returns only links that exist in requested category`() {
        val sharedLink = "https://example.com/shared-existing-${System.nanoTime()}"
        val onlyOtherCategoryLink = "https://example.com/other-only-${System.nanoTime()}"
        val missingLink = "https://example.com/missing-${System.nanoTime()}"

        itemStore.save(item(title = "A 공유", link = sharedLink, categoryId = categoryA))
        itemStore.save(item(title = "B 공유", link = sharedLink, categoryId = categoryB))
        itemStore.save(item(title = "B 전용", link = onlyOtherCategoryLink, categoryId = categoryB))

        val result = itemStore.findExistingLinks(
            links = listOf(sharedLink, onlyOtherCategoryLink, missingLink, sharedLink),
            categoryId = categoryA
        )

        result shouldBe setOf(sharedLink)
    }

    @Test
    fun `findRecentTitles returns only recent titles from requested category`() {
        val recentA = itemStore.save(item(title = "최근 A 기사", link = "https://example.com/recent-a-${System.nanoTime()}", categoryId = categoryA))
        val oldA = itemStore.save(item(title = "오래된 A 기사", link = "https://example.com/old-a-${System.nanoTime()}", categoryId = categoryA))
        val recentB = itemStore.save(item(title = "최근 B 기사", link = "https://example.com/recent-b-${System.nanoTime()}", categoryId = categoryB))
        entityManager.flush()

        jdbc.update(
            "UPDATE rss_items SET created_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)),
            oldA.id
        )
        entityManager.flush()
        entityManager.clear()

        val result = itemStore.findRecentTitles(
            categoryId = categoryA,
            after = Instant.now().minus(7, ChronoUnit.DAYS),
            limit = 10
        )

        result shouldContainExactlyInAnyOrder listOf(recentA.title)
        result shouldNotContain oldA.title
        result shouldNotContain recentB.title
    }

    @Test
    fun `findRecentTitles clamps excessive limit to 1000`() {
        repeat(1005) { index ->
            itemStore.save(
                item(
                    title = "limit-title-$index",
                    link = "https://example.com/limit-${System.nanoTime()}-$index",
                    categoryId = categoryA
                )
            )
        }

        val result = itemStore.findRecentTitles(
            categoryId = categoryA,
            after = Instant.now().minus(1, ChronoUnit.DAYS),
            limit = 5000
        )

        result.size shouldBe 1000
    }

    private fun item(title: String, link: String, categoryId: String): RssItem =
        RssItem(
            id = "",
            title = title,
            content = "본문",
            link = link,
            categoryId = categoryId,
            rssSourceId = null
        )
}
