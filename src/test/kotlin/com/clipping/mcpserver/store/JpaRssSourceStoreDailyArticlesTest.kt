package com.clipping.mcpserver.store

import com.clipping.mcpserver.repository.RssSourceRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Test
import java.sql.Date
import java.time.Instant
import java.time.LocalDate

/**
 * Rss source analytics native result coercion tests.
 */
class JpaRssSourceStoreDailyArticlesTest {

    private val repository = mockk<RssSourceRepository>(relaxed = true)
    private val entityManager = mockk<EntityManager>()
    private val query = mockk<Query>(relaxed = true)
    private val store = JpaRssSourceStore(repository, entityManager)

    @Test
    fun `countDailyArticlesBySource는 SQL Date와 LocalDate 결과를 모두 처리한다`() {
        every { entityManager.createNativeQuery(any<String>()) } returns query
        every { query.resultList } returns listOf(
            arrayOf<Any?>(Date.valueOf(LocalDate.of(2026, 4, 26)), 2L),
            arrayOf<Any?>(LocalDate.of(2026, 4, 25), 3),
        )

        val result = store.countDailyArticlesBySource("src-1", Instant.parse("2026-04-20T00:00:00Z"))

        result shouldBe listOf(
            LocalDate.of(2026, 4, 26) to 2,
            LocalDate.of(2026, 4, 25) to 3,
        )
        verify(exactly = 1) { query.setParameter(1, "src-1") }
    }

    @Test
    fun `countDailyArticlesBySource는 잘못된 date 또는 count row를 제외한다`() {
        every { entityManager.createNativeQuery(any<String>()) } returns query
        every { query.resultList } returns listOf(
            arrayOf<Any?>("2026-04-26", 2L),
            arrayOf<Any?>(LocalDate.of(2026, 4, 25), null),
            arrayOf<Any?>(LocalDate.of(2026, 4, 24), 1L),
        )

        store.countDailyArticlesBySource("src-1", Instant.parse("2026-04-20T00:00:00Z")) shouldBe
            listOf(LocalDate.of(2026, 4, 24) to 1)
    }
}
