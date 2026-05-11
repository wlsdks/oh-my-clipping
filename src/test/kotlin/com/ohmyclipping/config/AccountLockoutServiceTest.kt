package com.ohmyclipping.config

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

/**
 * AccountLockoutService의 Redis 기반 잠금 테스트.
 * 로그인 실패 카운터와 잠금 상태가 재시작 후에도 유지되도록 Redis 키 사용을 검증한다.
 */
class AccountLockoutServiceTest {

    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
    private val service = AccountLockoutService(redisTemplate)

    init {
        every { redisTemplate.opsForValue() } returns valueOps
    }

    @Test
    fun `로그인 실패는 Redis atomic counter와 TTL로 기록한다`() {
        every { valueOps.increment("account-lockout:fail:user@example.com") } returns 1L

        service.recordFailure(" User@Example.com ")

        verify(exactly = 1) { valueOps.increment("account-lockout:fail:user@example.com") }
        verify(exactly = 1) {
            redisTemplate.expire(
                "account-lockout:fail:user@example.com",
                Duration.ofMinutes(60),
            )
        }
    }

    @Test
    fun `실패 횟수가 기준 이상이면 Redis lock 키에 TTL을 설정한다`() {
        every { valueOps.increment("account-lockout:fail:user@example.com") } returns
            AccountLockoutService.MAX_FAILURES.toLong()

        service.recordFailure("user@example.com")

        verify(exactly = 1) {
            valueOps.set(
                "account-lockout:lock:user@example.com",
                match { it.toLongOrNull() != null },
                Duration.ofMinutes(AccountLockoutService.LOCKOUT_MINUTES),
            )
        }
        verify(exactly = 1) {
            redisTemplate.expire(
                "account-lockout:fail:user@example.com",
                Duration.ofMinutes(AccountLockoutService.LOCKOUT_MINUTES),
            )
        }
    }

    @Test
    fun `잠금 확인은 Redis lock 키를 우선 조회한다`() {
        val lockedUntil = System.currentTimeMillis() + 60_000
        every { valueOps.get("account-lockout:lock:user@example.com") } returns lockedUntil.toString()

        service.isLocked("user@example.com") shouldBe true

        verify(exactly = 1) { valueOps.get("account-lockout:lock:user@example.com") }
    }

    @Test
    fun `Redis 장애 시 인메모리 fallback으로 잠금 기능을 유지한다`() {
        every { valueOps.increment(any()) } throws redisDown()
        every { valueOps.get(any()) } throws redisDown()

        repeat(AccountLockoutService.MAX_FAILURES) {
            service.recordFailure("fallback@example.com")
        }

        service.isLocked("fallback@example.com") shouldBe true
    }

    @Test
    fun `Redis 장애 중 잡힌 인메모리 잠금은 Redis 복구 후에도 유지된다`() {
        every { valueOps.increment(any()) } throws redisDown()

        repeat(AccountLockoutService.MAX_FAILURES) {
            service.recordFailure("fallback@example.com")
        }

        every { valueOps.get("account-lockout:lock:fallback@example.com") } returns null

        service.isLocked("fallback@example.com") shouldBe true
    }

    @Test
    fun `Redis 잠금 값이 잘못된 형식이면 인메모리 상태로 폴백한다`() {
        every { valueOps.increment(any()) } throws redisDown()
        repeat(AccountLockoutService.MAX_FAILURES) {
            service.recordFailure("fallback@example.com")
        }
        every { valueOps.get("account-lockout:lock:fallback@example.com") } returns "not-a-timestamp"

        service.isLocked("fallback@example.com") shouldBe true
    }

    private fun redisDown() = DataAccessResourceFailureException("redis down")
}
