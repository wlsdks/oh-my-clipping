package com.ohmyclipping.observability

import com.ohmyclipping.service.port.CollectionMetricsPort
import com.ohmyclipping.service.port.SourceSchedulerMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

@Component
class ClippingMetrics(
    @org.springframework.context.annotation.Lazy private val schedulerErrorNotifier: SchedulerErrorNotifier?,
    private val meterRegistry: MeterRegistry,
    private val schedulerRunTracker: SchedulerRunTracker
) : CollectionMetricsPort, SourceSchedulerMetricsPort {

    private val pendingJobs = AtomicLong(0)
    private val oldestPendingLagSeconds = AtomicLong(0)

    private val collectDurationTimers = ConcurrentHashMap<String, Timer>()
    private val collectItemsCollectedCounters = ConcurrentHashMap<String, Counter>()
    private val collectItemsNewCounters = ConcurrentHashMap<String, Counter>()
    private val collectItemsDuplicatesCounters = ConcurrentHashMap<String, Counter>()
    private val collectSourceFailureCounters = ConcurrentHashMap<String, Counter>()

    private val extractionDurationTimers = ConcurrentHashMap<String, Timer>()
    private val extractionCounters = ConcurrentHashMap<String, Counter>()

    private val summarizeDurationTimers = ConcurrentHashMap<String, Timer>()
    private val summarizeCallCounters = ConcurrentHashMap<String, Counter>()
    private val summarizeTokenSummaries = ConcurrentHashMap<String, DistributionSummary>()

    private val asyncJobDurationTimers = ConcurrentHashMap<String, Timer>()
    private val asyncJobProcessedCounters = ConcurrentHashMap<String, Counter>()
    private val ralphLoopRunCounters = ConcurrentHashMap<String, Counter>()
    private val ralphLoopIterationSummaries = ConcurrentHashMap<String, DistributionSummary>()
    private val schedulerDurationTimers = ConcurrentHashMap<String, Timer>()
    private val schedulerRunCounters = ConcurrentHashMap<String, Counter>()
    private val externalApiTimers = ConcurrentHashMap<String, Timer>()
    private val externalApiCallCounters = ConcurrentHashMap<String, Counter>()
    private val slackRateLimitCounters = ConcurrentHashMap<String, Counter>()

    // --- RSS fetch 캐시 ---
    private val rssCacheHitCounter: Counter = Counter.builder("clipping.rss.cache.hits")
        .description("RSS fetch cache hit count")
        .register(meterRegistry)

    private val rssCacheMissCounter: Counter = Counter.builder("clipping.rss.cache.misses")
        .description("RSS fetch cache miss count")
        .register(meterRegistry)

    // --- AI summary 캐시 ---
    private val summaryCacheHitCounter: Counter = Counter.builder("clipping.ai.summary_cache.hits")
        .description("AI summary cache hit count (Gemini call avoided)")
        .register(meterRegistry)

    private val summaryCacheMissCounter: Counter = Counter.builder("clipping.ai.summary_cache.misses")
        .description("AI summary cache miss count (Gemini call issued)")
        .register(meterRegistry)

    private val mcpToolCallCounters = ConcurrentHashMap<String, Counter>()
    private val mcpToolCallTimers = ConcurrentHashMap<String, Timer>()
    private val mcpBearerAuthFailureCounter = ConcurrentHashMap<String, Counter>()
    private val mcpRateLimitRejectionCounters = ConcurrentHashMap<String, Counter>()
    private val mcpAuditCleanupCounters = ConcurrentHashMap<String, Counter>()
    private val mcpAuditCleanupFailureCounters = ConcurrentHashMap<String, Counter>()

    private val retentionRowsDeletedCounters = ConcurrentHashMap<String, Counter>()
    private val retentionDurationTimers = ConcurrentHashMap<String, Timer>()

    private val dbSizeAlertCounters = ConcurrentHashMap<String, Counter>()

    init {
        Gauge.builder("clipping.jobs.pending", pendingJobs) { it.get().toDouble() }
            .description("Number of pending async clipping jobs")
            .register(meterRegistry)

        Gauge.builder("clipping.jobs.oldest_pending_lag_seconds", oldestPendingLagSeconds) { it.get().toDouble() }
            .description("Lag in seconds of oldest pending clipping job")
            .register(meterRegistry)
    }

    override fun recordCollectionSource(
        sourceId: String,
        categoryId: String,
        success: Boolean,
        durationMs: Long,
        collected: Int,
        newItems: Int,
        duplicates: Int
    ) {
        val result = if (success) "success" else "failure"
        timer(
            collectDurationTimers,
            "clipping.collect.source.duration|$sourceId|$categoryId|$result"
        ) {
            Timer.builder("clipping.collect.source.duration")
                .tag("sourceId", sourceId)
                .tag("categoryId", categoryId)
                .tag("result", result)
                .register(meterRegistry)
        }.record(Duration.ofMillis(durationMs.coerceAtLeast(0)))

        if (success) {
            counter(
                collectItemsCollectedCounters,
                "clipping.collect.items.collected|$categoryId"
            ) {
                Counter.builder("clipping.collect.items.collected")
                    .tag("categoryId", categoryId)
                    .register(meterRegistry)
            }.increment(collected.toDouble().coerceAtLeast(0.0))

            counter(
                collectItemsNewCounters,
                "clipping.collect.items.new|$categoryId"
            ) {
                Counter.builder("clipping.collect.items.new")
                    .tag("categoryId", categoryId)
                    .register(meterRegistry)
            }.increment(newItems.toDouble().coerceAtLeast(0.0))

            counter(
                collectItemsDuplicatesCounters,
                "clipping.collect.items.duplicates|$categoryId"
            ) {
                Counter.builder("clipping.collect.items.duplicates")
                    .tag("categoryId", categoryId)
                    .register(meterRegistry)
            }.increment(duplicates.toDouble().coerceAtLeast(0.0))
        } else {
            counter(
                collectSourceFailureCounters,
                "clipping.collect.source.failures|$sourceId|$categoryId"
            ) {
                Counter.builder("clipping.collect.source.failures")
                    .tag("sourceId", sourceId)
                    .tag("categoryId", categoryId)
                    .register(meterRegistry)
            }.increment()
        }
    }

    override fun recordExtraction(context: String, success: Boolean, durationMs: Long) {
        val result = if (success) "success" else "failure"
        timer(
            extractionDurationTimers,
            "clipping.extract.duration|$context|$result"
        ) {
            Timer.builder("clipping.extract.duration")
                .tag("context", context)
                .tag("result", result)
                .register(meterRegistry)
        }.record(Duration.ofMillis(durationMs.coerceAtLeast(0)))

        counter(
            extractionCounters,
            "clipping.extract.calls|$context|$result"
        ) {
            Counter.builder("clipping.extract.calls")
                .tag("context", context)
                .tag("result", result)
                .register(meterRegistry)
        }.increment()
    }

    fun recordSummarizationCall(mode: String, success: Boolean, durationMs: Long, inputChars: Int, outputChars: Int) {
        val result = if (success) "success" else "failure"
        timer(
            summarizeDurationTimers,
            "clipping.summarize.duration|$mode|$result"
        ) {
            Timer.builder("clipping.summarize.duration")
                .tag("mode", mode)
                .tag("result", result)
                .register(meterRegistry)
        }.record(Duration.ofMillis(durationMs.coerceAtLeast(0)))

        counter(
            summarizeCallCounters,
            "clipping.summarize.calls|$mode|$result"
        ) {
            Counter.builder("clipping.summarize.calls")
                .tag("mode", mode)
                .tag("result", result)
                .register(meterRegistry)
        }.increment()

        val estimatedTokens = estimateTokens(inputChars + outputChars)
        summary(
            summarizeTokenSummaries,
            "clipping.summarize.tokens.estimated|$mode"
        ) {
            DistributionSummary.builder("clipping.summarize.tokens.estimated")
                .tag("mode", mode)
                .register(meterRegistry)
        }.record(estimatedTokens.toDouble())
    }

    fun recordAsyncJobProcessed(jobType: String, result: String, durationMs: Long) {
        timer(
            asyncJobDurationTimers,
            "clipping.jobs.duration|$jobType|$result"
        ) {
            Timer.builder("clipping.jobs.duration")
                .tag("jobType", jobType)
                .tag("result", result)
                .register(meterRegistry)
        }.record(Duration.ofMillis(durationMs.coerceAtLeast(0)))

        counter(
            asyncJobProcessedCounters,
            "clipping.jobs.processed|$jobType|$result"
        ) {
            Counter.builder("clipping.jobs.processed")
                .tag("jobType", jobType)
                .tag("result", result)
                .register(meterRegistry)
        }.increment()
    }

    fun updateQueueMetrics(pendingJobs: Int, oldestPendingLagSeconds: Long) {
        this.pendingJobs.set(pendingJobs.coerceAtLeast(0).toLong())
        this.oldestPendingLagSeconds.set(oldestPendingLagSeconds.coerceAtLeast(0))
    }

    fun recordRalphLoopResult(stopReason: String, iterationCount: Int) {
        val normalizedReason = stopReason.trim().ifBlank { "UNKNOWN" }
        counter(
            ralphLoopRunCounters,
            "clipping.ralph.loop.runs|$normalizedReason"
        ) {
            Counter.builder("clipping.ralph.loop.runs")
                .tag("stopReason", normalizedReason)
                .register(meterRegistry)
        }.increment()

        summary(
            ralphLoopIterationSummaries,
            "clipping.ralph.loop.iterations|$normalizedReason"
        ) {
            DistributionSummary.builder("clipping.ralph.loop.iterations")
                .tag("stopReason", normalizedReason)
                .register(meterRegistry)
        }.record(iterationCount.coerceAtLeast(1).toDouble())
    }

    /**
     * 스케줄러 실행을 측정하고 duration/runs/failures 메트릭을 기록한다.
     * 사용법: `metrics.recordSchedulerRun("data_cleanup") { doCleanup() }`
     */
    fun <T> recordSchedulerRun(schedulerName: String, block: () -> T): T {
        val start = System.nanoTime()
        var success = true
        var caught: Exception? = null
        try {
            return block()
        } catch (e: Exception) {
            success = false
            caught = e
            schedulerErrorNotifier?.notifySchedulerError(schedulerName, e)
            throw e
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            val result = if (success) "success" else "failure"
            timer(
                schedulerDurationTimers,
                "clipping.scheduler.duration|$schedulerName|$result"
            ) {
                Timer.builder("clipping.scheduler.duration")
                    .tag("scheduler", schedulerName)
                    .tag("result", result)
                    .register(meterRegistry)
            }.record(Duration.ofMillis(durationMs.coerceAtLeast(0)))

            counter(
                schedulerRunCounters,
                "clipping.scheduler.runs|$schedulerName|$result"
            ) {
                Counter.builder("clipping.scheduler.runs")
                    .tag("scheduler", schedulerName)
                    .tag("result", result)
                    .register(meterRegistry)
            }.increment()

            // 스케줄러 실행 이력을 인메모리 트래커에 소요시간/에러와 함께 기록한다
            val errorMessage = caught?.let { ex ->
                ex.message?.takeIf { it.isNotBlank() } ?: ex.javaClass.simpleName
            }
            schedulerRunTracker.record(
                name = schedulerName,
                success = success,
                durationMs = durationMs,
                lastError = errorMessage
            )
        }
    }

    override fun <T> recordSourceSchedulerRun(schedulerName: String, block: () -> T): T =
        recordSchedulerRun(schedulerName, block)

    /**
     * 외부 API 호출을 계측하고 duration/calls 메트릭을 기록한다.
     * 사용법: `metrics.recordExternalApiCall("slack", "chat.postMessage") { doCall() }`
     *
     * @param service 외부 서비스 이름 (slack, naver 등)
     * @param method API 메서드명 (chat.postMessage, searchNews 등)
     * @param block 실제 API 호출 블록
     * @return 호출 결과
     */
    fun <T> recordExternalApiCall(service: String, method: String, block: () -> T): T {
        val start = System.nanoTime()
        var success = true
        try {
            return block()
        } catch (e: Exception) {
            success = false
            throw e
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            val result = if (success) "success" else "failure"

            timer(
                externalApiTimers,
                "clipping.external.api.duration|$service|$method|$result"
            ) {
                Timer.builder("clipping.external.api.duration")
                    .tag("service", service)
                    .tag("method", method)
                    .tag("result", result)
                    .register(meterRegistry)
            }.record(Duration.ofMillis(durationMs.coerceAtLeast(0)))

            counter(
                externalApiCallCounters,
                "clipping.external.api.calls|$service|$method|$result"
            ) {
                Counter.builder("clipping.external.api.calls")
                    .tag("service", service)
                    .tag("method", method)
                    .tag("result", result)
                    .register(meterRegistry)
            }.increment()
        }
    }

    /**
     * RSS fetch 캐시 크기 게이지를 등록한다.
     * RssFeedCollector가 @PostConstruct에서 호출하여 실시간 캐시 엔트리 수를 노출한다.
     *
     * @param sizeSupplier 현재 캐시 엔트리 수를 반환하는 함수
     */
    fun registerRssCacheSizeGauge(sizeSupplier: () -> Int) {
        Gauge.builder("clipping.rss.cache.size") { sizeSupplier().toDouble() }
            .description("Current RSS fetch cache entry count")
            .register(meterRegistry)
    }

    fun recordRssCacheHit() {
        rssCacheHitCounter.increment()
    }

    fun recordRssCacheMiss() {
        rssCacheMissCounter.increment()
    }

    fun recordSummaryCacheHit() {
        summaryCacheHitCounter.increment()
    }

    fun recordSummaryCacheMiss() {
        summaryCacheMissCounter.increment()
    }

    /**
     * Slack API 429 rate limit 응답을 카운트한다.
     * @param method 429를 받은 Slack API 메서드명 (chat.postMessage 등)
     */
    fun recordSlackRateLimit(method: String) {
        counter(
            slackRateLimitCounters,
            "clipping.slack.rate_limit|$method"
        ) {
            Counter.builder("clipping.slack.rate_limit")
                .description("Number of Slack API 429 rate limit responses")
                .tag("method", method)
                .register(meterRegistry)
        }.increment()
    }

    /** MCP 도구 호출 카운터와 타이머를 기록한다. */
    fun recordMcpToolCall(toolName: String, resultCode: Int, durationMs: Long) {
        counter(
            mcpToolCallCounters,
            "mcp.tool.calls|$toolName|$resultCode"
        ) {
            Counter.builder("mcp.tool.calls")
                .tag("tool", toolName)
                .tag("result_code", resultCode.toString())
                .register(meterRegistry)
        }.increment()

        timer(
            mcpToolCallTimers,
            "mcp.tool.duration|$toolName"
        ) {
            Timer.builder("mcp.tool.duration")
                .tag("tool", toolName)
                .register(meterRegistry)
        }.record(Duration.ofMillis(durationMs.coerceAtLeast(0)))
    }

    /** MCP Bearer 인증 실패 카운터를 기록한다. */
    fun recordMcpBearerAuthFailure() {
        counter(
            mcpBearerAuthFailureCounter,
            "mcp.bearer.auth.failures"
        ) {
            Counter.builder("mcp.bearer.auth.failures")
                .description("MCP Bearer 인증 실패 횟수")
                .register(meterRegistry)
        }.increment()
    }

    /** MCP 레이트 리밋 거부 카운터를 기록한다. */
    fun recordMcpRateLimitRejection(toolName: String, actor: String) {
        counter(
            mcpRateLimitRejectionCounters,
            "mcp.rate_limit.rejections|$toolName|$actor"
        ) {
            Counter.builder("mcp.rate_limit.rejections")
                .tag("tool", toolName)
                .tag("actor", actor)
                .register(meterRegistry)
        }.increment()
    }

    /** MCP 감사 로그 정리 결과를 기록한다. */
    fun recordMcpAuditCleanup(deleted: Int) {
        counter(
            mcpAuditCleanupCounters,
            "mcp.audit_log.cleanup.deleted"
        ) {
            Counter.builder("mcp.audit_log.cleanup.deleted")
                .description("MCP 감사 로그 정리 삭제 건수")
                .register(meterRegistry)
        }.increment(deleted.toDouble())
    }

    /** MCP 감사 로그 정리 실패를 기록한다. */
    fun recordMcpAuditCleanupFailure() {
        counter(
            mcpAuditCleanupFailureCounters,
            "mcp.audit_log.cleanup.failures"
        ) {
            Counter.builder("mcp.audit_log.cleanup.failures")
                .description("MCP 감사 로그 정리 실패 횟수")
                .register(meterRegistry)
        }.increment()
    }

    /**
     * Retention 실행 결과를 기록한다 — 테이블별 삭제 row 수 및 실행 시간.
     *
     * @param table "batch_summaries" 또는 "rss_items"
     * @param rowsDeleted 실제 삭제된 row 수
     * @param durationMs 실행 시간 (ms)
     */
    fun recordRetentionRun(table: String, rowsDeleted: Long, durationMs: Long) {
        counter(
            retentionRowsDeletedCounters,
            "clipping.retention.rows_deleted|$table"
        ) {
            Counter.builder("clipping.retention.rows_deleted")
                .tag("table", table)
                .description("Retention 실행으로 삭제된 row 수 (테이블별)")
                .register(meterRegistry)
        }.increment(rowsDeleted.toDouble().coerceAtLeast(0.0))

        timer(
            retentionDurationTimers,
            "clipping.retention.duration|$table"
        ) {
            Timer.builder("clipping.retention.duration")
                .tag("table", table)
                .description("Retention 실행 소요 시간 (ms)")
                .register(meterRegistry)
        }.record(Duration.ofMillis(durationMs.coerceAtLeast(0)))
    }

    /**
     * DB 크기 임계 초과로 Slack 알림이 발송된 횟수를 카운트한다.
     *
     * @param level 알림 수준 ("critical")
     */
    fun recordDbSizeAlertFired(level: String) {
        counter(
            dbSizeAlertCounters,
            "clipping.db_size.slack_alerts_fired|$level"
        ) {
            Counter.builder("clipping.db_size.slack_alerts_fired")
                .tag("level", level)
                .description("DB 용량 임계 초과로 Slack 알림 발송된 횟수")
                .register(meterRegistry)
        }.increment()
    }

    private fun estimateTokens(chars: Int): Long =
        ceil(chars.coerceAtLeast(0).toDouble() / 4.0).toLong()

    companion object {
        /** 메트릭 캐시 최대 엔트리 수 — 초과 시 새 메트릭 등록을 건너뛴다 */
        private const val MAX_METRIC_ENTRIES = 500
    }

    private fun timer(cache: ConcurrentHashMap<String, Timer>, key: String, factory: () -> Timer): Timer {
        cache[key]?.let { return it }
        if (cache.size >= MAX_METRIC_ENTRIES) return Timer.builder("clipping.overflow").register(meterRegistry)
        return cache.computeIfAbsent(key) { factory() }
    }

    private fun counter(cache: ConcurrentHashMap<String, Counter>, key: String, factory: () -> Counter): Counter {
        cache[key]?.let { return it }
        if (cache.size >= MAX_METRIC_ENTRIES) return Counter.builder("clipping.overflow").register(meterRegistry)
        return cache.computeIfAbsent(key) { factory() }
    }

    private fun summary(
        cache: ConcurrentHashMap<String, DistributionSummary>,
        key: String,
        factory: () -> DistributionSummary
    ): DistributionSummary = cache.computeIfAbsent(key) { factory() }
}
