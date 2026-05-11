package com.clipping.mcpserver.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.ZSetOperations
import java.time.Duration
import io.kotest.matchers.shouldBe

class RedisRateLimitServiceTest {

    private val zSetOps = mockk<ZSetOperations<String, String>>(relaxed = true)
    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)

    init {
        every { redisTemplate.opsForZSet() } returns zSetOps
        every { redisTemplate.opsForValue() } returns valueOps
    }

    private val sut = RedisRateLimitService(redisTemplate)

    @Nested
    inner class `isRateLimited 테스트` {

        @Test
        fun `요청 수가 제한 미만이면 false를 반환하고 요청을 기록한다`() {
            // given
            val key = "rl:user:alice"
            val maxRequests = 10
            val windowSeconds = 60L
            every { zSetOps.zCard(key) } returns 5L

            // when
            val result = sut.isRateLimited(key, maxRequests, windowSeconds)

            // then
            result shouldBe false
            verify { zSetOps.removeRangeByScore(key, 0.0, any()) }
            verify { zSetOps.add(key, any(), any()) }
            verify { redisTemplate.expire(key, Duration.ofSeconds(windowSeconds + 10)) }
        }

        @Test
        fun `Redis ZSET member는 같은 millisecond 요청도 덮어쓰지 않도록 유니크 suffix를 포함한다`() {
            // given
            val key = "rl:user:alice"
            every { zSetOps.zCard(key) } returns 0L

            // when
            sut.isRateLimited(key, maxRequests = 10, windowSeconds = 60L)

            // then
            verify {
                zSetOps.add(
                    key,
                    match { member -> member.contains(":") && member.substringAfter(":").isNotBlank() },
                    any(),
                )
            }
        }

        @Test
        fun `요청 수가 제한 이상이면 true를 반환한다`() {
            // given
            val key = "rl:user:alice"
            val maxRequests = 10
            val windowSeconds = 60L
            every { zSetOps.zCard(key) } returns 10L

            // when
            val result = sut.isRateLimited(key, maxRequests, windowSeconds)

            // then
            result shouldBe true
            verify(exactly = 0) { zSetOps.add(any(), any(), any()) }
        }

        @Test
        fun `Redis 장애 시 fail-open으로 false를 반환한다`() {
            // given
            val key = "rl:user:alice"
            every { zSetOps.removeRangeByScore(any(), any(), any()) } throws redisDown()

            // when
            val result = sut.isRateLimited(key, 10, 60L)

            // then
            result shouldBe false
        }
    }

    @Nested
    inner class `isDuplicate 테스트` {

        @Test
        fun `키가 존재하면 true를 반환한다`() {
            // given
            val key = "notif:dedup:COST_THRESHOLD:2026-04-01"
            every { redisTemplate.hasKey(key) } returns true

            // when
            val result = sut.isDuplicate(key, 60L)

            // then
            result shouldBe true
        }

        @Test
        fun `키가 존재하지 않으면 false를 반환한다`() {
            // given
            val key = "notif:dedup:COST_THRESHOLD:2026-04-01"
            every { redisTemplate.hasKey(key) } returns false

            // when
            val result = sut.isDuplicate(key, 60L)

            // then
            result shouldBe false
        }

        @Test
        fun `Redis 장애 시 fail-open으로 false를 반환한다`() {
            // given
            val key = "notif:dedup:COST_THRESHOLD:2026-04-01"
            every { redisTemplate.hasKey(key) } throws redisDown()

            // when
            val result = sut.isDuplicate(key, 60L)

            // then
            result shouldBe false
        }
    }

    @Nested
    inner class `markSent 테스트` {

        @Test
        fun `올바른 TTL로 dedup 키를 기록한다`() {
            // given
            val key = "notif:dedup:COST_THRESHOLD:2026-04-01"
            val windowMinutes = 120L

            // when
            sut.markSent(key, windowMinutes)

            // then
            verify { valueOps.set(key, "1", Duration.ofMinutes(windowMinutes)) }
        }

        @Test
        fun `Redis 장애 시 예외를 던지지 않는다`() {
            // given
            val key = "notif:dedup:COST_THRESHOLD:2026-04-01"
            every { valueOps.set(any(), any(), any<Duration>()) } throws redisDown()

            // when — 예외 없이 정상 종료되어야 한다
            sut.markSent(key, 120L)
        }
    }

    private fun redisDown() = DataAccessResourceFailureException("Redis connection refused")
}
