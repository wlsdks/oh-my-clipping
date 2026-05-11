package com.ohmyclipping.resilience

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

private val log = KotlinLogging.logger {}

/**
 * Token Bucket 기반 Rate Limiter.
 * 분당 허용 요청 수(permitsPerMinute)를 초과하지 않도록 호출을 조절한다.
 * burst 허용분을 초과하면 토큰이 충전될 때까지 호출 스레드를 block한다.
 *
 * maxWaitMs를 초과하면 acquire()가 false를 반환하여 무한 대기를 방지한다.
 */
class TokenBucketRateLimiter(
    val name: String,
    private val permitsPerMinute: Int,
    private val maxBurst: Int = permitsPerMinute.coerceAtMost(20),
    private val maxWaitMs: Long = 60_000,
) {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val _waitCount = AtomicInteger(0)

    private val refillIntervalNanos: Long = (60_000_000_000L / permitsPerMinute)

    private var availableTokens: Double = maxBurst.toDouble()
    private var lastRefillNanos: Long = System.nanoTime()

    /** 현재 대기 중인 스레드 수 */
    val currentWaitCount: Int get() = _waitCount.get()

    /**
     * 토큰 1개를 소비한다. 토큰이 없으면 최대 maxWaitMs까지 대기.
     * @return true=토큰 획득, false=타임아웃
     */
    fun acquire(): Boolean {
        return tryAcquire(maxWaitMs)
    }

    /**
     * 타임아웃 내에 토큰을 얻으면 true, 초과하면 false.
     */
    fun tryAcquire(timeoutMs: Long): Boolean {
        val deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000
        lock.withLock {
            refill()
            while (availableTokens < 1.0) {
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0) return false
                // 다음 토큰 충전까지 필요한 대기 시간 계산
                val tokensNeeded = 1.0 - availableTokens
                val nanosUntilToken = (tokensNeeded * refillIntervalNanos).toLong()
                val waitNanos = minOf(remaining, nanosUntilToken)
                _waitCount.incrementAndGet()
                try {
                    condition.awaitNanos(waitNanos)
                } finally {
                    _waitCount.decrementAndGet()
                }
                refill()
            }
            availableTokens -= 1.0
            return true
        }
    }

    /** 경과 시간에 비례해 토큰을 충전한다. lock 보유 상태에서만 호출. */
    private fun refill() {
        val now = System.nanoTime()
        val elapsed = now - lastRefillNanos
        if (elapsed <= 0) return
        val newTokens = elapsed.toDouble() / refillIntervalNanos
        availableTokens = min(availableTokens + newTokens, maxBurst.toDouble())
        lastRefillNanos = now
    }
}
