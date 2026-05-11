package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsNotificationEvent

import com.ohmyclipping.config.SlaEscalationProperties
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.UserClippingRequestStore
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

class ClippingRequestSlaSchedulerTest {

    private val requestStore = mockk<UserClippingRequestStore>()
    private val opsLogNotifier = mockk<OpsLogNotifier>(relaxed = true)
    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val properties = SlaEscalationProperties(enabled = true, clippingRequestStaleDays = 7)

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneId.of("Asia/Seoul"))

    private fun newScheduler(clock: Clock = fixedClock, props: SlaEscalationProperties = properties) =
        ClippingRequestSlaScheduler(requestStore, props, opsLogNotifier, metrics, clock)

    private fun pending(id: String, createdDaysAgo: Long) = UserClippingRequest(
        id = id,
        requesterUserId = "user-$id",
        requestName = "요청-$id",
        sourceName = "소스-$id",
        sourceUrl = "https://example.com/rss-$id",
        slackChannelId = "C123",
        personaName = "persona",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.PENDING,
        createdAt = fixedClock.instant().minus(Duration.ofDays(createdDaysAgo)),
        updatedAt = fixedClock.instant().minus(Duration.ofDays(createdDaysAgo)),
    )

    @Nested
    inner class `알림 발송 조건` {

        @Test
        fun `임계 초과 PENDING 이 없으면 알림을 발송하지 않는다`() {
            every { requestStore.findPendingCreatedBefore(any()) } returns emptyList()

            newScheduler().run()

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }

        @Test
        fun `임계 초과 PENDING 이 2건 있으면 count=2 의 요약 알림이 1건 간다`() {
            every { requestStore.findPendingCreatedBefore(any()) } returns
                listOf(pending("r1", 10), pending("r2", 8))

            val ctx = slot<Map<String, Any?>>()
            every { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.CLIPPING_REQUEST_SLA_EXCEEDED), capture(ctx)) } just Runs

            newScheduler().run()

            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.CLIPPING_REQUEST_SLA_EXCEEDED), any()) }
            assert(ctx.captured["count"] == 2)
            assert(ctx.captured["stale_days"] == 7)
        }
    }

    @Nested
    inner class `Dedup 동작` {

        @Test
        fun `같은 날 두 번 실행해도 동일 requestId 는 한 번만 알림된다`() {
            every { requestStore.findPendingCreatedBefore(any()) } returns listOf(pending("r1", 10))

            val scheduler = newScheduler()
            scheduler.run()
            scheduler.run()

            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.CLIPPING_REQUEST_SLA_EXCEEDED), any()) }
        }

        @Test
        fun `임계값 이내 PENDING 은 repository 쿼리에서 필터되어 알림이 가지 않는다`() {
            // repository 레벨에서 cutoff 조건으로 필터링되는 동작을 시뮬레이션
            every { requestStore.findPendingCreatedBefore(any()) } returns emptyList()

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

            verify(exactly = 0) { requestStore.findPendingCreatedBefore(any()) }
            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }
    }
}
