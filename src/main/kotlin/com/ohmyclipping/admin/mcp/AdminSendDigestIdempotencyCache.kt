package com.ohmyclipping.admin.mcp

import org.springframework.stereotype.Component
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * `admin_send_digest` 재호출 중복 방지용 경량 캐시.
 *
 * LLM 이 네트워크 타임아웃/재시도로 같은 Slack 발송을 두 번 일으키는 사고를 막는다.
 * 키는 `(actor, categoryId, KST 날짜, slackChannelId)` 조합이고 TTL 은 24시간이다.
 *
 * 24 시간 이유:
 * - 키에 이미 KST 날짜가 포함돼 있어 "같은 actor/카테고리/채널" 로는 하루에 한 번만
 *   발송 가능해야 한다. 10 분 TTL 이던 이전 설계는 10 분 경과 후 같은 키로 재발송이
 *   열리면서 "하루에 두 번 같은 digest 를 쏘는" 사고 경로가 남아있었다.
 * - LLM 재시도 윈도우는 실무상 수 분 이내이므로 10 분이든 24 시간이든 오탐은 없다.
 * - 일이 바뀌면 키의 KST 날짜 부분이 바뀌므로 다음 날 발송은 자연스럽게 열린다.
 *
 * Redis `SET NX + TTL` 기반으로 동작해 재시작/다중 인스턴스에서도 같은 날 중복 발송을 막는다.
 * Redis 장애 시에는 기존 ConcurrentHashMap fallback으로 중복 방지 기능을 유지한다.
 */
@Component
class AdminSendDigestIdempotencyCache(
    private val redisTemplate: StringRedisTemplate,
    private val clock: Clock = Clock.systemUTC(),
) {

    companion object {
        /**
         * KST 하루를 통째로 덮는 TTL. 키 자체에 KST 날짜가 있으므로 24 시간이면 충분.
         * 시간대 오차/서머타임 보정을 위해 24h 로 맞췄다 (과도한 48h 는 메모리 상 무의미).
         */
        val DEFAULT_TTL: Duration = Duration.ofHours(24)

        private const val KEY_PREFIX = "mcp:admin-send-digest:idempotency"
    }

    private data class Entry(val expiresAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * 이미 TTL 안에 동일 키가 기록됐는지 확인하고, 없으면 새 엔트리를 삽입한다.
     *
     * @return true 면 최초 기록(= 발송을 진행해도 됨), false 면 중복 호출(= 거부)
     */
    fun tryAcquire(key: String): Boolean {
        return try {
            tryAcquireInRedis(key)
        } catch (_: Exception) {
            tryAcquireInMemory(key)
        }
    }

    /**
     * 테스트/운영 도구용 초기화 훅. 일반 런타임에선 호출하지 않는다.
     */
    fun clear() {
        entries.clear()
        try {
            if (redisTemplate.connectionFactory == null) return
            val redisKeys = redisTemplate.keys("$KEY_PREFIX:*")
            if (!redisKeys.isNullOrEmpty()) {
                redisTemplate.delete(redisKeys)
            }
        } catch (_: Exception) {
            // clear() is a test/ops hook; memory state is already cleared and Redis may be unavailable.
        }
    }

    private fun tryAcquireInRedis(key: String): Boolean {
        if (isHeldInMemory(key)) {
            return false
        }
        if (redisTemplate.connectionFactory == null) {
            return tryAcquireInMemory(key)
        }
        val redisKey = buildRedisKey(key)
        // SET NX EX 로 한 번 기록된 발송 키는 TTL 내 재등록되지 않는다.
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(redisKey, "1", DEFAULT_TTL) ?: false
        if (acquired) {
            recordInMemory(key)
        }
        return acquired
    }

    private fun tryAcquireInMemory(key: String): Boolean {
        val now = clock.instant()
        // 만료된 엔트리를 먼저 정리해 고아 키가 쌓이지 않도록 한다.
        cleanupExpired(now)
        val expiresAt = now.plus(DEFAULT_TTL)
        val existing = entries.putIfAbsent(key, Entry(expiresAt))
        if (existing == null) return true
        // 기존 엔트리가 TTL 밖이면 새 엔트리로 교체하고 통과시킨다.
        if (existing.expiresAt.isBefore(now)) {
            val replaced = entries.replace(key, existing, Entry(expiresAt))
            return replaced
        }
        return false
    }

    private fun isHeldInMemory(key: String): Boolean {
        val now = clock.instant()
        cleanupExpired(now)
        val existing = entries[key] ?: return false
        return existing.expiresAt.isAfter(now)
    }

    private fun recordInMemory(key: String) {
        val now = clock.instant()
        cleanupExpired(now)
        entries[key] = Entry(now.plus(DEFAULT_TTL))
    }

    /** 만료된 엔트리를 제거한다. 쓰레드 간 경쟁은 허용(중복 제거는 무해). */
    private fun cleanupExpired(now: Instant) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (_, value) = iterator.next()
            if (value.expiresAt.isBefore(now)) {
                iterator.remove()
            }
        }
    }

    private fun buildRedisKey(key: String): String = "$KEY_PREFIX:$key"
}
