package com.clipping.mcpserver.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

@Service
class AccountLockoutService(
    private val redisTemplate: StringRedisTemplate
) {

    companion object {
        const val MAX_FAILURES = 5
        const val LOCKOUT_MINUTES = 30L
        private const val CLEANUP_AGE_MINUTES = 60L
        private const val KEY_PREFIX = "account-lockout"
    }

    data class AccountLockState(
        val failCount: Int = 0,
        val lastFailedAt: Instant = Instant.now(),
        val lockedUntil: Instant? = null
    )

    private val lockMap = ConcurrentHashMap<String, AccountLockState>()

    fun isLocked(username: String): Boolean {
        val normalized = normalize(username)
        return try {
            isLockedInRedis(normalized) || isLockedInMemory(normalized)
        } catch (e: DataAccessException) {
            log.warn(e) { "Redis account lockout lookup failed, falling back to in-memory: $normalized" }
            isLockedInMemory(normalized)
        } catch (e: ClassCastException) {
            log.warn(e) { "Redis account lockout value type mismatch, falling back to in-memory: $normalized" }
            isLockedInMemory(normalized)
        } catch (e: NumberFormatException) {
            log.warn(e) { "Redis account lockout value is malformed, falling back to in-memory: $normalized" }
            isLockedInMemory(normalized)
        }
    }

    fun recordFailure(username: String) {
        val normalized = normalize(username)
        try {
            recordFailureInRedis(normalized)
        } catch (e: DataAccessException) {
            log.warn(e) { "Redis account lockout update failed, falling back to in-memory: $normalized" }
            recordFailureInMemory(normalized)
        }
    }

    fun recordSuccess(username: String) {
        val normalized = normalize(username)
        try {
            redisTemplate.delete(listOf(failureKey(normalized), lockKey(normalized)))
        } catch (e: DataAccessException) {
            log.warn(e) { "Redis account lockout clear failed, falling back to in-memory clear: $normalized" }
        }
        lockMap.remove(normalized)
    }

    private fun isLockedInRedis(username: String): Boolean {
        val rawLockedUntil = redisTemplate.opsForValue().get(lockKey(username)) ?: return false
        val lockedUntil = Instant.ofEpochMilli(rawLockedUntil.toLong())
        if (Instant.now().isBefore(lockedUntil)) return true
        // TTL 만료 전 clock skew 등으로 지난 잠금 값이 보이면 즉시 정리한다.
        redisTemplate.delete(listOf(failureKey(username), lockKey(username)))
        return false
    }

    private fun recordFailureInRedis(username: String) {
        val failKey = failureKey(username)
        val failCount = redisTemplate.opsForValue().increment(failKey) ?: 1L
        redisTemplate.expire(failKey, Duration.ofMinutes(CLEANUP_AGE_MINUTES))
        if (failCount >= MAX_FAILURES) {
            val lockedUntil = Instant.now().plusSeconds(LOCKOUT_MINUTES * 60)
            log.warn { "Account locked: $username (failed $failCount times)" }
            redisTemplate.expire(failKey, Duration.ofMinutes(LOCKOUT_MINUTES))
            redisTemplate.opsForValue().set(
                lockKey(username),
                lockedUntil.toEpochMilli().toString(),
                Duration.ofMinutes(LOCKOUT_MINUTES),
            )
        }
    }

    private fun isLockedInMemory(username: String): Boolean {
        val state = lockMap[username] ?: return false
        val lockedUntil = state.lockedUntil ?: return false
        if (Instant.now().isBefore(lockedUntil)) return true
        // 잠금 기간이 만료되었으면 상태를 초기화한다.
        lockMap.remove(username)
        return false
    }

    private fun recordFailureInMemory(username: String) {
        lockMap.compute(username) { _, current ->
            val newCount = (current?.failCount ?: 0) + 1
            val lockedUntil = if (newCount >= MAX_FAILURES) {
                log.warn { "Account locked: $username (failed $newCount times)" }
                Instant.now().plusSeconds(LOCKOUT_MINUTES * 60)
            } else {
                current?.lockedUntil
            }
            AccountLockState(
                failCount = newCount,
                lastFailedAt = Instant.now(),
                lockedUntil = lockedUntil
            )
        }
    }

    /** 1시간 이상 지난 항목을 자동 정리한다. */
    @Scheduled(fixedDelay = 600_000) // 10분마다
    fun cleanup() {
        val cutoff = Instant.now().minusSeconds(CLEANUP_AGE_MINUTES * 60)
        val removed = lockMap.entries.removeIf { (_, state) ->
            state.lastFailedAt.isBefore(cutoff) &&
                (state.lockedUntil == null || Instant.now().isAfter(state.lockedUntil))
        }
        if (removed) {
            log.debug { "AccountLockoutService: cleaned up stale entries" }
        }
    }

    /** 테스트용: 내부 상태를 초기화한다. */
    internal fun clear() = lockMap.clear()

    /** 테스트용: 현재 잠금 상태를 조회한다. */
    internal fun getState(username: String): AccountLockState? = lockMap[username]

    private fun normalize(username: String): String = username.trim().lowercase()

    private fun failureKey(username: String): String = "$KEY_PREFIX:fail:$username"

    private fun lockKey(username: String): String = "$KEY_PREFIX:lock:$username"
}
