package com.ohmyclipping.observability

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test

class ClippingMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val schedulerRunTracker = SchedulerRunTracker()
    private val metrics = ClippingMetrics(null, registry, schedulerRunTracker)

    @Test
    fun `recordCollectionSource should track success and failure counters with latency`() {
        metrics.recordCollectionSource(
            sourceId = "src-1",
            categoryId = "cat-1",
            success = true,
            durationMs = 120,
            collected = 5,
            newItems = 3,
            duplicates = 2
        )
        metrics.recordCollectionSource(
            sourceId = "src-1",
            categoryId = "cat-1",
            success = false,
            durationMs = 200,
            collected = 0,
            newItems = 0,
            duplicates = 0
        )

        registry.get("clipping.collect.source.duration")
            .tag("sourceId", "src-1")
            .tag("result", "success")
            .timer().count() shouldBe 1

        registry.get("clipping.collect.source.duration")
            .tag("sourceId", "src-1")
            .tag("result", "failure")
            .timer().count() shouldBe 1

        registry.get("clipping.collect.source.failures")
            .tag("sourceId", "src-1")
            .counter().count() shouldBe 1.0

        registry.get("clipping.collect.items.new")
            .tag("categoryId", "cat-1")
            .counter().count() shouldBe 3.0
    }

    @Test
    fun `recordSummarization should track estimated token usage`() {
        metrics.recordSummarizationCall(
            mode = "article",
            success = true,
            durationMs = 250,
            inputChars = 400,
            outputChars = 200
        )

        registry.get("clipping.summarize.calls")
            .tag("mode", "article")
            .tag("result", "success")
            .counter().count() shouldBe 1.0

        val estimated = registry.get("clipping.summarize.tokens.estimated")
            .tag("mode", "article")
            .summary().totalAmount()
        estimated.shouldBeGreaterThan(100.0)
    }

    @Test
    fun `updateQueueMetrics should expose pending gauge and lag gauge`() {
        metrics.updateQueueMetrics(pendingJobs = 4, oldestPendingLagSeconds = 18)

        registry.get("clipping.jobs.pending").gauge().value() shouldBe 4.0
        registry.get("clipping.jobs.oldest_pending_lag_seconds").gauge().value() shouldBe 18.0
    }

    @Test
    fun `recordRalphLoopResult should track loop run count and iteration summary`() {
        metrics.recordRalphLoopResult(stopReason = "MAX_ITERATIONS_REACHED", iterationCount = 4)

        registry.get("clipping.ralph.loop.runs")
            .tag("stopReason", "MAX_ITERATIONS_REACHED")
            .counter().count() shouldBe 1.0

        registry.get("clipping.ralph.loop.iterations")
            .tag("stopReason", "MAX_ITERATIONS_REACHED")
            .summary().totalAmount() shouldBe 4.0
    }

    @Test
    fun `recordMcpToolCall should track call counter and duration timer`() {
        metrics.recordMcpToolCall(toolName = "list_categories", resultCode = 200, durationMs = 50)

        registry.get("mcp.tool.calls")
            .tag("tool", "list_categories")
            .tag("result_code", "200")
            .counter().count() shouldBe 1.0

        registry.get("mcp.tool.duration")
            .tag("tool", "list_categories")
            .timer().count() shouldBe 1
    }

    @Test
    fun `recordMcpBearerAuthFailure should increment failure counter`() {
        metrics.recordMcpBearerAuthFailure()
        metrics.recordMcpBearerAuthFailure()

        registry.get("mcp.bearer.auth.failures")
            .counter().count() shouldBe 2.0
    }

    @Test
    fun `recordMcpRateLimitRejection should track rejection by tool and actor`() {
        metrics.recordMcpRateLimitRejection(toolName = "send_digest", actor = "service")

        registry.get("mcp.rate_limit.rejections")
            .tag("tool", "send_digest")
            .tag("actor", "service")
            .counter().count() shouldBe 1.0
    }

    @Test
    fun `recordMcpAuditCleanup should increment deleted count`() {
        metrics.recordMcpAuditCleanup(42)

        registry.get("mcp.audit_log.cleanup.deleted")
            .counter().count() shouldBe 42.0
    }

    @Test
    fun `recordMcpAuditCleanupFailure should increment failure counter`() {
        metrics.recordMcpAuditCleanupFailure()

        registry.get("mcp.audit_log.cleanup.failures")
            .counter().count() shouldBe 1.0
    }
}
