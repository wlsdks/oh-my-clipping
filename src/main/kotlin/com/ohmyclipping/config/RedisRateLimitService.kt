package com.ohmyclipping.config

import com.ohmyclipping.service.port.NotificationDedupPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private val log = KotlinLogging.logger {}

@Service
class RedisRateLimitService(
    private val redisTemplate: StringRedisTemplate
) : NotificationDedupPort {

    private val inMemoryCounters = ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>>()

    fun isRateLimited(key: String, maxRequests: Int, windowSeconds: Long): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val windowStart = now - (windowSeconds * 1000)
            val ops = redisTemplate.opsForZSet()

            ops.removeRangeByScore(key, 0.0, windowStart.toDouble())

            val count = ops.zCard(key) ?: 0
            if (count >= maxRequests) {
                return true
            }

            ops.add(key, "$now:${System.nanoTime()}", now.toDouble())
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds + 10))
            false
        } catch (e: DataAccessException) {
            log.warn(e) { "Redis rate limit check failed, falling back to in-memory: $key" }
            isRateLimitedInMemory(key, maxRequests, windowSeconds)
        }
    }

    private fun isRateLimitedInMemory(key: String, maxRequests: Int, windowSeconds: Long): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - (windowSeconds * 1000)

        val queue = inMemoryCounters.computeIfAbsent(key) { ConcurrentLinkedQueue() }

        while (queue.peek()?.let { it < windowStart } == true) {
            queue.poll()
        }

        if (queue.size >= maxRequests) {
            return true
        }

        queue.add(now)
        return false
    }

    fun isRateLimitedForTool(
        toolName: String,
        actor: String,
        dimension: String? = null,
        maxRequests: Int,
        windowSeconds: Long
    ): Boolean {
        val key = listOf(
            MCP_KEY_PREFIX,
            encodeMcpRateLimitSegment(toolName),
            encodeMcpRateLimitSegment(actor),
            dimension?.let(::encodeMcpRateLimitSegment) ?: MCP_GLOBAL_DIMENSION,
        ).joinToString(":")
        return isRateLimited(key, maxRequests, windowSeconds)
    }

    override fun isDuplicate(key: String, windowMinutes: Long): Boolean {
        return try {
            redisTemplate.hasKey(key)
        } catch (e: DataAccessException) {
            log.warn(e) { "Redis dedup check failed (fail-open): $key" }
            false
        }
    }

    override fun markSent(key: String, windowMinutes: Long) {
        try {
            redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(windowMinutes))
        } catch (e: DataAccessException) {
            log.warn(e) { "Redis dedup mark failed: $key" }
        }
    }

    private fun encodeMcpRateLimitSegment(value: String): String =
        "b64_" + Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    companion object {
        private const val MCP_KEY_PREFIX = "mcp"
        private const val MCP_GLOBAL_DIMENSION = "global"
    }
}
