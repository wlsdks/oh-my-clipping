package com.ohmyclipping.resilience

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 인메모리 서킷 브레이커.
 * CLOSED → OPEN → HALF_OPEN 상태 머신으로 외부 API 장애를 격리한다.
 * Clock 주입으로 테스트에서 시간 의존 로직을 검증 가능.
 */
class InMemoryCircuitBreaker(
    val name: String,
    private val failureThreshold: Int = 5,
    private val resetTimeoutSeconds: Long = 30,
    private val maxResetTimeoutSeconds: Long = 300,
    private val clock: Clock = Clock.systemUTC()
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    /** 특정 시점의 서킷 브레이커 상태를 담는 불변 스냅숏 */
    data class CircuitBreakerSnapshot(
        val state: State,
        val canCall: Boolean,
        val consecutiveOpenCount: Int,
        val totalOpenCount: Int,
        val openedAt: Instant?
    )

    private val _state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val openedAt = AtomicReference<Instant?>(null)
    // 연속 OPEN 전환 횟수. 지수 백오프 계산에 사용한다.
    private val consecutiveOpenCount = AtomicInteger(0)
    // 누적 OPEN 전환 횟수. 시스템 상태 모니터링에 사용한다.
    private val totalOpenCount = AtomicInteger(0)

    fun state(): State = _state.get()

    /** 현재 상태를 스냅숏으로 반환한다. 읽기 경합 없이 일관된 값을 제공한다. */
    fun snapshot(): CircuitBreakerSnapshot = CircuitBreakerSnapshot(
        state = _state.get(),
        canCall = canCall(),
        consecutiveOpenCount = consecutiveOpenCount.get(),
        totalOpenCount = totalOpenCount.get(),
        openedAt = openedAt.get()
    )

    fun canCall(): Boolean {
        return when (_state.get()) {
            State.CLOSED -> true
            State.OPEN -> {
                val opened = openedAt.get() ?: return false
                val elapsed = Duration.between(opened, clock.instant()).seconds
                // 지수 백오프: 연속 OPEN 횟수에 따라 대기 시간을 점진적으로 늘린다.
                val backoffTimeout = (resetTimeoutSeconds * (1L shl consecutiveOpenCount.get().coerceAtMost(4)))
                    .coerceAtMost(maxResetTimeoutSeconds)
                if (elapsed >= backoffTimeout) {
                    _state.set(State.HALF_OPEN)
                    true
                } else {
                    false
                }
            }
            State.HALF_OPEN -> true
        }
    }

    fun recordSuccess() {
        failureCount.set(0)
        consecutiveOpenCount.set(0)
        _state.set(State.CLOSED)
        openedAt.set(null)
    }

    fun recordFailure() {
        val currentState = _state.get()
        // HALF_OPEN 상태에서 실패 시 즉시 OPEN으로 전환하고 백오프 증가
        if (currentState == State.HALF_OPEN) {
            consecutiveOpenCount.incrementAndGet()
            totalOpenCount.incrementAndGet()
            _state.set(State.OPEN)
            openedAt.set(clock.instant())
            return
        }
        // CLOSED 상태에서 실패 누적 후 임계값 도달 시 OPEN 전환
        val count = failureCount.incrementAndGet()
        if (count >= failureThreshold) {
            totalOpenCount.incrementAndGet()
            _state.set(State.OPEN)
            openedAt.set(clock.instant())
        }
    }
}
