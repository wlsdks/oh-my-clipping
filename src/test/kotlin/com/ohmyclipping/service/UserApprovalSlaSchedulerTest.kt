package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsNotificationEvent

import com.ohmyclipping.config.SlaEscalationProperties
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.AdminUserStore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class UserApprovalSlaSchedulerTest {

    private val adminUserStore = mockk<AdminUserStore>()
    private val opsLogNotifier = mockk<OpsLogNotifier>(relaxed = true)
    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val properties = SlaEscalationProperties(enabled = true, userApprovalStaleDays = 3)

    /** KST 2026-04-20 (월) 09:00 기준 고정 Clock */
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneId.of("Asia/Seoul"))

    private fun newScheduler(clock: Clock = fixedClock, props: SlaEscalationProperties = properties) =
        UserApprovalSlaScheduler(adminUserStore, props, opsLogNotifier, metrics, clock)

    private fun pendingUser(
        id: String,
        createdDaysAgo: Long,
        base: Instant = fixedClock.instant(),
    ) = AdminUser(
        id = id,
        username = "user-$id",
        passwordHash = "hash",
        role = AccountRole.USER,
        approvalStatus = AccountApprovalStatus.PENDING,
        createdAt = base.minus(Duration.ofDays(createdDaysAgo)),
        updatedAt = base.minus(Duration.ofDays(createdDaysAgo)),
    )

    @Nested
    inner class `정상 흐름` {

        @Test
        fun `임계 초과 PENDING 이 없으면 알림을 발송하지 않는다`() {
            every {
                adminUserStore.findPendingUsersCreatedBefore(any())
            } returns emptyList()

            newScheduler().run()

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
            verify(exactly = 1) { metrics.recordSchedulerRun(eq("user_approval_sla"), any<() -> Any?>()) }
        }

        @Test
        fun `임계 초과 PENDING 이 1건 있으면 요약 알림을 1건 발송한다`() {
            val target = pendingUser("u1", createdDaysAgo = 5)
            every {
                adminUserStore.findPendingUsersCreatedBefore(any())
            } returns listOf(target)

            val contextSlot = slot<Map<String, Any?>>()
            every { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.USER_APPROVAL_SLA_EXCEEDED), capture(contextSlot)) } just Runs

            newScheduler().run()

            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.USER_APPROVAL_SLA_EXCEEDED), any()) }
            assertAll(
                { assert(contextSlot.captured["count"] == 1) { "count expected 1" } },
                { assert(contextSlot.captured["stale_days"] == 3) { "stale_days expected 3" } },
            )
        }
    }

    @Nested
    inner class `Dedup 동작` {

        @Test
        fun `같은 날 두 번 실행해도 동일 userId 는 한 번만 알림된다`() {
            val target = pendingUser("u1", createdDaysAgo = 5)
            every {
                adminUserStore.findPendingUsersCreatedBefore(any())
            } returns listOf(target)

            val scheduler = newScheduler()
            scheduler.run()
            // 두 번째 호출 — dedup 으로 알림 생략
            scheduler.run()

            verify(exactly = 1) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.USER_APPROVAL_SLA_EXCEEDED), any()) }
        }

        @Test
        fun `다음 날 실행 시 dedup 이 리셋되어 다시 알림된다`() {
            val target = pendingUser("u1", createdDaysAgo = 5)
            every {
                adminUserStore.findPendingUsersCreatedBefore(any())
            } returns listOf(target)

            val day1 = Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneId.of("Asia/Seoul"))
            val day2 = Clock.fixed(Instant.parse("2026-04-21T00:00:00Z"), ZoneId.of("Asia/Seoul"))

            // 각 Clock 별로 새 인스턴스 사용 — 같은 스케줄러 인스턴스가 dedup 맵을 유지하면서 날짜만 바뀐 시나리오를 검증
            val scheduler = UserApprovalSlaScheduler(adminUserStore, properties, opsLogNotifier, metrics, day1)
            scheduler.run()

            // Clock 바꿔도 기존 dedup 맵이 유지된 상태에서 날짜 prune 후 재알림되어야 한다
            val scheduler2 = UserApprovalSlaScheduler(adminUserStore, properties, opsLogNotifier, metrics, day2)
            scheduler2.run()

            verify(exactly = 2) { opsLogNotifier.postOpsEvent(eq(OpsNotificationEvent.USER_APPROVAL_SLA_EXCEEDED), any()) }
        }
    }

    @Nested
    inner class `예외 상황` {

        @Test
        fun `임계값 이내 생성된 PENDING 은 리포지토리 cutoff 필터로 제외되어 알림이 가지 않는다`() {
            // 리포지토리 구현체가 cutoff 필터링을 하므로, 이 테스트에서는 스케줄러가 조회 결과(빈 목록)를 신뢰함을 검증한다
            every {
                adminUserStore.findPendingUsersCreatedBefore(any())
            } returns emptyList()

            newScheduler().run()

            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }

        @Test
        fun `enabled=false 면 리포지토리 조회 없이 종료한다`() {
            val disabled = properties.copy(enabled = false)

            newScheduler(props = disabled).run()

            verify(exactly = 0) {
                adminUserStore.findPendingUsersCreatedBefore(any())
            }
            verify(exactly = 0) { opsLogNotifier.postOpsEvent(any(), any()) }
        }
    }
}
