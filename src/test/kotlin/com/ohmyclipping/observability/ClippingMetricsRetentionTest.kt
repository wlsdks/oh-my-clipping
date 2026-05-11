package com.ohmyclipping.observability

import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test

class ClippingMetricsRetentionTest {

    private val registry = SimpleMeterRegistry()
    private val schedulerRunTracker = SchedulerRunTracker()
    private val metrics = ClippingMetrics(null, registry, schedulerRunTracker)

    @Test
    fun `recordRetentionRun batch_summaries tag rowsDeleted counter durationMs timer 증가`() {
        metrics.recordRetentionRun(
            table = "batch_summaries",
            rowsDeleted = 42,
            durationMs = 350
        )

        // 삭제된 row 수 카운터
        registry.get("clipping.retention.rows_deleted")
            .tag("table", "batch_summaries")
            .counter().count() shouldBe 42.0

        // 실행 시간 타이머
        registry.get("clipping.retention.duration")
            .tag("table", "batch_summaries")
            .timer().count() shouldBe 1
    }

    @Test
    fun `recordRetentionRun rss_items tag 로 구분되어 각각 카운트된다`() {
        // batch_summaries 1회
        metrics.recordRetentionRun(
            table = "batch_summaries",
            rowsDeleted = 10,
            durationMs = 100
        )

        // rss_items 1회
        metrics.recordRetentionRun(
            table = "rss_items",
            rowsDeleted = 25,
            durationMs = 200
        )

        // 각 테이블별로 독립적으로 기록되어야 함
        registry.get("clipping.retention.rows_deleted")
            .tag("table", "batch_summaries")
            .counter().count() shouldBe 10.0

        registry.get("clipping.retention.rows_deleted")
            .tag("table", "rss_items")
            .counter().count() shouldBe 25.0

        // 타이머도 각각 1회씩
        registry.get("clipping.retention.duration")
            .tag("table", "batch_summaries")
            .timer().count() shouldBe 1

        registry.get("clipping.retention.duration")
            .tag("table", "rss_items")
            .timer().count() shouldBe 1
    }

}
