package com.clipping.mcpserver.observability

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

/**
 * 토큰(Slack Bot / Gemini API) 헬스 상태를 Redis에 캐시하는 트래커.
 *
 * 장애 발생 시 실패 이벤트에서 상태를 기록하고, `/api/admin/system/token-health`
 * 엔드포인트와 프론트 배너가 이 값을 읽어 운영팀에 노출한다. 복구(성공)가 감지되면
 * 자동으로 OK로 갱신된다.
 *
 * Redis 장애 시 인메모리 폴백으로 동작해 상태가 완전히 사라지지 않게 한다.
 */
@Component
class TokenHealthTracker(
    private val redisTemplate: StringRedisTemplate
) {

    /** Redis 장애 시 인메모리 폴백 저장소 (key -> status). */
    private val inMemoryFallback = ConcurrentHashMap<String, String>()

    /**
     * Slack Bot 토큰의 현재 상태를 반환한다. 저장된 값이 없으면 [TokenStatus.OK].
     */
    fun slackBotStatus(): TokenStatus = readStatus(KEY_SLACK_BOT)

    /**
     * Gemini API 토큰의 현재 상태를 반환한다. 저장된 값이 없으면 [TokenStatus.OK].
     */
    fun geminiStatus(): TokenStatus = readStatus(KEY_GEMINI)

    /**
     * Slack Bot 토큰의 상태를 기록한다. OK이면 키를 삭제해 메모리 낭비를 줄인다.
     */
    fun recordSlackBot(status: TokenStatus) {
        writeStatus(KEY_SLACK_BOT, status)
    }

    /**
     * Gemini 토큰의 상태를 기록한다. OK이면 키를 삭제한다.
     */
    fun recordGemini(status: TokenStatus) {
        writeStatus(KEY_GEMINI, status)
    }

    private fun readStatus(key: String): TokenStatus {
        val raw = try {
            redisTemplate.opsForValue().get(key) ?: inMemoryFallback[key]
        } catch (e: Exception) {
            // Redis 장애 시 인메모리 폴백을 사용한다.
            log.debug(e) { "Redis token-health 조회 실패, 인메모리 폴백을 사용한다: $key" }
            inMemoryFallback[key]
        }
        return TokenStatus.fromRaw(raw)
    }

    private fun writeStatus(key: String, status: TokenStatus) {
        if (status == TokenStatus.OK) {
            // 복구 상태는 키 삭제로 표현한다 (TTL에 의존하지 않고 즉시 OK로 내려감).
            clearKey(key)
            return
        }
        val value = status.name
        try {
            // 관리자 수동 개입 전까지 유지되도록 넉넉한 TTL(1시간)을 둔다.
            redisTemplate.opsForValue().set(key, value, Duration.ofHours(1))
            inMemoryFallback[key] = value
        } catch (e: Exception) {
            log.warn(e) { "Redis token-health 기록 실패, 인메모리 폴백에만 저장한다: $key" }
            inMemoryFallback[key] = value
        }
    }

    private fun clearKey(key: String) {
        try {
            redisTemplate.delete(key)
        } catch (e: Exception) {
            log.debug(e) { "Redis token-health 키 삭제 실패: $key" }
        }
        inMemoryFallback.remove(key)
    }

    companion object {
        const val KEY_SLACK_BOT = "token-health:slack-bot"
        const val KEY_GEMINI = "token-health:gemini"
    }
}

/**
 * 토큰 헬스 상태 열거형.
 *
 * Slack 전용: [EXPIRED], [SCOPE_MISMATCH]
 * Gemini 전용: [EXPIRED], [QUOTA_EXHAUSTED]
 * 공통: [OK], [UNKNOWN]
 */
enum class TokenStatus(val wireValue: String) {
    OK("ok"),
    EXPIRED("expired"),
    SCOPE_MISMATCH("scope_mismatch"),
    QUOTA_EXHAUSTED("quota_exhausted"),
    UNKNOWN("unknown");

    companion object {
        /** 저장된 enum 이름 문자열을 TokenStatus로 복원한다. 매핑 실패 시 OK로 폴백한다. */
        fun fromRaw(raw: String?): TokenStatus {
            if (raw.isNullOrBlank()) return OK
            return values().firstOrNull { it.name == raw.trim().uppercase() } ?: OK
        }
    }
}
