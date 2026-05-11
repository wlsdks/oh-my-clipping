package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.store.AsyncJobStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class StuckJobRecoverySchedulerTest {

    private val jobStore = mockk<AsyncJobStore>()
    private val notificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val scheduler = StuckJobRecoveryScheduler(jobStore, notificationService, metrics)

    @Test
    fun `recover는 기본 timeout 30분과 maxAttempts 3으로 호출한다`() {
        every { jobStore.recoverStuck(30, 3) } returns Pair(2, 0)
        scheduler.recover()
        verify(exactly = 1) { jobStore.recoverStuck(30, 3) }
    }

    @Test
    fun `onShutdown은 30초 staleSeconds로 리셋을 호출한다`() {
        every { jobStore.resetStalledRunningToPending(30) } returns 1
        scheduler.onShutdown()
        verify(exactly = 1) { jobStore.resetStalledRunningToPending(30) }
    }

    @Test
    fun `timeout 과 maxAttempts 는 생성자 인자로 override 가능하다`() {
        val customScheduler = StuckJobRecoveryScheduler(
            jobStore, notificationService, metrics,
            stuckTimeoutMinutes = 10, maxAttempts = 5
        )
        every { jobStore.recoverStuck(10, 5) } returns Pair(0, 0)
        customScheduler.recover()
        verify(exactly = 1) { jobStore.recoverStuck(10, 5) }
    }
}
