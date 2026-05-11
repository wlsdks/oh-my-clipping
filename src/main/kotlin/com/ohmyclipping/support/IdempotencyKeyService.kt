package com.ohmyclipping.support

import com.ohmyclipping.error.ConflictException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

private val log = KotlinLogging.logger {}

/**
 * `Idempotency-Key` 헤더 기반 중복 요청 방지 서비스.
 *
 * 네트워크 재시도/더블클릭으로 같은 키가 재전송되면, 첫 응답을 Redis 에 1시간 캐시해 두고
 * 이후 요청에는 supplier 를 다시 실행하지 않고 캐시된 JSON 을 그대로 돌려준다. 이 덕분에
 * `PUT/PATCH/POST/DELETE` 경로에서 DB 중복 업데이트를 유발하지 않는다.
 *
 * 동시성: 첫 요청이 supplier 를 실행하는 사이 같은 키의 두 번째 요청이 들어올 수 있다.
 * 이 경우 Redis `SET NX` 로 "processing" 락을 잡은 쪽만 supplier 를 실행하고, 다른 쪽은
 * 결과가 저장될 때까지 짧게 폴링한다. 락 TTL 이 만료되면 마지막 재시도는 supplier 를 다시 실행한다.
 *
 * Fail-open: Redis 장애 시 멱등성 보장을 포기하고 supplier 를 바로 실행한다. Editing 을 Redis
 * 장애로 완전 차단하지 않기 위한 정책이며, 재시도 빈도가 낮은 관리자 API 에서는 허용 가능한 절충이다.
 */
@Service
class IdempotencyKeyService @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    companion object {
        /** 캐시된 응답 TTL — 1시간 */
        val CACHE_TTL: Duration = Duration.ofHours(1)

        /** 처리 중(락) 마커 TTL — 같은 키의 supplier 동시 실행을 막는 용도 */
        val LOCK_TTL: Duration = Duration.ofSeconds(30)

        /** 처리 중인 요청을 폴링할 때 한 번 기다리는 간격 */
        val POLL_INTERVAL: Duration = Duration.ofMillis(100)

        /** 폴링 최대 횟수 — `POLL_INTERVAL * MAX_POLLS` 만큼 대기 후 포기 */
        const val MAX_POLLS: Int = 30

        /** 처리 중 마커 값 */
        const val PROCESSING_MARKER: String = "__PROCESSING__"

        internal const val KEY_PREFIX: String = "idempotency"
    }

    /**
     * Idempotency-Key 가 제공됐을 때만 멱등 캐시를 거치고, null/blank 면 supplier 를 그대로 실행한다.
     *
     * @param actor 호출 주체 식별자 (Spring Security `authentication.name`)
     * @param key HTTP `Idempotency-Key` 헤더 값. null/blank 이면 캐시를 적용하지 않는다.
     * @param resultClass 응답 타입 (역직렬화용)
     * @param supplier 첫 호출에서 실행될 실제 비즈니스 로직
     */
    fun <T : Any> executeIfKeyPresent(
        actor: String,
        key: String?,
        resultClass: Class<T>,
        supplier: () -> T
    ): T {
        // 헤더가 없으면 멱등 처리 없이 그대로 실행한다.
        if (key.isNullOrBlank()) {
            return supplier()
        }
        return executeIdempotent(actor, key, resultClass, supplier)
    }

    /**
     * 지정된 (actor, key) 조합으로 supplier 를 **한 번만** 실행하도록 보장한다.
     *
     * - 첫 호출: supplier 실행 → 결과를 JSON 으로 Redis 에 저장 (TTL [CACHE_TTL]) → 반환
     * - 재호출: 캐시 hit → supplier 실행 생략하고 역직렬화 후 반환
     * - 동시 호출: `SET NX` 로 락을 잡지 못한 쪽은 짧게 폴링하며 캐시를 기다린다
     * - Redis 장애: supplier 실행 + 경고 로그 (fail-open)
     */
    fun <T : Any> executeIdempotent(
        actor: String,
        key: String,
        resultClass: Class<T>,
        supplier: () -> T
    ): T {
        val redisKey = buildKey(actor, key)

        // 1) 캐시된 결과가 있으면 바로 반환
        val cachedOrProcessing = safeGet(redisKey)
        if (cachedOrProcessing != null && cachedOrProcessing != PROCESSING_MARKER) {
            return deserializeOrRecompute(cachedOrProcessing, resultClass, supplier)
        }

        // 2) 없으면 "processing" 마커를 SET NX 로 시도 — 락을 잡으면 supplier 실행
        return when (tryAcquireLock(redisKey)) {
            LockAttempt.ACQUIRED -> runSupplierAndCache(redisKey, resultClass, supplier)
            LockAttempt.UNAVAILABLE -> supplier()
            LockAttempt.HELD_BY_OTHER -> pollForCachedResult(redisKey, resultClass, supplier)
        }
    }

    /**
     * 멱등성 캐시에서 특정 키를 명시적으로 제거한다. 테스트/운영 도구 용도.
     */
    fun remove(actor: String, key: String) {
        try {
            redisTemplate.delete(buildKey(actor, key))
        } catch (e: Exception) {
            // 캐시 무효화 실패는 업무 로직에 영향을 주지 않으므로 warn 만 남긴다.
            log.warn(e) { "Failed to remove idempotency key: actor=$actor" }
        }
    }

    private fun <T : Any> runSupplierAndCache(
        redisKey: String,
        resultClass: Class<T>,
        supplier: () -> T
    ): T {
        // supplier 실행은 반드시 락 해제와 함께 일관성 있게 처리한다.
        val result: T = try {
            supplier()
        } catch (e: Throwable) {
            // supplier 가 실패한 경우 processing 마커를 즉시 제거해 다음 재시도가 막히지 않도록 한다.
            safeDelete(redisKey)
            throw e
        }

        // 성공 결과는 TTL 과 함께 Redis 에 저장 (processing 마커 덮어쓰기)
        safeSet(redisKey, serialize(result), CACHE_TTL)
        return result
    }

    private fun <T : Any> pollForCachedResult(
        redisKey: String,
        resultClass: Class<T>,
        supplier: () -> T
    ): T {
        // 처리 중인 요청이 결과를 저장할 때까지 잠시 기다린 뒤 캐시를 재조회한다.
        repeat(MAX_POLLS) {
            InterruptibleSleep.sleep(POLL_INTERVAL.toMillis(), "idempotency poll")
            val value = safeGet(redisKey)
            if (value == null) {
                // 처리 마커가 사라졌지만 결과가 없으면 새 요청이 락을 다시 잡아 실행한다.
                when (tryAcquireLock(redisKey)) {
                    LockAttempt.ACQUIRED -> return runSupplierAndCache(redisKey, resultClass, supplier)
                    LockAttempt.UNAVAILABLE -> return supplier()
                    LockAttempt.HELD_BY_OTHER -> return@repeat
                }
            }
            if (value != PROCESSING_MARKER) {
                return deserializeOrRecompute(value, resultClass, supplier)
            }
        }
        // 타임아웃 시 처리 중인 요청을 중복 실행하지 않고 호출자에게 재시도를 요구한다.
        throw ConflictException("동일한 Idempotency-Key 요청이 아직 처리 중입니다. 잠시 후 다시 시도해 주세요.")
    }

    private fun <T : Any> deserializeOrRecompute(
        raw: String,
        resultClass: Class<T>,
        supplier: () -> T
    ): T {
        return try {
            objectMapper.readValue(raw, resultClass)
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            // 캐시된 JSON 이 버전 미스매치 등으로 깨졌을 때는 supplier 로 복구한다.
            log.warn(e) { "Cached idempotent response failed to deserialize, recomputing" }
            supplier()
        }
    }

    private fun tryAcquireLock(redisKey: String): LockAttempt {
        return try {
            // SET NX PX: 마커가 없는 경우에만 TTL 을 붙여 기록
            val acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, PROCESSING_MARKER, LOCK_TTL) ?: false
            if (acquired) LockAttempt.ACQUIRED else LockAttempt.HELD_BY_OTHER
        } catch (e: Exception) {
            // Redis 장애 시 fail-open — 락 획득 실패로 간주해 호출자가 supplier 를 직접 실행하게 한다.
            log.warn(e) { "Redis SET NX failed, falling back to direct execution" }
            LockAttempt.UNAVAILABLE
        }
    }

    private fun safeGet(redisKey: String): String? {
        return try {
            redisTemplate.opsForValue().get(redisKey)
        } catch (e: Exception) {
            // Redis 장애 시 fail-open: null 반환하면 호출자가 직접 supplier 실행 경로로 유도된다.
            log.warn(e) { "Redis GET failed (fail-open): $redisKey" }
            null
        }
    }

    private fun safeSet(redisKey: String, value: String, ttl: Duration) {
        try {
            redisTemplate.opsForValue().set(redisKey, value, ttl)
        } catch (e: Exception) {
            // 캐시 저장 실패 시에도 supplier 결과는 그대로 반환한다 (멱등성만 포기).
            log.warn(e) { "Redis SET failed (fail-open): $redisKey" }
        }
    }

    private fun safeDelete(redisKey: String) {
        try {
            redisTemplate.delete(redisKey)
        } catch (e: Exception) {
            log.warn(e) { "Redis DEL failed (fail-open): $redisKey" }
        }
    }

    private fun serialize(value: Any): String = objectMapper.writeValueAsString(value)

    private fun buildKey(actor: String, key: String): String = "$KEY_PREFIX:$actor:$key"
}
