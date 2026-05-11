package com.clipping.mcpserver.admin.mcp

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [AdminSendDigestIdempotencyCache] 단위 테스트.
 *
 * TTL 경과 전 / 후 동작과 서로 다른 키가 서로 간섭하지 않는지를 검증한다.
 */
class AdminSendDigestIdempotencyCacheTest {

    private val fixedNow = Instant.parse("2026-04-08T12:00:00Z")
    private var currentNow: Instant = fixedNow
    private val clock: Clock = object : Clock() {
        override fun instant(): Instant = currentNow
        override fun withZone(zone: ZoneId?): Clock = this
        override fun getZone(): ZoneId = ZoneOffset.UTC
    }
    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
    private val cache = AdminSendDigestIdempotencyCache(redisTemplate, clock)

    init {
        every { redisTemplate.opsForValue() } returns valueOps
        every { redisTemplate.connectionFactory } returns mockk<RedisConnectionFactory>(relaxed = true)
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } throws RuntimeException("redis down")
    }

    @Test
    fun `Redis SET NX TTL 로 최초 키를 기록한다`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true

        cache.tryAcquire("k1") shouldBe true

        verify(exactly = 1) {
            valueOps.setIfAbsent(
                "mcp:admin-send-digest:idempotency:k1",
                "1",
                AdminSendDigestIdempotencyCache.DEFAULT_TTL,
            )
        }
    }

    @Test
    fun `Redis 성공 키는 인메모리에도 기록해 이후 Redis 장애 중복을 막는다`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true

        cache.tryAcquire("k1") shouldBe true
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } throws RuntimeException("redis down")

        cache.tryAcquire("k1") shouldBe false
    }

    @Test
    fun `Redis 장애 중 기록한 키는 Redis 복구 후에도 중복으로 거부한다`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } throws RuntimeException("redis down")

        cache.tryAcquire("k1") shouldBe true
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true

        cache.tryAcquire("k1") shouldBe false
    }

    @Test
    fun `Redis SET NX 실패는 중복 호출로 본다`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns false

        cache.tryAcquire("k1") shouldBe false
    }

    @Test
    fun `최초 tryAcquire 는 true 를 반환한다`() {
        cache.tryAcquire("k1") shouldBe true
    }

    @Test
    fun `TTL 내 재요청은 false 를 반환한다`() {
        cache.tryAcquire("k1") shouldBe true
        currentNow = fixedNow.plusSeconds(60)
        cache.tryAcquire("k1") shouldBe false
    }

    @Test
    fun `TTL 24시간 이내에는 여전히 false 를 반환한다 (10분 TTL 이던 이전 설계의 중복 발송 구멍 차단)`() {
        cache.tryAcquire("k1") shouldBe true
        // 12시간 경과 — 이전 10분 TTL 에선 true 로 뚫렸으나 24h 로 연장 후에는 여전히 차단.
        currentNow = fixedNow.plusSeconds(12 * 60 * 60)
        cache.tryAcquire("k1") shouldBe false
    }

    @Test
    fun `TTL 24시간 직후에는 다시 true 를 반환한다 (다음 날 발송 경로)`() {
        cache.tryAcquire("k1") shouldBe true
        // 24시간 + 1초 경과 — 키 날짜가 바뀌는 경계를 모사. 키가 바뀌면 자연스럽게 풀려야 한다.
        currentNow = fixedNow.plusSeconds(24 * 60 * 60 + 1)
        cache.tryAcquire("k1") shouldBe true
    }

    @Test
    fun `서로 다른 키는 서로 간섭하지 않는다`() {
        cache.tryAcquire("k1") shouldBe true
        cache.tryAcquire("k2") shouldBe true
        cache.tryAcquire("k1") shouldBe false
        cache.tryAcquire("k2") shouldBe false
    }

    @Test
    fun `clear 후에는 이전 키로 다시 true 를 반환한다`() {
        cache.tryAcquire("k1") shouldBe true
        cache.clear()
        cache.tryAcquire("k1") shouldBe true
    }

    @Test
    fun `clear 는 Redis idempotency 키도 삭제한다`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true
        every { redisTemplate.keys("mcp:admin-send-digest:idempotency:*") } returns
            setOf("mcp:admin-send-digest:idempotency:k1")

        cache.tryAcquire("k1") shouldBe true
        cache.clear()

        verify(exactly = 1) {
            redisTemplate.delete(setOf("mcp:admin-send-digest:idempotency:k1"))
        }
    }
}
