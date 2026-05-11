package com.ohmyclipping.resilience

import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class TokenBucketRateLimiterTest {

    @Nested
    inner class `기본 동작` {

        @Test
        fun `토큰이 있으면 즉시 acquire 성공한다`() {
            val limiter = TokenBucketRateLimiter(
                name = "test",
                permitsPerMinute = 60,
                maxBurst = 10
            )
            val elapsed = measureTimeMillis {
                limiter.acquire()
            }
            elapsed shouldBeLessThan 50
        }

        @Test
        fun `burst 한도까지 즉시 소비할 수 있다`() {
            val limiter = TokenBucketRateLimiter(
                name = "test",
                permitsPerMinute = 60,
                maxBurst = 5
            )
            val elapsed = measureTimeMillis {
                repeat(5) { limiter.acquire() }
            }
            elapsed shouldBeLessThan 100
        }

        @Test
        fun `burst 초과 시 대기한다`() {
            val limiter = TokenBucketRateLimiter(
                name = "test",
                permitsPerMinute = 600,  // 초당 10개
                maxBurst = 2
            )
            repeat(2) { limiter.acquire() }
            val elapsed = measureTimeMillis {
                limiter.acquire()
            }
            (elapsed in 50..300) shouldBe true
        }
    }

    @Nested
    inner class `tryAcquire` {

        @Test
        fun `타임아웃 내에 토큰을 얻으면 true 반환`() {
            val limiter = TokenBucketRateLimiter(
                name = "test",
                permitsPerMinute = 600,
                maxBurst = 1
            )
            limiter.acquire()
            val result = limiter.tryAcquire(timeoutMs = 500)
            result shouldBe true
        }

        @Test
        fun `타임아웃 초과 시 false 반환`() {
            val limiter = TokenBucketRateLimiter(
                name = "test",
                permitsPerMinute = 6,
                maxBurst = 1
            )
            limiter.acquire()
            val result = limiter.tryAcquire(timeoutMs = 100)
            result shouldBe false
        }
    }

    @Nested
    inner class `acquire with maxWaitMs` {

        @Test
        fun `maxWaitMs 초과 시 false 반환`() {
            val limiter = TokenBucketRateLimiter(
                name = "test",
                permitsPerMinute = 6,
                maxBurst = 1,
                maxWaitMs = 200
            )
            limiter.acquire() // burst 소진
            val result = limiter.acquire() // 200ms 이내 토큰 불가 → false
            result shouldBe false
        }
    }

    @Nested
    inner class `모니터링` {

        @Test
        fun `currentWaitCount는 대기 중인 스레드 수를 반환한다`() {
            val limiter = TokenBucketRateLimiter(
                name = "test",
                permitsPerMinute = 6,
                maxBurst = 1
            )
            limiter.acquire()
            limiter.currentWaitCount shouldBe 0

            val thread = Thread {
                limiter.acquire()
            }
            thread.start()
            Thread.sleep(100)
            limiter.currentWaitCount shouldBe 1
            thread.interrupt()
        }
    }
}
