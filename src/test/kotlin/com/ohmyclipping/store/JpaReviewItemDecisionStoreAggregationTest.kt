package com.ohmyclipping.store

import com.ohmyclipping.repository.ReviewItemDecisionRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * Review queue aggregate bad-row coercion tests.
 */
class JpaReviewItemDecisionStoreAggregationTest {

    private val repository = mockk<ReviewItemDecisionRepository>(relaxed = true)
    private val jdbc = mockk<JdbcTemplate>()
    private val store = JpaReviewItemDecisionStore(repository, jdbc)

    @Test
    fun `countByStatusGroupedByCategory는 null category 또는 null status row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("GROUP BY category_id, status") }, any<RowMapper<CategoryStatusCount?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<CategoryStatusCount?>>()
            listOfNotNull(
                mapper.mapRow(categoryStatusRow(categoryId = null, status = "REVIEW"), 0),
                mapper.mapRow(categoryStatusRow(categoryId = "cat-1", status = null), 1),
                mapper.mapRow(categoryStatusRow(categoryId = "cat-1", status = "EXCLUDE"), 2),
            )
        }

        store.countByStatusGroupedByCategory(FROM, TO) shouldBe listOf(
            CategoryStatusCount("cat-1", "EXCLUDE", 1)
        )
    }

    @Test
    fun `findExcludedItems는 excluded_at이 null인 row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("ri.created_at AS excluded_at") }, any<RowMapper<ExcludedItemRow?>>(), any<String>(), any<Int>())
        } answers {
            val mapper = secondArg<RowMapper<ExcludedItemRow?>>()
            listOfNotNull(mapper.mapRow(excludedItemRow(excludedAt = null), 0))
        }

        store.findExcludedItems("cat-1", 5).shouldBeEmpty()
    }

    @Test
    fun `countByCategory는 null category row를 제외하고 categoryName null은 빈 문자열로 보정한다`() {
        every {
            jdbc.query(match { it.contains("COUNT(CASE WHEN ri.status = 'REVIEW'") }, any<RowMapper<ReviewCategoryCounts?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<ReviewCategoryCounts?>>()
            listOfNotNull(
                mapper.mapRow(reviewCategoryRow(categoryId = null, categoryName = "깨진 카테고리"), 0),
                mapper.mapRow(reviewCategoryRow(categoryId = "cat-1", categoryName = null), 1),
            )
        }

        store.countByCategory() shouldBe listOf(
            ReviewCategoryCounts(
                categoryId = "cat-1",
                categoryName = "",
                totalCount = 3,
                reviewCount = 1,
                includeCount = 1,
                excludeCount = 1,
                suggestedIncludeCount = 1,
            )
        )
    }

    @Test
    fun `getAccuracyStats는 null category 또는 status row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("ri.suggested_status") && it.contains("GROUP BY") }, any<RowMapper<AccuracyRow?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<AccuracyRow?>>()
            listOfNotNull(
                mapper.mapRow(accuracyRow(categoryId = null, suggestedStatus = "INCLUDE", actualStatus = "EXCLUDE"), 0),
                mapper.mapRow(accuracyRow(categoryId = "cat-1", suggestedStatus = null, actualStatus = "EXCLUDE"), 1),
                mapper.mapRow(accuracyRow(categoryId = "cat-1", suggestedStatus = "INCLUDE", actualStatus = null), 2),
                mapper.mapRow(accuracyRow(categoryId = "cat-1", categoryName = null, suggestedStatus = "INCLUDE", actualStatus = "EXCLUDE"), 3),
            )
        }

        store.getAccuracyStats(FROM, TO) shouldBe listOf(
            AccuracyRow(
                categoryId = "cat-1",
                categoryName = "",
                suggestedStatus = "INCLUDE",
                actualStatus = "EXCLUDE",
                count = 1,
            )
        )
    }

    @Test
    fun `findAutoExcluded는 summaryId 또는 excludedAt이 null인 row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("ri.reviewed_by = 'policy-auto'") && it.contains("LIMIT ? OFFSET ?") }, any<RowMapper<AutoExcludedRow?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<AutoExcludedRow?>>()
            listOfNotNull(
                mapper.mapRow(autoExcludedRow(summaryId = null, excludedAt = FROM), 0),
                mapper.mapRow(autoExcludedRow(summaryId = "summary-1", excludedAt = null), 1),
                mapper.mapRow(autoExcludedRow(summaryId = "summary-2", excludedAt = FROM, categoryName = null), 2),
            )
        }

        store.findAutoExcluded(FROM, categoryId = null, reasonPrefix = null, limit = 10, offset = 0) shouldBe listOf(
            AutoExcludedRow(
                summaryId = "summary-2",
                title = "title",
                originalTitle = "original",
                translatedTitle = null,
                categoryId = "cat-1",
                categoryName = "",
                score = 1.0f,
                reason = "rule:test",
                excludedAt = FROM,
                summary = "summary",
                sourceUrl = null,
                sourceName = null,
                publishedAt = null,
                eventType = null,
                sentiment = null,
            )
        )
    }

    private fun categoryStatusRow(categoryId: String?, status: String?): ResultSet =
        mockk {
            every { getString("category_id") } returns categoryId
            every { getString("status") } returns status
            every { getInt("cnt") } returns 1
        }

    private fun excludedItemRow(excludedAt: Instant?): ResultSet =
        mockk {
            every { getString("title") } returns "title"
            every { getString("reason") } returns "reason"
            every { getFloat("score") } returns 1.0f
            every { getTimestamp("excluded_at") } returns excludedAt?.let(Timestamp::from)
        }

    private fun reviewCategoryRow(categoryId: String?, categoryName: String?): ResultSet =
        mockk {
            every { getString("category_id") } returns categoryId
            every { getString("category_name") } returns categoryName
            every { getInt("total_count") } returns 3
            every { getInt("review_count") } returns 1
            every { getInt("include_count") } returns 1
            every { getInt("exclude_count") } returns 1
            every { getInt("suggested_include_count") } returns 1
        }

    private fun accuracyRow(
        categoryId: String?,
        categoryName: String? = "카테고리",
        suggestedStatus: String?,
        actualStatus: String?,
    ): ResultSet =
        mockk {
            every { getString("category_id") } returns categoryId
            every { getString("category_name") } returns categoryName
            every { getString("suggested_status") } returns suggestedStatus
            every { getString("status") } returns actualStatus
            every { getInt("cnt") } returns 1
        }

    private fun autoExcludedRow(
        summaryId: String?,
        excludedAt: Instant?,
        categoryName: String? = "카테고리",
    ): ResultSet =
        mockk {
            every { getString("summary_id") } returns summaryId
            every { getString("title") } returns "title"
            every { getString("original_title") } returns "original"
            every { getString("translated_title") } returns null
            every { getString("category_id") } returns "cat-1"
            every { getString("category_name") } returns categoryName
            every { getFloat("score") } returns 1.0f
            every { getString("reason") } returns "rule:test"
            every { getTimestamp("excluded_at") } returns excludedAt?.let(Timestamp::from)
            every { getString("summary") } returns "summary"
            every { getString("source_url") } returns null
            every { getString("source_name") } returns null
            every { getTimestamp("published_at") } returns null
            every { getString("event_type") } returns null
            every { getString("sentiment") } returns null
        }

    private companion object {
        val FROM: Instant = Instant.parse("2026-04-01T00:00:00Z")
        val TO: Instant = Instant.parse("2026-04-02T00:00:00Z")
    }
}
