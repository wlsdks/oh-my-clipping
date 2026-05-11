package com.clipping.mcpserver.store

import com.clipping.mcpserver.service.dto.pipeline.PipelineRunEntity as PipelineRunModel
import com.clipping.mcpserver.service.dto.pipeline.PipelineRunStatus
import com.clipping.mcpserver.service.dto.clipping.PipelineStepStatus
import com.clipping.mcpserver.service.dto.pipeline.PipelineStepTraceEntity as PipelineStepTraceModel
import com.clipping.mcpserver.entity.PipelineRunEntity as PipelineRunJpaEntity
import com.clipping.mcpserver.entity.PipelineStepTraceEntity as PipelineStepTraceJpaEntity
import com.clipping.mcpserver.repository.PipelineRunRepository
import com.clipping.mcpserver.repository.PipelineStepTraceRepository
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * 파이프라인 실행 이력 JPA 구현. JdbcPipelineRunStore를 대체한다.
 * 동적 WHERE 조합은 JdbcTemplate을 병용한다.
 */
@Repository
@Primary
class JpaPipelineRunStore(
    private val runRepository: PipelineRunRepository,
    private val stepTraceRepository: PipelineStepTraceRepository,
    private val jdbc: JdbcTemplate
) : PipelineRunStore {

    override fun save(run: PipelineRunModel): PipelineRunModel {
        val id = run.id.ifBlank { UUID.randomUUID().toString() }
        val saved = run.copy(id = id, createdAt = Instant.now())
        runRepository.save(saved.toEntity())
        return saved
    }

    override fun update(run: PipelineRunModel) {
        val entity = runRepository.findById(run.id).orElse(null) ?: return
        entity.status = run.status.name
        entity.totalCollected = run.totalCollected
        entity.totalSummarized = run.totalSummarized
        entity.totalDigestSelected = run.totalDigestSelected
        entity.postedToSlack = run.postedToSlack
        entity.endedAt = run.endedAt
        entity.durationMs = run.durationMs
        entity.errorMessage = run.errorMessage
        runRepository.save(entity)
    }

    override fun findById(id: String): PipelineRunModel? =
        runRepository.findById(id).orElse(null)?.toModel()

    override fun findLatestByCategoryId(categoryId: String, limit: Int): List<PipelineRunModel> =
        runRepository.findByCategoryIdOrderByCreatedAtDesc(
            categoryId,
            PageRequest.of(0, limit)
        ).map { it.toModel() }

    override fun findAll(
        categoryId: String?,
        status: String?,
        since: Instant?,
        offset: Int,
        limit: Int,
        categoryIds: Collection<String>?
    ): List<PipelineRunModel> {
        // 빈 카테고리 컬렉션이면 SQL을 실행하지 않고 즉시 빈 리스트를 반환한다.
        if (categoryIds != null && categoryIds.isEmpty()) return emptyList()

        // 동적 WHERE 조합이므로 JdbcTemplate으로 처리한다.
        val (whereClause, params) = buildWhereClause(categoryId, status, since, categoryIds)
        val sql = """
            SELECT * FROM pipeline_runs
            WHERE $whereClause
            ORDER BY started_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        val allParams = params + listOf(limit, offset)
        return jdbc.query(sql, { rs, _ -> mapRunRowOrNull(rs) }, *allParams.toTypedArray())
            .mapNotNull { it }
    }

    override fun countAll(
        categoryId: String?,
        status: String?,
        since: Instant?,
        categoryIds: Collection<String>?
    ): Int {
        // 빈 카테고리 컬렉션이면 SQL을 실행하지 않고 0을 반환한다.
        if (categoryIds != null && categoryIds.isEmpty()) return 0

        val (whereClause, params) = buildWhereClause(categoryId, status, since, categoryIds)
        val sql = "SELECT COUNT(*) FROM pipeline_runs WHERE $whereClause"
        return jdbc.queryForObject(sql, Int::class.java, *params.toTypedArray()) ?: 0
    }

    override fun saveStepTrace(trace: PipelineStepTraceModel): PipelineStepTraceModel {
        val id = trace.id.ifBlank { UUID.randomUUID().toString() }
        val saved = trace.copy(id = id, createdAt = Instant.now())
        stepTraceRepository.save(saved.toEntity())
        return saved
    }

    override fun updateStepTrace(trace: PipelineStepTraceModel) {
        val entity = stepTraceRepository.findById(trace.id).orElse(null) ?: return
        entity.status = trace.status.name
        entity.endedAt = trace.endedAt
        entity.durationMs = trace.durationMs
        entity.detail = trace.detail
        stepTraceRepository.save(entity)
    }

    override fun findStepTracesByRunId(runId: String): List<PipelineStepTraceModel> =
        // 단일 실행 상세 조회는 DB에서 run_id 조건과 started_at 정렬을 처리한다.
        stepTraceRepository.findByRunIdOrderByStartedAtAsc(runId)
            .map { it.toModel() }

    @Transactional
    override fun deleteOlderThan(cutoff: Instant): Int {
        val cutoffTs = Timestamp.from(cutoff)
        // 자식 테이블(step_traces)을 먼저 삭제한다.
        jdbc.update(
            "DELETE FROM pipeline_step_traces WHERE run_id IN (SELECT id FROM pipeline_runs WHERE created_at < ?)",
            cutoffTs
        )
        return runRepository.deleteByCreatedAtBefore(cutoff)
    }

    override fun countRunningByCategoryId(categoryId: String): Int =
        runRepository.countByCategoryIdAndStatus(categoryId, PipelineRunStatus.RUNNING.name)

    override fun findLatestFailedByCategory(categoryId: String, cutoff: Instant): PipelineRunModel? =
        runRepository.findLatestFailedByCategory(categoryId, cutoff, PageRequest.of(0, 1))
            .firstOrNull()
            ?.toModel()

    override fun findRecentByCategory(categoryId: String, limit: Int): List<PipelineRunModel> =
        runRepository.findByCategoryIdOrderByEndedAtDesc(categoryId, PageRequest.of(0, limit))
            .map { it.toModel() }

    @Transactional
    override fun updateSlackThread(runId: String, threadTs: String, payloadJson: String) {
        runRepository.updateSlackThread(runId, threadTs, payloadJson)
    }

    override fun hasRunStartedBetween(lower: Instant, upper: Instant): Boolean =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pipeline_runs WHERE started_at BETWEEN ? AND ?",
            Int::class.java,
            java.sql.Timestamp.from(lower),
            java.sql.Timestamp.from(upper),
        )?.let { it > 0 } ?: false

    override fun findFailureCountsPerCategorySince(since: Instant, minFailures: Int): List<CategoryFailureSummary> {
        // 카테고리별 FAILED 건수를 집계해 minFailures 이상인 항목만 반환한다
        return jdbc.query(
            """
            SELECT category_id, category_name, COUNT(*) AS failure_count
            FROM pipeline_runs
            WHERE status = 'FAILED' AND ended_at >= ?
            GROUP BY category_id, category_name
            HAVING COUNT(*) >= ?
            ORDER BY failure_count DESC
            """.trimIndent(),
            { rs, _ ->
                CategoryFailureSummary(
                    categoryId = rs.getString("category_id"),
                    categoryName = rs.getString("category_name") ?: "",
                    failureCount = rs.getInt("failure_count"),
                )
            },
            java.sql.Timestamp.from(since),
            minFailures,
        )
    }

    override fun findTopFailingSourcesSince(since: Instant, limit: Int): List<CategoryFailureSummary> {
        // 실패 건수 기준 상위 N개 카테고리를 반환한다
        return jdbc.query(
            """
            SELECT category_id, category_name, COUNT(*) AS failure_count
            FROM pipeline_runs
            WHERE status = 'FAILED' AND ended_at >= ?
            GROUP BY category_id, category_name
            ORDER BY failure_count DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                CategoryFailureSummary(
                    categoryId = rs.getString("category_id"),
                    categoryName = rs.getString("category_name") ?: "",
                    failureCount = rs.getInt("failure_count"),
                )
            },
            java.sql.Timestamp.from(since),
            limit,
        )
    }

    override fun findDurationsBetween(from: Instant, to: Instant): List<Long> {
        // 범위 내 종료된 실행의 duration_ms 목록을 반환한다 (null 제외)
        return jdbc.queryForList(
            "SELECT duration_ms FROM pipeline_runs WHERE ended_at BETWEEN ? AND ? AND duration_ms IS NOT NULL",
            Long::class.java,
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to),
        )
    }

    override fun countByStatusSince(since: Instant): Map<String, Long> =
        // status 별 GROUP BY 집계 단일 쿼리 — started_at >= since
        jdbc.query(
            """
            SELECT status, COUNT(*) AS cnt
            FROM pipeline_runs
            WHERE started_at >= ?
            GROUP BY status
            """.trimIndent(),
            { rs, _ ->
                val status = rs.getString("status") ?: return@query null
                status to rs.getLong("cnt")
            },
            Timestamp.from(since)
        ).mapNotNull { it }.toMap()

    // ── private helpers ──

    private fun buildWhereClause(
        categoryId: String?,
        status: String?,
        since: Instant? = null,
        categoryIds: Collection<String>? = null
    ): Pair<String, List<Any>> {
        val conditions = mutableListOf("1 = 1")
        val params = mutableListOf<Any>()
        if (!categoryId.isNullOrBlank()) {
            conditions += "category_id = ?"
            params += categoryId
        }
        if (!status.isNullOrBlank()) {
            conditions += "status = ?"
            params += status
        }
        // within 파라미터에서 변환된 startedAt 하한 필터를 적용한다.
        if (since != null) {
            conditions += "started_at >= ?"
            params += Timestamp.from(since)
        }
        // categoryIds는 IN 절로 처리한다. 교집합 의미로 categoryId 조건과 함께 AND로 결합된다.
        if (!categoryIds.isNullOrEmpty()) {
            val placeholders = categoryIds.joinToString(",") { "?" }
            conditions += "category_id IN ($placeholders)"
            params.addAll(categoryIds)
        }
        return conditions.joinToString(" AND ") to params
    }

    private fun mapRunRowOrNull(rs: java.sql.ResultSet): PipelineRunModel? {
        val id = rs.getString("id") ?: return null
        val categoryId = rs.getString("category_id") ?: return null
        val status = parsePipelineRunStatus(rs.getString("status")) ?: return null
        val startedAt = rs.getTimestamp("started_at")?.toInstant() ?: return null
        val createdAt = rs.getTimestamp("created_at")?.toInstant() ?: startedAt
        return PipelineRunModel(
            id = id,
            categoryId = categoryId,
            categoryName = rs.getString("category_name") ?: "",
            triggeredBy = rs.getString("triggered_by") ?: "",
            status = status,
            orchestrationMode = rs.getString("orchestration_mode") ?: "",
            totalCollected = rs.getInt("total_collected"),
            totalSummarized = rs.getInt("total_summarized"),
            totalDigestSelected = rs.getInt("total_digest_selected"),
            postedToSlack = rs.getBoolean("posted_to_slack"),
            startedAt = startedAt,
            endedAt = rs.getTimestamp("ended_at")?.toInstant(),
            durationMs = rs.getObject("duration_ms") as? Long,
            errorMessage = rs.getString("error_message"),
            createdAt = createdAt
        )
    }

    private fun parsePipelineRunStatus(value: String?): PipelineRunStatus? =
        if (value.isNullOrBlank()) {
            null
        } else {
            runCatching { PipelineRunStatus.valueOf(value) }.getOrNull()
        }

    private fun PipelineRunJpaEntity.toModel() = PipelineRunModel(
        id = id,
        categoryId = categoryId,
        categoryName = categoryName ?: "",
        triggeredBy = triggeredBy ?: "",
        status = PipelineRunStatus.valueOf(status),
        orchestrationMode = orchestrationMode ?: "",
        totalCollected = totalCollected ?: 0,
        totalSummarized = totalSummarized ?: 0,
        totalDigestSelected = totalDigestSelected ?: 0,
        postedToSlack = postedToSlack ?: false,
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        errorMessage = errorMessage,
        createdAt = createdAt,
        slackThreadTs = slackThreadTs,
    )

    private fun PipelineRunModel.toEntity() = PipelineRunJpaEntity(
        id = id,
        categoryId = categoryId,
        categoryName = categoryName,
        triggeredBy = triggeredBy,
        status = status.name,
        orchestrationMode = orchestrationMode,
        totalCollected = totalCollected,
        totalSummarized = totalSummarized,
        totalDigestSelected = totalDigestSelected,
        postedToSlack = postedToSlack,
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        errorMessage = errorMessage,
        createdAt = createdAt
    )

    private fun PipelineStepTraceJpaEntity.toModel() = PipelineStepTraceModel(
        id = id,
        runId = runId,
        step = step,
        status = PipelineStepStatus.valueOf(status),
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        detail = detail,
        createdAt = createdAt
    )

    private fun PipelineStepTraceModel.toEntity() = PipelineStepTraceJpaEntity(
        id = id,
        runId = runId,
        step = step,
        status = status.name,
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        detail = detail,
        createdAt = createdAt
    )
}
