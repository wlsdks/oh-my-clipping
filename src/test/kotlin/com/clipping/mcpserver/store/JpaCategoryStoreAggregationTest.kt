package com.clipping.mcpserver.store

import com.clipping.mcpserver.repository.CategoryRepository
import com.clipping.mcpserver.repository.RssSourceRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Test
import java.math.BigInteger

/**
 * Category aggregate native result coercion tests.
 */
class JpaCategoryStoreAggregationTest {

    private val repository = mockk<CategoryRepository>(relaxed = true)
    private val rssSourceRepository = mockk<RssSourceRepository>(relaxed = true)
    private val entityManager = mockk<EntityManager>()
    private val query = mockk<Query>(relaxed = true)
    private val store = JpaCategoryStore(repository, rssSourceRepository, entityManager)

    @Test
    fun `countSourcesByCategoryIds는 Number 계열 COUNT 타입을 안전하게 변환한다`() {
        every { entityManager.createNativeQuery(any<String>()) } returns query
        every { query.resultList } returns listOf(
            arrayOf<Any?>("cat-1", 2),
            arrayOf<Any?>("cat-2", BigInteger.valueOf(3)),
        )

        store.countSourcesByCategoryIds(listOf("cat-1", "cat-2")) shouldBe mapOf(
            "cat-1" to 2,
            "cat-2" to 3,
        )
        verify(exactly = 1) { query.setParameter(1, "cat-1") }
        verify(exactly = 1) { query.setParameter(2, "cat-2") }
    }

    @Test
    fun `countSourcesByCategoryIds는 null category 또는 null count row를 제외한다`() {
        every { entityManager.createNativeQuery(any<String>()) } returns query
        every { query.resultList } returns listOf(
            arrayOf<Any?>("cat-1", 4L),
            arrayOf<Any?>(null, 2L),
            arrayOf<Any?>("cat-2", null),
        )

        store.countSourcesByCategoryIds(listOf("cat-1", "cat-2")) shouldBe mapOf("cat-1" to 4)
    }
}
