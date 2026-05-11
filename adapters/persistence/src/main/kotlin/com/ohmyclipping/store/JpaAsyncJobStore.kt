package com.ohmyclipping.store

import com.ohmyclipping.entity.AsyncJobEntity
import com.ohmyclipping.model.AsyncJob
import com.ohmyclipping.model.AsyncJobStatus
import com.ohmyclipping.model.AsyncJobType
import com.ohmyclipping.repository.AsyncJobRepository
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * 비동기 작업 큐 JPA 구현. JdbcAsyncJobStore를 대체한다.
 * claim/recover 등 동시성 원자 갱신은 JdbcTemplate을 병용한다.
 */
@Repository
@Primary
class JpaAsyncJobStore(
    private val repository: AsyncJobRepository,
    private val jdbc: JdbcTemplate
) : AsyncJobStore {

    override fun enqueue(type: AsyncJobType, payloadJson: String, maxAttempts: Int): AsyncJob {
        val now = Instant.now()
        val id = UUID.randomUUID().toString()
        val entity = AsyncJobEntity(
            id = id,
            jobType = type.name,
            payloadJson = payloadJson,
            status = AsyncJobStatus.PENDING.name,
            attempts = 0,
            maxAttempts = maxAttempts,
            nextRunAt = now,
            lastError = null,
            resultJson = null,
            createdAt = now,
            updatedAt = now
        )
        repository.save(entity)
        return findById(id) ?: error("Failed to read inserted async job: $id")
    }

    override fun findById(id: String): AsyncJob? =
        repository.findById(id).orElse(null)?.toModel()

    @Transactional
    override fun claimNextDue(now: Instant): AsyncJob? {
        // FOR UPDATE SKIP LOCKED로 동시 워커 간 레이스 컨디션을 방지한다.
        val candidate = jdbc.query(
            """
            SELECT * FROM clipping_jobs
            WHERE status = 'PENDING' AND next_run_at <= ?
            ORDER BY next_run_at, created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
            { rs, _ -> rs.getString("id") },
            Timestamp.from(now)
        ).firstOrNull() ?: return null

        val updated = jdbc.update(
            """
            UPDATE clipping_jobs
            SET status = ?, attempts = attempts + 1, updated_at = ?
            WHERE id = ? AND status = 'PENDING'
            """.trimIndent(),
            AsyncJobStatus.RUNNING.name,
            Timestamp.from(now),
            candidate
        )
        if (updated == 0) return null
        return findById(candidate)
    }

    override fun countPending(): Long =
        repository.countByStatus(AsyncJobStatus.PENDING.name)

    override fun oldestPendingNextRunAt(): Instant? =
        repository.findOldestPendingNextRunAt()

    override fun markSucceeded(id: String, resultJson: String?) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.status = AsyncJobStatus.SUCCEEDED.name
        entity.resultJson = resultJson
        entity.lastError = null
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }

    override fun markRetry(id: String, errorMessage: String, nextRunAt: Instant) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.status = AsyncJobStatus.PENDING.name
        entity.lastError = errorMessage
        entity.nextRunAt = nextRunAt
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }

    override fun markFailed(id: String, errorMessage: String) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.status = AsyncJobStatus.FAILED.name
        entity.lastError = errorMessage
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }

    @Transactional
    override fun recoverStuck(stuckMinutes: Long, maxAttempts: Int): Pair<Int, Int> {
        // 원자적 조건부 일괄 UPDATE는 JdbcTemplate으로 처리한다.
        val now = Instant.now()
        val cutoff = now.minusSeconds(stuckMinutes * 60)
        val nextRunAt = now.plusSeconds(30)

        val recovered = jdbc.update(
            """
            UPDATE clipping_jobs
            SET status = ?, next_run_at = ?, last_error = ?, updated_at = ?
            WHERE status = ? AND updated_at < ? AND attempts < ?
            """.trimIndent(),
            AsyncJobStatus.PENDING.name,
            Timestamp.from(nextRunAt),
            "Auto-recovered: heartbeat timeout",
            Timestamp.from(now),
            AsyncJobStatus.RUNNING.name,
            Timestamp.from(cutoff),
            maxAttempts
        )

        val failed = jdbc.update(
            """
            UPDATE clipping_jobs
            SET status = ?, last_error = ?, updated_at = ?
            WHERE status = ? AND updated_at < ? AND attempts >= ?
            """.trimIndent(),
            AsyncJobStatus.FAILED.name,
            "Auto-failed: max attempts exceeded after stuck recovery",
            Timestamp.from(now),
            AsyncJobStatus.RUNNING.name,
            Timestamp.from(cutoff),
            maxAttempts
        )

        return Pair(recovered, failed)
    }

    @Transactional
    override fun resetStalledRunningToPending(staleSeconds: Long): Int {
        val now = Instant.now()
        val cutoff = now.minusSeconds(staleSeconds)
        val nextRunAt = now.plusSeconds(10)

        return jdbc.update(
            """
            UPDATE clipping_jobs
            SET status = ?, next_run_at = ?, last_error = ?, updated_at = ?
            WHERE status = ? AND updated_at < ?
            """.trimIndent(),
            AsyncJobStatus.PENDING.name,
            Timestamp.from(nextRunAt),
            "Reset on shutdown: stale running job",
            Timestamp.from(now),
            AsyncJobStatus.RUNNING.name,
            Timestamp.from(cutoff)
        )
    }

    @Transactional
    override fun deleteCompletedOlderThan(cutoff: Instant): Int =
        repository.deleteCompletedOlderThan(cutoff)

    override fun listRecent(limit: Int): List<AsyncJob> {
        val safeLimit = limit.coerceIn(1, 100)
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit))
            .map { it.toModel() }
    }

    private fun AsyncJobEntity.toModel() = AsyncJob(
        id = id,
        jobType = AsyncJobType.valueOf(jobType),
        payloadJson = payloadJson,
        status = AsyncJobStatus.valueOf(status),
        attempts = attempts,
        maxAttempts = maxAttempts,
        nextRunAt = nextRunAt,
        lastError = lastError,
        resultJson = resultJson,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
