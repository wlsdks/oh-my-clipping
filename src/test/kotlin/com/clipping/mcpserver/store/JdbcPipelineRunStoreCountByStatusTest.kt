package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Category
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
import java.util.UUID

/**
 * PipelineRunStore.countByStatusSince 통합 테스트.
 * started_at 경계 필터와 status GROUP BY 집계가 실제 H2 스키마에서 동작하는지 검증한다.
 *
 * 구현 주의:
 * 1) JPA save 가 즉시 flush 되지 않아 raw JDBC query 가 변경을 보지 못하는 이슈를 피하기 위해
 *    테스트 fixture 는 raw JDBC INSERT 로 삽입한다.
 * 2) H2 `DB_CLOSE_DELAY=-1` 로 JVM 수명 동안 DB 가 살아있어 다른 테스트의 row 가 보일 수 있으므로
 *    `@BeforeEach` 에서 본 테스트가 건드릴 started_at 구간을 선제 삭제한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcPipelineRunStoreCountByStatusTest {

    @Autowired lateinit var pipelineRunStore: PipelineRunStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var entityManager: EntityManager

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        // pipeline_runs.category_id FK 를 만족시키기 위해 카테고리를 먼저 만든다.
        categoryId = categoryStore.save(
            Category(id = "", name = "PipelineCountByStatus-${System.nanoTime()}")
        ).id
        // JPA 세션을 플러시해야 raw JDBC INSERT 가 FK 참조를 볼 수 있다.
        entityManager.flush()
        // cross-test pollution 방어: 본 테스트 범위의 started_at 구간을 선제 정리한다.
        // SINCE_BOUNDARY 한 시간 전부터 그 이후 전체를 삭제 — 테스트 insert 시점이 모두 이 범위 안.
        jdbc.update(
            "DELETE FROM pipeline_runs WHERE started_at >= ?",
            Timestamp.from(SINCE_BOUNDARY.minusSeconds(3600)),
        )
    }

    @Test
    fun `since 이후 started_at 인 run 만 status 별로 집계한다`() {
        val since = SINCE_BOUNDARY

        // since 이후 SUCCEEDED 2건, FAILED 1건, RUNNING 1건
        insertRun(status = "SUCCEEDED", startedAt = since.plusSeconds(60))
        insertRun(status = "SUCCEEDED", startedAt = since.plusSeconds(120))
        insertRun(status = "FAILED", startedAt = since.plusSeconds(180))
        insertRun(status = "RUNNING", startedAt = since.plusSeconds(240))
        // since 보다 먼저 시작된 run — 집계에서 제외되어야 한다
        insertRun(status = "SUCCEEDED", startedAt = since.minusSeconds(1))
        insertRun(status = "FAILED", startedAt = since.minusSeconds(3600))

        val result = pipelineRunStore.countByStatusSince(since)

        result["SUCCEEDED"] shouldBe 2L
        result["FAILED"] shouldBe 1L
        result["RUNNING"] shouldBe 1L
        result.size shouldBe 3
    }

    @Test
    fun `since 시점과 정확히 같은 startedAt 도 포함된다 (inclusive 하한)`() {
        val since = SINCE_BOUNDARY

        // started_at == since 인 run 이 집계에 포함되어야 한다 (>= since 비교)
        insertRun(status = "SUCCEEDED", startedAt = since)

        val result = pipelineRunStore.countByStatusSince(since)

        result["SUCCEEDED"] shouldBe 1L
    }

    @Test
    fun `since 이후 run 이 없으면 빈 Map 을 반환한다`() {
        // since 만 지정하고 그 이후 row 는 삽입하지 않는다. setup 에서 해당 구간이 정리된 상태.
        val since = SINCE_BOUNDARY

        val result = pipelineRunStore.countByStatusSince(since)

        result.size shouldBe 0
    }

    /**
     * raw JDBC 로 pipeline_runs row 를 삽입한다.
     * JPA 플러시 시점 차이로 인한 invisible write 를 피한다.
     */
    private fun insertRun(status: String, startedAt: Instant) {
        val now = Instant.now()
        jdbc.update(
            """
            INSERT INTO pipeline_runs
            (id, category_id, category_name, triggered_by, status, orchestration_mode,
             total_collected, total_summarized, total_digest_selected, posted_to_slack,
             started_at, ended_at, duration_ms, error_message, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID().toString(),
            categoryId,
            "count-by-status-test",
            "test",
            status,
            "DETERMINISTIC",
            0,
            0,
            0,
            false,
            Timestamp.from(startedAt),
            null,
            null,
            null,
            Timestamp.from(now),
        )
    }

    companion object {
        /** 테스트 전 구간의 since 기준. 현실적인 2026 년 KST 자정 근방 시점을 사용한다. */
        private val SINCE_BOUNDARY: Instant = Instant.parse("2026-04-17T15:00:00Z")
    }
}
