package com.ohmyclipping.resilience

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * InMemoryCircuitBreaker 단위 테스트.
 * MutableClock을 주입하여 시간 의존 로직을 결정적으로 검증한다.
 */
class InMemoryCircuitBreakerTest {

    /** 테스트에서 시간을 자유롭게 조작하기 위한 가변 Clock */
    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun getZone(): java.time.ZoneId = ZoneOffset.UTC

        fun advanceSeconds(seconds: Long) {
            now = now.plusSeconds(seconds)
        }
    }

    private val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))

    private fun createBreaker(
        failureThreshold: Int = 3,
        resetTimeoutSeconds: Long = 10
    ) = InMemoryCircuitBreaker(
        name = "test",
        failureThreshold = failureThreshold,
        resetTimeoutSeconds = resetTimeoutSeconds,
        clock = clock
    )

    @Nested
    inner class `초기 상태` {
        @Test
        fun `초기 상태는 CLOSED이며 호출 허용`() {
            val breaker = createBreaker()

            breaker.state() shouldBe InMemoryCircuitBreaker.State.CLOSED
            breaker.canCall() shouldBe true
        }
    }

    @Nested
    inner class `CLOSED 상태에서 실패 누적` {
        @Test
        fun `임계값 미만 실패는 CLOSED 유지`() {
            val breaker = createBreaker(failureThreshold = 3)

            breaker.recordFailure()
            breaker.recordFailure()

            breaker.state() shouldBe InMemoryCircuitBreaker.State.CLOSED
            breaker.canCall() shouldBe true
        }

        @Test
        fun `임계값 도달 시 OPEN으로 전환되고 호출 차단`() {
            val breaker = createBreaker(failureThreshold = 3)

            repeat(3) { breaker.recordFailure() }

            breaker.state() shouldBe InMemoryCircuitBreaker.State.OPEN
            breaker.canCall() shouldBe false
        }
    }

    @Nested
    inner class `성공 기록` {
        @Test
        fun `성공 시 실패 카운트 초기화 후 CLOSED 유지`() {
            val breaker = createBreaker(failureThreshold = 3)

            // 임계값 직전까지 실패 누적
            breaker.recordFailure()
            breaker.recordFailure()
            breaker.recordSuccess()

            breaker.state() shouldBe InMemoryCircuitBreaker.State.CLOSED

            // 초기화 확인: 다시 2번 실패해도 CLOSED
            breaker.recordFailure()
            breaker.recordFailure()
            breaker.state() shouldBe InMemoryCircuitBreaker.State.CLOSED
        }
    }

    @Nested
    inner class `OPEN에서 HALF_OPEN 전환` {
        @Test
        fun `resetTimeout 경과 전 canCall은 false`() {
            val breaker = createBreaker(failureThreshold = 3, resetTimeoutSeconds = 10)

            repeat(3) { breaker.recordFailure() }
            clock.advanceSeconds(5)

            breaker.canCall() shouldBe false
            breaker.state() shouldBe InMemoryCircuitBreaker.State.OPEN
        }

        @Test
        fun `resetTimeout 경과 후 canCall 호출 시 HALF_OPEN 전환`() {
            val breaker = createBreaker(failureThreshold = 3, resetTimeoutSeconds = 10)

            repeat(3) { breaker.recordFailure() }
            clock.advanceSeconds(10)

            breaker.canCall() shouldBe true
            breaker.state() shouldBe InMemoryCircuitBreaker.State.HALF_OPEN
        }
    }

    @Nested
    inner class `HALF_OPEN 상태` {
        private fun openAndTransitionToHalfOpen(): InMemoryCircuitBreaker {
            val breaker = createBreaker(failureThreshold = 3, resetTimeoutSeconds = 10)
            repeat(3) { breaker.recordFailure() }
            clock.advanceSeconds(10)
            breaker.canCall() // HALF_OPEN으로 전환
            return breaker
        }

        @Test
        fun `HALF_OPEN에서 성공 시 CLOSED로 전환`() {
            val breaker = openAndTransitionToHalfOpen()

            breaker.recordSuccess()

            breaker.state() shouldBe InMemoryCircuitBreaker.State.CLOSED
            breaker.canCall() shouldBe true
        }

        @Test
        fun `HALF_OPEN에서 실패 시 다시 OPEN으로 전환`() {
            val breaker = openAndTransitionToHalfOpen()

            breaker.recordFailure()

            breaker.state() shouldBe InMemoryCircuitBreaker.State.OPEN
            breaker.canCall() shouldBe false
        }
    }

    @Nested
    inner class `지수 백���프` {
        @Test
        fun `HALF_OPEN 실패 반복 시 대기 시간이 지수적으로 증가`() {
            val breaker = createBreaker(failureThreshold = 3, resetTimeoutSeconds = 10)

            // 1차 OPEN (대기: 10초)
            repeat(3) { breaker.recordFailure() }
            breaker.state() shouldBe InMemoryCircuitBreaker.State.OPEN

            clock.advanceSeconds(10)
            breaker.canCall() shouldBe true  // HALF_OPEN
            breaker.recordFailure()          // 다시 OPEN (consecutiveOpenCount = 1)

            // 2차 OPEN → 대기 20초 (10 * 2^1)
            clock.advanceSeconds(10)
            breaker.canCall() shouldBe false  // 10초로는 부족
            clock.advanceSeconds(10)
            breaker.canCall() shouldBe true   // 20초 경과 → HALF_OPEN
            breaker.recordFailure()           // 다시 OPEN (consecutiveOpenCount = 2)

            // 3차 OPEN → 대기 40초 (10 * 2^2)
            clock.advanceSeconds(20)
            breaker.canCall() shouldBe false  // 20초로는 부족
            clock.advanceSeconds(20)
            breaker.canCall() shouldBe true   // 40초 경과 → HALF_OPEN
        }

        @Test
        fun `성공 시 백오프 카운터가 초기화된다`() {
            val breaker = createBreaker(failureThreshold = 3, resetTimeoutSeconds = 10)

            // OPEN → HALF_OPEN → 실패 → OPEN (백오프 증가)
            repeat(3) { breaker.recordFailure() }
            clock.advanceSeconds(10)
            breaker.canCall()
            breaker.recordFailure()

            // HALF_OPEN → 성공 → CLOSED (백오프 초��화)
            clock.advanceSeconds(20)
            breaker.canCall()
            breaker.recordSuccess()
            breaker.state() shouldBe InMemoryCircuitBreaker.State.CLOSED

            // 다시 OPEN 시 대기 시간은 원래 값(10초)
            repeat(3) { breaker.recordFailure() }
            clock.advanceSeconds(10)
            breaker.canCall() shouldBe true  // 10초면 충분 (백오프 초기화됨)
        }

        @Test
        fun `백오프는 maxResetTimeoutSeconds를 초과하지 않는다`() {
            val breaker = InMemoryCircuitBreaker(
                name = "test",
                failureThreshold = 1,
                resetTimeoutSeconds = 10,
                maxResetTimeoutSeconds = 50,
                clock = clock
            )

            // 5번 연속 실패 → 이론적 백오프 = 10 * 2^5 = 320초
            repeat(5) {
                breaker.recordFailure()
                clock.advanceSeconds(1000)  // 충분히 긴 대기
                breaker.canCall()
                breaker.recordFailure()
            }

            // 6번째: maxResetTimeoutSeconds(50초)로 제한
            breaker.recordFailure()
            clock.advanceSeconds(49)
            breaker.canCall() shouldBe false  // 49초 < 50초
            clock.advanceSeconds(1)
            breaker.canCall() shouldBe true   // 50초 = maxResetTimeoutSeconds
        }
    }

    @Nested
    inner class `snapshot 메서드` {
        @Test
        fun `초기 상태에서 snapshot은 CLOSED, canCall=true, 카운트 0을 반환한다`() {
            val breaker = createBreaker()
            val snap = breaker.snapshot()

            snap.state shouldBe InMemoryCircuitBreaker.State.CLOSED
            snap.canCall shouldBe true
            snap.consecutiveOpenCount shouldBe 0
            snap.totalOpenCount shouldBe 0
            snap.openedAt shouldBe null
        }

        @Test
        fun `OPEN 전환 시 totalOpenCount가 1 증가하고 openedAt이 설정된다`() {
            val breaker = createBreaker(failureThreshold = 3)
            repeat(3) { breaker.recordFailure() }

            val snap = breaker.snapshot()
            snap.state shouldBe InMemoryCircuitBreaker.State.OPEN
            snap.canCall shouldBe false
            snap.totalOpenCount shouldBe 1
            snap.openedAt shouldBe clock.instant()
        }

        @Test
        fun `HALF_OPEN에서 실패 후 다시 OPEN이면 totalOpenCount가 2가 된다`() {
            val breaker = createBreaker(failureThreshold = 3, resetTimeoutSeconds = 10)
            repeat(3) { breaker.recordFailure() } // 1st OPEN
            clock.advanceSeconds(10)
            breaker.canCall() // → HALF_OPEN
            breaker.recordFailure() // 2nd OPEN

            val snap = breaker.snapshot()
            snap.totalOpenCount shouldBe 2
            snap.consecutiveOpenCount shouldBe 1
        }

        @Test
        fun `성공으로 CLOSED 복귀 후 다시 OPEN이면 totalOpenCount는 누적된다`() {
            val breaker = createBreaker(failureThreshold = 3, resetTimeoutSeconds = 10)
            repeat(3) { breaker.recordFailure() } // 1st OPEN
            clock.advanceSeconds(10)
            breaker.canCall()
            breaker.recordSuccess() // CLOSED (consecutiveOpenCount 리셋)

            repeat(3) { breaker.recordFailure() } // 2nd OPEN

            val snap = breaker.snapshot()
            snap.totalOpenCount shouldBe 2
            snap.consecutiveOpenCount shouldBe 0 // 성공으로 리셋됨
        }
    }
}
