package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.AsyncJob
import com.clipping.mcpserver.model.AsyncJobStatus
import com.clipping.mcpserver.model.AsyncJobType
import com.clipping.mcpserver.store.AsyncJobStore
import java.time.Instant
import java.util.UUID

class InMemoryAsyncJobStore : AsyncJobStore {

    private val jobs = linkedMapOf<String, AsyncJob>()

    override fun enqueue(type: AsyncJobType, payloadJson: String, maxAttempts: Int): AsyncJob {
        val now = Instant.now()
        val job = AsyncJob(
            id = UUID.randomUUID().toString(),
            jobType = type,
            payloadJson = payloadJson,
            status = AsyncJobStatus.PENDING,
            attempts = 0,
            maxAttempts = maxAttempts,
            nextRunAt = now,
            lastError = null,
            resultJson = null,
            createdAt = now,
            updatedAt = now
        )
        jobs[job.id] = job
        return job
    }

    override fun findById(id: String): AsyncJob? = jobs[id]

    override fun claimNextDue(now: Instant): AsyncJob? {
        val candidate = jobs.values
            .firstOrNull { it.status == AsyncJobStatus.PENDING && !it.nextRunAt.isAfter(now) }
            ?: return null

        val claimed = candidate.copy(
            status = AsyncJobStatus.RUNNING,
            attempts = candidate.attempts + 1,
            updatedAt = now
        )
        jobs[claimed.id] = claimed
        return claimed
    }

    override fun countPending(): Long =
        jobs.values.count { it.status == AsyncJobStatus.PENDING }.toLong()

    override fun oldestPendingNextRunAt(): Instant? =
        jobs.values
            .filter { it.status == AsyncJobStatus.PENDING }
            .map { it.nextRunAt }
            .minOrNull()

    override fun markSucceeded(id: String, resultJson: String?) {
        val current = jobs[id] ?: return
        jobs[id] = current.copy(
            status = AsyncJobStatus.SUCCEEDED,
            resultJson = resultJson,
            updatedAt = Instant.now()
        )
    }

    override fun markRetry(id: String, errorMessage: String, nextRunAt: Instant) {
        val current = jobs[id] ?: return
        jobs[id] = current.copy(
            status = AsyncJobStatus.PENDING,
            lastError = errorMessage,
            nextRunAt = nextRunAt,
            updatedAt = Instant.now()
        )
    }

    override fun markFailed(id: String, errorMessage: String) {
        val current = jobs[id] ?: return
        jobs[id] = current.copy(
            status = AsyncJobStatus.FAILED,
            lastError = errorMessage,
            updatedAt = Instant.now()
        )
    }

    override fun recoverStuck(stuckMinutes: Long, maxAttempts: Int): Pair<Int, Int> = Pair(0, 0)
    override fun resetStalledRunningToPending(staleSeconds: Long): Int = 0

    override fun deleteCompletedOlderThan(cutoff: Instant): Int {
        val toRemove = jobs.values.filter {
            it.status in listOf(AsyncJobStatus.SUCCEEDED, AsyncJobStatus.FAILED) &&
                it.createdAt.isBefore(cutoff)
        }.map { it.id }
        toRemove.forEach { jobs.remove(it) }
        return toRemove.size
    }

    override fun listRecent(limit: Int): List<AsyncJob> =
        jobs.values
            .sortedByDescending { it.createdAt }
            .take(limit.coerceIn(1, 100))
}
