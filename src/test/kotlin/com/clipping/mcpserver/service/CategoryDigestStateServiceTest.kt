package com.clipping.mcpserver.service

import io.kotest.matchers.shouldBe
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
class CategoryDigestStateServiceTest(
    @Autowired private val service: CategoryDigestStateService,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @Test
    fun `초기값 0 이다 (행 미존재)`() {
        val categoryId = insertTestCategory()
        service.getLegendDisplayCount(categoryId) shouldBe 0
    }

    @Test
    fun `increment 1회 후 1`() {
        val categoryId = insertTestCategory()
        service.incrementLegendDisplayCount(categoryId) shouldBe 1
        service.getLegendDisplayCount(categoryId) shouldBe 1
    }

    @Test
    fun `increment 3회 후 shouldShowFullLegend false`() {
        val categoryId = insertTestCategory()
        repeat(3) { service.incrementLegendDisplayCount(categoryId) }
        service.getLegendDisplayCount(categoryId) shouldBe 3
        service.shouldShowFullLegend(categoryId) shouldBe false
    }

    @Test
    fun `increment 2회 후 shouldShowFullLegend true (아직 임계 미만)`() {
        val categoryId = insertTestCategory()
        service.incrementLegendDisplayCount(categoryId)
        service.incrementLegendDisplayCount(categoryId)
        service.shouldShowFullLegend(categoryId) shouldBe true
    }

    @Test
    fun `increment 후 lastShownAt 이 기록된다`() {
        val categoryId = insertTestCategory()
        service.incrementLegendDisplayCount(categoryId)
        (service.getLegendLastShownAt(categoryId) != null) shouldBe true
    }

    private fun insertTestCategory(): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO batch_categories (id, name, is_active, created_at, updated_at, system_updated_at)
               VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            id, "cds-test-${System.nanoTime()}",
        )
        return id
    }
}
