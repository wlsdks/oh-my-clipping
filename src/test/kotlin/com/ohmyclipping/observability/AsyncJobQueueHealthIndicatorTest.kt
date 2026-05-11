package com.ohmyclipping.observability

import com.ohmyclipping.store.AsyncJobStore
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

/**
 * AsyncJobQueueHealthIndicator 단위 테스트.
 *
 * 대기 작업 수가 임계값(100)을 초과하는지에 따라 Health 상태가 UP ↔ DOWN 으로 전이되는지 검증한다.
 * 임계값은 클래스 상수로 고정되어 있어 테스트도 같은 상수를 참조한다.
 */
class AsyncJobQueueHealthIndicatorTest {

    private val asyncJobStore = mockk<AsyncJobStore>()

    private fun newIndicator(): AsyncJobQueueHealthIndicator =
        AsyncJobQueueHealthIndicator(asyncJobStore)

    @Nested
    inner class `정상 상태 (UP)` {

        @Test
        fun `대기 작업 수가 0이면 UP이며 pendingJobs 세부값이 노출된다`() {
            every { asyncJobStore.countPending() } returns 0

            val health = newIndicator().health()

            health.status shouldBe Status.UP
            health.details shouldContain ("pendingJobs" to 0L)
            health.details shouldContain ("threshold" to AsyncJobQueueHealthIndicator.PENDING_THRESHOLD)
        }

        @Test
        fun `대기 작업 수가 임계값과 정확히 같으면 UP이다 (경계값 포함 정책)`() {
            // countPending <= PENDING_THRESHOLD 일 때 UP — 경계값이 포함됨을 명시적으로 확인
            every { asyncJobStore.countPending() } returns AsyncJobQueueHealthIndicator.PENDING_THRESHOLD.toLong()

            val health = newIndicator().health()

            health.status shouldBe Status.UP
        }
    }

    @Nested
    inner class `장애 상태 (DOWN)` {

        @Test
        fun `대기 작업 수가 임계값을 초과하면 DOWN이며 reason이 포함된다`() {
            // 임계값 + 1 로 설정하여 초과 판정 경계를 명시.
            every { asyncJobStore.countPending() } returns
                (AsyncJobQueueHealthIndicator.PENDING_THRESHOLD + 1).toLong()

            val health = newIndicator().health()

            health.status shouldBe Status.DOWN
            (health.details["reason"] as String) shouldBe
                "Pending job count (${AsyncJobQueueHealthIndicator.PENDING_THRESHOLD + 1}) exceeds threshold " +
                "(${AsyncJobQueueHealthIndicator.PENDING_THRESHOLD})"
        }

        @Test
        fun `대기 작업 수가 임계값의 2배 이상이면 DOWN을 유지한다`() {
            every { asyncJobStore.countPending() } returns
                (AsyncJobQueueHealthIndicator.PENDING_THRESHOLD * 2L + 5)

            val health = newIndicator().health()

            health.status shouldBe Status.DOWN
        }
    }
}
