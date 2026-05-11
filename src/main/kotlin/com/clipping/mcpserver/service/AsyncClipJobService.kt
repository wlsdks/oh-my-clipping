package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.BatchJobExecutionException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.*
import com.clipping.mcpserver.service.dto.clipping.*
import com.clipping.mcpserver.service.pipeline.toCollectResult
import com.clipping.mcpserver.service.pipeline.toSummarizeResult
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.service.port.ClippingPipelinePort
import com.clipping.mcpserver.store.AsyncJobStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import kotlin.math.max

private val log = KotlinLogging.logger {}

@Service
class AsyncClipJobService(
    private val jobStore: AsyncJobStore,
    private val clippingPipelinePort: ClippingPipelinePort,
    private val runtimeSettingService: RuntimeSettingService,
    private val metrics: ClippingMetrics,
    @param:Lazy private val autoCollectionScheduler: AutoCollectionScheduler
) {

    private val mapper = jacksonObjectMapper().apply { findAndRegisterModules() }

    data class CollectPayload(
        val categoryId: String? = null,
        val hoursBack: Int? = null
    )

    data class SummarizePayload(
        val categoryId: String? = null
    )

    fun enqueueCollect(categoryId: String?, hoursBack: Int?): AsyncJobQueuedResult {
        val payload = mapper.writeValueAsString(CollectPayload(categoryId, hoursBack))
        val runtime = runtimeSettingService.current()
        val maxAttempts = runtime.jobMaxAttempts.coerceAtLeast(1)
        val queued = jobStore.enqueue(AsyncJobType.COLLECT, payload, maxAttempts).toQueuedResult()
        refreshQueueMetrics()
        return queued
    }

    fun enqueueSummarize(categoryId: String?): AsyncJobQueuedResult {
        val payload = mapper.writeValueAsString(SummarizePayload(categoryId))
        val runtime = runtimeSettingService.current()
        val maxAttempts = runtime.jobMaxAttempts.coerceAtLeast(1)
        val queued = jobStore.enqueue(AsyncJobType.SUMMARIZE, payload, maxAttempts).toQueuedResult()
        refreshQueueMetrics()
        return queued
    }

    fun getJobStatus(jobId: String): AsyncJobStatusResult {
        val job = jobStore.findById(jobId) ?: throw NotFoundException("Job not found: $jobId")
        return job.toStatusResult()
    }

    fun processDueJobs(maxJobs: Int? = null): Int {
        val runtime = runtimeSettingService.current()
        var processed = 0
        var remaining = max(1, maxJobs ?: runtime.jobWorkerBatchSize)
        while (remaining > 0) {
            val job = jobStore.claimNextDue(Instant.now()) ?: break
            val startedAt = Instant.now()
            processed++
            execute(job, runtime)
            val result = jobStore.findById(job.id)?.status?.name ?: "UNKNOWN"
            val durationMs = Duration.between(startedAt, Instant.now()).toMillis().coerceAtLeast(0)
            metrics.recordAsyncJobProcessed(job.jobType.name, result, durationMs)
            remaining--
        }
        refreshQueueMetrics()
        return processed
    }

    private fun execute(job: AsyncJob, runtime: RuntimeSettingService.RuntimeSettings) {
        try {
            val resultJson = executePayload(job)
            jobStore.markSucceeded(job.id, resultJson)
        } catch (e: BatchJobExecutionException) {
            val errorMessage = e.operationalMessage
            if (job.attempts >= job.maxAttempts) {
                jobStore.markFailed(job.id, errorMessage)
                log.error(e) { "Async job failed permanently ${job.id}: $errorMessage" }
            } else {
                val shift = (job.attempts - 1).coerceAtLeast(0).coerceAtMost(20)
                val backoffSeconds = runtime.jobInitialBackoffSeconds.toLong().coerceAtLeast(1L) * (1L shl shift)
                val nextRunAt = Instant.now().plusSeconds(backoffSeconds)
                jobStore.markRetry(job.id, errorMessage, nextRunAt)
                log.warn(e) { "Async job retry scheduled ${job.id} in ${backoffSeconds}s: $errorMessage" }
            }
        }
    }

    private fun executePayload(job: AsyncJob): String =
        runCatching {
            when (job.jobType) {
                AsyncJobType.COLLECT -> {
                    val payload = mapper.readValue<CollectPayload>(job.payloadJson)
                    val result = clippingPipelinePort.collect(payload.categoryId, payload.hoursBack)
                        .toCollectResult()
                    updateAdaptivePollingState(result)
                    mapper.writeValueAsString(result)
                }

                AsyncJobType.SUMMARIZE -> {
                    val payload = mapper.readValue<SummarizePayload>(job.payloadJson)
                    mapper.writeValueAsString(clippingPipelinePort.summarize(payload.categoryId).toSummarizeResult())
                }
            }
        }.getOrElse { cause ->
            throw BatchJobExecutionException(
                jobId = job.id,
                jobType = job.jobType.name,
                operationalMessage = cause.message ?: "Unknown error",
                cause = cause
            )
        }

    private fun updateAdaptivePollingState(result: CollectResult) {
        for (categoryResult in result.categories) {
            if (categoryResult.newItems > 0) {
                autoCollectionScheduler.markCategoryActive(categoryResult.categoryId)
            } else {
                autoCollectionScheduler.markCategoryInactive(categoryResult.categoryId)
            }
        }
    }

    private fun refreshQueueMetrics() {
        val pending = jobStore.countPending().toInt()
        val oldestPending = jobStore.oldestPendingNextRunAt()
        val lagSeconds = if (oldestPending == null) {
            0L
        } else {
            Duration.between(oldestPending, Instant.now()).seconds.coerceAtLeast(0L)
        }
        metrics.updateQueueMetrics(pending, lagSeconds)
    }

    private fun AsyncJob.toQueuedResult() = AsyncJobQueuedResult(
        jobId = id,
        jobType = jobType.name,
        status = status.name
    )

    /**
     * 최근 비동기 작업 목록을 생성일 역순으로 조회한다.
     * MCP 도구에서 최근 작업 상태를 확인하는 데 사용한다.
     */
    fun listRecentJobs(limit: Int = 10): List<AsyncJobStatusResult> {
        val safeLimit = limit.coerceIn(1, 50)
        return jobStore.listRecent(safeLimit).map { it.toStatusResult() }
    }

    private fun AsyncJob.toStatusResult() = AsyncJobStatusResult(
        id = id,
        jobType = jobType.name,
        status = status.name,
        attempts = attempts,
        maxAttempts = maxAttempts,
        nextRunAt = nextRunAt.toString(),
        lastError = lastError,
        resultJson = resultJson,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
