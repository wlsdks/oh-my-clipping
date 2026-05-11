package com.clipping.mcpserver.store

import com.clipping.mcpserver.store.analytics.dto.PersonaBatchRun
import com.clipping.mcpserver.store.analytics.dto.TriggerType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * Persona batch run recent-list bad-row coercion tests.
 */
class JdbcPersonaAnalyticsStoreBatchRunTest {

    private val jdbc = mockk<JdbcTemplate>()
    private val store = JdbcPersonaAnalyticsStore(jdbc)

    @Test
    fun `findRecentBatchRuns는 triggerType 또는 필수 날짜가 깨진 row를 제외한다`() {
        every {
            jdbc.query(any<String>(), any<RowMapper<PersonaBatchRun?>>(), any<Int>())
        } answers {
            val mapper = secondArg<RowMapper<PersonaBatchRun?>>()
            listOfNotNull(
                mapper.mapRow(row(id = "bad-trigger", triggerType = "UNKNOWN"), 0),
                mapper.mapRow(row(id = "bad-week", weekStart = null), 1),
                mapper.mapRow(row(id = "bad-started", startedAt = null), 2),
                mapper.mapRow(row(id = "ok"), 3),
            )
        }

        val result = store.findRecentBatchRuns(10)

        result shouldHaveSize 1
        result.first().id shouldBe "ok"
        result.first().triggerType shouldBe TriggerType.SCHEDULED
    }

    @Test
    fun `findRecentBatchRuns는 overallStatus가 null이면 UNKNOWN으로 보정한다`() {
        every {
            jdbc.query(any<String>(), any<RowMapper<PersonaBatchRun?>>(), any<Int>())
        } answers {
            val mapper = secondArg<RowMapper<PersonaBatchRun?>>()
            listOfNotNull(mapper.mapRow(row(id = "nullable-status", overallStatus = null), 0))
        }

        store.findRecentBatchRuns(10).first().overallStatus shouldBe "UNKNOWN"
    }

    private fun row(
        id: String,
        runId: String? = "run-$id",
        triggerType: String? = TriggerType.SCHEDULED.name,
        weekStart: LocalDate? = LocalDate.of(2026, 4, 6),
        startedAt: Instant? = Instant.parse("2026-04-06T00:00:00Z"),
        overallStatus: String? = "SUCCEEDED",
    ): ResultSet =
        mockk {
            every { getString("id") } returns id
            every { getString("run_id") } returns runId
            every { getString("trigger_type") } returns triggerType
            every { getDate("week_start") } returns weekStart?.let(Date::valueOf)
            every { getTimestamp("started_at") } returns startedAt?.let(Timestamp::from)
            every { getTimestamp("finished_at") } returns null
            every { getString("overall_status") } returns overallStatus
            every { getString("snapshot_status") } returns null
            every { getString("anomaly_status") } returns null
            every { getString("clustering_status") } returns null
            every { getString("report_status") } returns null
            every { getInt("personas_scanned") } returns 0
            every { getInt("anomalies_created") } returns 0
            every { getInt("anomalies_resolved") } returns 0
            every { getInt("embedding_calls") } returns 0
            every { getInt("llm_calls") } returns 0
            every { getInt("llm_tokens_used") } returns 0
            every { getString("error_message") } returns null
            every { getString("error_step") } returns null
            every { getString("triggered_by") } returns null
        }
}
