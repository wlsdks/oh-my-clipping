package com.ohmyclipping.store

import com.ohmyclipping.entity.PipelineStepTraceEntity
import com.ohmyclipping.service.dto.pipeline.PipelineRunEntity
import com.ohmyclipping.service.dto.pipeline.PipelineRunStatus
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant
import java.sql.Timestamp

/**
 * JpaPipelineRunStore의 단계 추적 조회 최적화 테스트.
 * 파이프라인 실행 이력이 누적되어도 단일 runId 조건 조회만 수행하는지 검증한다.
 */
class JpaPipelineRunStoreStepTraceTest {

    private val runRepository = mockk<com.ohmyclipping.repository.PipelineRunRepository>(relaxed = true)
    private val stepTraceRepository = mockk<com.ohmyclipping.repository.PipelineStepTraceRepository>()
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)

    private val store = JpaPipelineRunStore(runRepository, stepTraceRepository, jdbc)

    @Test
    fun `findStepTracesByRunId는 전체 trace를 메모리에서 필터링하지 않고 runId 조건 조회를 사용한다`() {
        val startedAt = Instant.parse("2026-04-26T00:00:00Z")
        val trace = PipelineStepTraceEntity(
            id = "trace-1",
            runId = "run-1",
            step = "COLLECT",
            status = "SUCCEEDED",
            startedAt = startedAt,
            createdAt = startedAt,
        )
        every { stepTraceRepository.findByRunIdOrderByStartedAtAsc("run-1") } returns listOf(trace)

        val result = store.findStepTracesByRunId("run-1")

        result.map { it.id } shouldBe listOf("trace-1")
        verify(exactly = 1) { stepTraceRepository.findByRunIdOrderByStartedAtAsc("run-1") }
        verify(exactly = 0) { stepTraceRepository.findAll() }
    }

    @Test
    fun `hasRunStartedBetween은 COUNT 결과가 null이면 false를 반환한다`() {
        val lower = Instant.parse("2026-04-26T00:00:00Z")
        val upper = Instant.parse("2026-04-26T01:00:00Z")
        every {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pipeline_runs WHERE started_at BETWEEN ? AND ?",
                Int::class.java,
                Timestamp.from(lower),
                Timestamp.from(upper),
            )
        } returns null

        store.hasRunStartedBetween(lower, upper) shouldBe false
    }

    @Test
    fun `findAll은 status 또는 필수 timestamp가 깨진 row를 제외한다`() {
        every {
            jdbc.query(any<String>(), any<RowMapper<PipelineRunEntity?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<PipelineRunEntity?>>()
            listOfNotNull(
                mapper.mapRow(row(id = "bad-status", status = "BROKEN"), 0),
                mapper.mapRow(row(id = "bad-started", startedAt = null), 1),
                mapper.mapRow(row(id = "ok"), 2),
            )
        }

        val result = store.findAll(
            categoryId = null,
            status = null,
            since = null,
            offset = 0,
            limit = 20,
            categoryIds = null,
        )

        result.map { it.id } shouldBe listOf("ok")
        result.first().status shouldBe PipelineRunStatus.SUCCEEDED
    }

    @Test
    fun `countByStatusSince는 null status row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("GROUP BY status") }, any<RowMapper<Pair<String, Long>?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<Pair<String, Long>?>>()
            listOfNotNull(
                mapper.mapRow(statusCountRow(status = null, count = 7L), 0),
                mapper.mapRow(statusCountRow(status = "SUCCEEDED", count = 3L), 1),
            )
        }

        store.countByStatusSince(Instant.parse("2026-04-26T00:00:00Z")) shouldBe mapOf("SUCCEEDED" to 3L)
    }

    private fun row(
        id: String,
        status: String? = PipelineRunStatus.SUCCEEDED.name,
        startedAt: Instant? = Instant.parse("2026-04-26T00:00:00Z"),
        createdAt: Instant? = Instant.parse("2026-04-26T00:00:01Z"),
    ): ResultSet =
        mockk {
            every { getString("id") } returns id
            every { getString("category_id") } returns "cat-1"
            every { getString("category_name") } returns "카테고리"
            every { getString("triggered_by") } returns "scheduler"
            every { getString("status") } returns status
            every { getString("orchestration_mode") } returns "SYNC"
            every { getInt("total_collected") } returns 1
            every { getInt("total_summarized") } returns 1
            every { getInt("total_digest_selected") } returns 1
            every { getBoolean("posted_to_slack") } returns false
            every { getTimestamp("started_at") } returns startedAt?.let(Timestamp::from)
            every { getTimestamp("ended_at") } returns null
            every { getObject("duration_ms") } returns null
            every { getString("error_message") } returns null
            every { getTimestamp("created_at") } returns createdAt?.let(Timestamp::from)
        }

    private fun statusCountRow(status: String?, count: Long): ResultSet =
        mockk {
            every { getString("status") } returns status
            every { getLong("cnt") } returns count
        }
}
