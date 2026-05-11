package com.ohmyclipping.store

import com.ohmyclipping.model.SummaryFeedbackHotSummary
import com.ohmyclipping.repository.SummaryFeedbackRepository
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
 * Weekly hot feedback aggregate bad-row coercion tests.
 */
class JpaSummaryFeedbackStoreHotSummaryTest {

    private val repository = mockk<SummaryFeedbackRepository>(relaxed = true)
    private val jdbc = mockk<JdbcTemplate>()
    private val store = JpaSummaryFeedbackStore(repository, jdbc)

    @Test
    fun `findWeeklyHot은 summaryId 또는 lastFeedbackAt이 null인 row를 제외한다`() {
        every {
            jdbc.query(
                match { it.contains("MAX(f.created_at) AS last_feedback_at") && !it.contains("bs.category_id = ?") },
                any<RowMapper<SummaryFeedbackHotSummary?>>(),
                *anyVararg()
            )
        } answers {
            val mapper = secondArg<RowMapper<SummaryFeedbackHotSummary?>>()
            listOfNotNull(
                mapper.mapRow(hotRow(summaryId = null, title = "깨진 요약", sourceLink = "https://example.com/a", lastFeedbackAt = NOW), 0),
                mapper.mapRow(hotRow(summaryId = "summary-1", title = "제목", sourceLink = "https://example.com/b", lastFeedbackAt = null), 1),
                mapper.mapRow(hotRow(summaryId = "summary-2", title = null, sourceLink = null, lastFeedbackAt = NOW), 2),
            )
        }

        store.findWeeklyHot(NOW.minusSeconds(3600), NOW.plusSeconds(3600), limit = 10, categoryId = null) shouldBe listOf(
            SummaryFeedbackHotSummary(
                summaryId = "summary-2",
                title = "",
                sourceLink = "",
                likeCount = 2,
                neutralCount = 1,
                dislikeCount = 0,
                totalCount = 3,
                score = 4.1,
                lastFeedbackAt = NOW,
            )
        )
    }

    private fun hotRow(
        summaryId: String?,
        title: String?,
        sourceLink: String?,
        lastFeedbackAt: Instant?,
    ): ResultSet =
        mockk {
            every { getString("summary_id") } returns summaryId
            every { getString("title") } returns title
            every { getString("source_link") } returns sourceLink
            every { getInt("like_count") } returns 2
            every { getInt("neutral_count") } returns 1
            every { getInt("dislike_count") } returns 0
            every { getInt("total_count") } returns 3
            every { getDouble("score") } returns 4.1
            every { getTimestamp("last_feedback_at") } returns lastFeedbackAt?.let(Timestamp::from)
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-04-27T00:00:00Z")
    }
}
