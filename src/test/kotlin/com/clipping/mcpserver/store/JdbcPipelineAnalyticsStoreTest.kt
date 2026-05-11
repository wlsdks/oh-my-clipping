package com.clipping.mcpserver.store

import com.clipping.mcpserver.store.pipeline.DeliveryLogStatus
import com.clipping.mcpserver.store.pipeline.LlmRunStatus
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate

/**
 * Pipeline analytics aggregate bad-row coercion tests.
 */
class JdbcPipelineAnalyticsStoreTest {

    private val jdbc = mockk<JdbcTemplate>()
    private val store = JdbcPipelineAnalyticsStore(jdbc)

    @Test
    fun `queryLlmStatusCounts는 null 또는 알 수 없는 status row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("FROM llm_runs") }, any<RowMapper<Unit>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<Unit>>()
            mapper.mapRow(resultSet(status = null, count = 3), 0)
            mapper.mapRow(resultSet(status = "UNKNOWN", count = 2), 1)
            mapper.mapRow(resultSet(status = LlmRunStatus.SUCCEEDED.name, count = 1), 2)
            emptyList<Unit>()
        }

        store.queryLlmStatusCounts(
            Instant.parse("2026-04-20T00:00:00Z"),
            Instant.parse("2026-04-21T00:00:00Z")
        ) shouldBe mapOf(LlmRunStatus.SUCCEEDED to 1)
    }

    @Test
    fun `queryDeliveryStatusCounts는 null 또는 알 수 없는 status row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("FROM delivery_log") }, any<RowMapper<Unit>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<Unit>>()
            mapper.mapRow(resultSet(status = null, count = 3), 0)
            mapper.mapRow(resultSet(status = "ABANDONED", count = 2), 1)
            mapper.mapRow(resultSet(status = DeliveryLogStatus.SENT.name, count = 1), 2)
            emptyList<Unit>()
        }

        store.queryDeliveryStatusCounts(
            LocalDate.of(2026, 4, 20),
            LocalDate.of(2026, 4, 20)
        ) shouldBe mapOf(DeliveryLogStatus.SENT to 1)
    }

    @Test
    fun `queryDeliveryDailyMap은 delivery_date가 null인 row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("delivery_date, status") }, any<RowMapper<Unit>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<Unit>>()
            mapper.mapRow(resultSet(status = DeliveryLogStatus.SENT.name, count = 3, date = null), 0)
            emptyList<Unit>()
        }

        store.queryDeliveryDailyMap(
            LocalDate.of(2026, 4, 20),
            LocalDate.of(2026, 4, 20)
        ).shouldBeEmpty()
    }

    private fun resultSet(status: String?, count: Int, date: java.sql.Date? = null): ResultSet =
        mockk {
            every { getString("status") } returns status
            every { getInt("cnt") } returns count
            every { getDate("delivery_date") } returns date
        }
}
