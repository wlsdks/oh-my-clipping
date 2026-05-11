package com.ohmyclipping.store

import com.ohmyclipping.repository.CategoryOrganizationRepository
import com.ohmyclipping.repository.OrganizationRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigInteger

/**
 * Organization aggregate projection coercion tests.
 */
class JpaOrganizationStoreAggregationTest {

    private val repository = mockk<OrganizationRepository>(relaxed = true)
    private val linkRepository = mockk<CategoryOrganizationRepository>()
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val store = JpaOrganizationStore(repository, linkRepository, jdbc, ObjectMapper())

    @Test
    fun `countCategoryLinksByOrganizationIds는 Number 계열 COUNT 타입을 안전하게 변환한다`() {
        every { linkRepository.countByOrganizationIds(listOf("org-1", "org-2")) } returns listOf(
            arrayOf("org-1", 2),
            arrayOf("org-2", BigInteger.valueOf(3)),
        )

        store.countCategoryLinksByOrganizationIds(listOf("org-1", "org-2")) shouldBe mapOf(
            "org-1" to 2,
            "org-2" to 3,
        )
    }

    @Test
    fun `countCategoryLinksByOrganizationIds는 null organization 또는 null count row를 제외한다`() {
        @Suppress("UNCHECKED_CAST")
        val rows = listOf(
            arrayOf<Any?>("org-1", 4L) as Array<Any>,
            arrayOf<Any?>(null, 2L) as Array<Any>,
            arrayOf<Any?>("org-2", null) as Array<Any>,
        )
        every { linkRepository.countByOrganizationIds(listOf("org-1", "org-2")) } returns listOf(
            *rows.toTypedArray()
        )

        store.countCategoryLinksByOrganizationIds(listOf("org-1", "org-2")) shouldBe mapOf("org-1" to 4)
    }
}
