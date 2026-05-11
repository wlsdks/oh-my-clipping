package com.ohmyclipping.service.source

import com.ohmyclipping.model.RssSource
import com.ohmyclipping.service.port.OpsNotificationEvent
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.service.port.SourceSchedulerMetricsPort
import com.ohmyclipping.service.port.SourceSlaSettings
import com.ohmyclipping.service.port.SourceSlaSettingsPort
import com.ohmyclipping.store.RssSourceStore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class SourceRequestSlaSchedulerTest {

    private val sourceStore = mockk<RssSourceStore>()
    private val opsLogNotifier = mockk<OpsLogNotifier>(relaxed = true)
    private val sourceSlaSettingsPort = mockk<SourceSlaSettingsPort>()
    private val metrics = mockk<SourceSchedulerMetricsPort>(relaxed = true) {
        every { recordSourceSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val properties = SourceSlaSettings(enabled = true, sourceRequestStaleDays = 5)

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneId.of("Asia/Seoul"))

    private fun newScheduler(clock: Clock = fixedClock, props: SourceSlaSettings = properties): SourceRequestSlaScheduler {
        every { sourceSlaSettingsPort.currentSourceSlaSettings() } returns props
        return SourceRequestSlaScheduler(sourceStore, sourceSlaSettingsPort, opsLogNotifier, metrics, clock)
    }

    private fun pendingSource(id: String, createdDaysAgo: Long) = RssSource(
        id = id,
        name = "소스-$id",
        url = "https://example.com/rss-$id",
        categoryId = "cat-$id",
        verificationStatus = "PENDING",
        createdAt = fixedClock.instant().minus(Duration.ofDays(createdDaysAgo)),
        updatedAt = fixedClock.instant().minus(Duration.ofDays(createdDaysAgo)),
        systemUpdatedAt = fixedClock.instant().minus(Duration.ofDays(createdDaysAgo)),
    )

    @Nested
    inner class `알림 발송 조건` {

        @Test
        fun `임계 초과 PENDING 이 없으면 알림을 발송하지 않는다`() {
            every { sourceStore.findPendingVerificationCreatedBefore(any()) } returns emptyList()

            newScheduler().run()

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }

        @Test
        fun `임계 초과 PENDING 이 1건 있으면 요약 알림이 1건 간다`() {
            every {
                sourceStore.findPendingVerificationCreatedBefore(any())
            } returns listOf(pendingSource("s1", 10))

            val ctx = slot<Map<String, Any?>>()
            every { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.SOURCE_REQUEST_SLA_EXCEEDED), capture(ctx)) } just Runs

            newScheduler().run()

            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.SOURCE_REQUEST_SLA_EXCEEDED), any()) }
            assert(ctx.captured["count"] == 1)
            assert(ctx.captured["stale_days"] == 5)
        }
    }

    @Nested
    inner class `Dedup 동작` {

        @Test
        fun `같은 날 두 번 실행해도 동일 sourceId 는 한 번만 알림된다`() {
            every {
                sourceStore.findPendingVerificationCreatedBefore(any())
            } returns listOf(pendingSource("s1", 10))

            val scheduler = newScheduler()
            scheduler.run()
            scheduler.run()

            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.SOURCE_REQUEST_SLA_EXCEEDED), any()) }
        }

        @Test
        fun `임계값 이내 PENDING 은 repository 쿼리에서 필터되어 알림이 가지 않는다`() {
            every {
                sourceStore.findPendingVerificationCreatedBefore(any())
            } returns emptyList()

            newScheduler().run()

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }
    }

    @Nested
    inner class `토글 스위치` {

        @Test
        fun `enabled=false 면 repository 조회 없이 종료한다`() {
            val disabled = properties.copy(enabled = false)

            newScheduler(props = disabled).run()

            verify(exactly = 0) { sourceStore.findPendingVerificationCreatedBefore(any()) }
            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }
    }
}
