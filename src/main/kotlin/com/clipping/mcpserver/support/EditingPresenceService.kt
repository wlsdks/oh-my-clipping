package com.clipping.mcpserver.support

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * 편집 presence(Editing Presence) 트래킹 서비스.
 *
 * 4가지 리소스(Persona, Category, CategoryRule, RssSource)에 대해 "누가 지금 편집 모달을 열고 있는지"를
 * Redis 에 TTL 기반 heartbeat 으로 추적한다. 프론트가 편집 모달을 열 때 30초 간격으로 heartbeat 을
 * 보내 presence 를 유지하고, 모달을 닫거나 탭을 떠나면 Redis TTL(60초)으로 자동 정리된다.
 *
 * Redis 키 패턴: `editing:{resourceType}:{resourceId}:{userId}`
 * Redis 값(JSON): `{"userId":"...","displayName":"...","startedAt":"2026-04-17T00:00:00Z"}`
 *
 * 동시성: 같은 userId 가 같은 리소스에 재heartbeat 을 보내면 `startedAt` 은 **최초 값을 유지**하고
 * TTL 만 갱신한다. 이로써 "A님이 2분 전부터 편집 중" 문구가 일관되게 유지된다.
 *
 * Fail-open: Redis 장애 시 presence 를 비활성화(listActive 는 빈 리스트 반환)하지만 실제 편집 저장
 * 로직에는 영향을 주지 않는다. 본 기능은 충돌 예방 UX 이므로 Redis 를 필수 의존성으로 두지 않는다.
 */
@Service
class EditingPresenceService @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
    private val clock: Clock = Clock.systemUTC(),
    private val objectMapper: ObjectMapper = defaultMapper()
) {

    companion object {
        /** heartbeat 으로 유지되는 presence TTL. 프론트 30초 주기 기준, 1회 누락을 허용하는 60초. */
        val PRESENCE_TTL: Duration = Duration.ofSeconds(60)

        /** 허용된 resourceType 화이트리스트. 임의 입력으로 Redis 키를 오염시키지 않는다. */
        val ALLOWED_RESOURCE_TYPES: Set<String> = setOf(
            "persona",
            "category",
            "categoryRule",
            "rssSource"
        )

        internal const val KEY_PREFIX: String = "editing"

        /** SCAN 의 한 번의 COUNT 힌트. 4 엔티티 * 관리자 수 정도라 작게 둔다. */
        internal const val SCAN_COUNT: Long = 64

        /** listActive 가 처리하는 최대 키 수. 비정상적으로 많을 경우 방어. */
        internal const val MAX_ACTIVE_KEYS: Int = 100

        /** Instant 직렬화를 위해 JavaTimeModule 을 등록한 기본 ObjectMapper. */
        internal fun defaultMapper(): ObjectMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    /**
     * 편집 세션 heartbeat. 처음 호출이면 startedAt 을 현재 시각으로 저장하고, 이후 호출이면
     * 기존 startedAt 을 유지한 채 TTL 만 갱신한다.
     *
     * @param resourceType `ALLOWED_RESOURCE_TYPES` 중 하나여야 한다.
     * @param resourceId 편집 대상 리소스 식별자. blank 불가.
     * @param userId 인증된 사용자 식별자(`authentication.name`).
     * @param displayName 다른 관리자에게 표시될 이름. blank 면 "관리자" 로 대체된다.
     */
    fun heartbeat(
        resourceType: String,
        resourceId: String,
        userId: String,
        displayName: String
    ) {
        // 입력 화이트리스트 검증 — Redis 키 오염/스캔 비용 폭주 방지.
        require(resourceType in ALLOWED_RESOURCE_TYPES) { "unsupported resourceType: $resourceType" }
        require(resourceId.isNotBlank()) { "resourceId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }

        val safeDisplayName = displayName.ifBlank { "관리자" }
        val key = buildKey(resourceType, resourceId, userId)

        try {
            // 기존 세션을 읽어 startedAt 을 유지하거나, 없으면 현재 시각으로 새로 생성한다.
            val existing = redisTemplate.opsForValue().get(key)?.let { safeParse(it) }
            val startedAt = existing?.startedAt ?: Instant.now(clock)
            val session = EditingSession(
                userId = userId,
                displayName = safeDisplayName,
                startedAt = startedAt
            )
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(session), PRESENCE_TTL)
        } catch (e: JsonProcessingException) {
            log.warn(e) { "EditingPresence heartbeat failed (fail-open): key=$key" }
        } catch (e: RuntimeException) {
            // Redis 장애 시 fail-open — 기능을 비활성화하고 기본 편집 흐름은 이어가도록 한다.
            log.warn(e) { "EditingPresence heartbeat failed (fail-open): key=$key" }
        }
    }

    /**
     * 편집 세션 명시적 해제. 모달을 닫거나 언마운트될 때 호출된다.
     * TTL 로도 자동 정리되지만, 즉시 presence 를 제거해 다른 편집자의 대기 시간을 줄여 준다.
     */
    fun release(resourceType: String, resourceId: String, userId: String) {
        require(resourceType in ALLOWED_RESOURCE_TYPES) { "unsupported resourceType: $resourceType" }
        require(resourceId.isNotBlank()) { "resourceId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }
        val key = buildKey(resourceType, resourceId, userId)
        try {
            redisTemplate.delete(key)
        } catch (e: RuntimeException) {
            log.warn(e) { "EditingPresence release failed (fail-open): key=$key" }
        }
    }

    /**
     * 해당 리소스를 현재 편집 중인 관리자 목록.
     * `excludeUserId` 를 지정하면 본인 세션은 결과에서 제외한다(보통 호출자 본인).
     */
    fun listActive(
        resourceType: String,
        resourceId: String,
        excludeUserId: String? = null
    ): List<EditingSession> {
        require(resourceType in ALLOWED_RESOURCE_TYPES) { "unsupported resourceType: $resourceType" }
        require(resourceId.isNotBlank()) { "resourceId must not be blank" }
        val pattern = "$KEY_PREFIX:$resourceType:$resourceId:*"
        return try {
            scanSessions(pattern, excludeUserId)
        } catch (e: RuntimeException) {
            // Redis 장애 — 빈 리스트 반환해 UI 는 "아무도 편집 안 함" 으로 동작한다.
            log.warn(e) { "EditingPresence listActive failed (fail-open): pattern=$pattern" }
            emptyList()
        }
    }

    private fun scanSessions(pattern: String, excludeUserId: String?): List<EditingSession> {
        val options = ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build()
        val connectionFactory = redisTemplate.connectionFactory ?: return emptyList()
        val results = mutableListOf<EditingSession>()
        // 커넥션 수동 관리 — SCAN 은 cursor 반복이 끝나면 반드시 close 해야 리소스 누수를 막는다.
        connectionFactory.connection.use { connection ->
            connection.keyCommands().scan(options).use { cursor ->
                while (cursor.hasNext() && results.size < MAX_ACTIVE_KEYS) {
                    val rawKey = String(cursor.next())
                    val raw = redisTemplate.opsForValue().get(rawKey) ?: continue
                    val session = safeParse(raw) ?: continue
                    if (excludeUserId != null && session.userId == excludeUserId) continue
                    results += session
                }
            }
        }
        // startedAt 오름차순 — "가장 먼저 편집을 시작한 사람" 이 첫 번째가 되도록.
        return results.sortedBy { it.startedAt }
    }

    private fun safeParse(raw: String): EditingSession? {
        return try {
            objectMapper.readValue(raw, EditingSession::class.java)
        } catch (e: JsonProcessingException) {
            // 값 포맷 변경/깨짐 대비 — 경고만 남기고 무시해 나머지 세션 조회에는 영향이 없게 한다.
            log.warn(e) { "EditingPresence JSON parse failed: raw=$raw" }
            null
        }
    }

    private fun buildKey(resourceType: String, resourceId: String, userId: String): String =
        "$KEY_PREFIX:$resourceType:$resourceId:$userId"
}

/**
 * 편집 세션 스냅샷 DTO. API 응답과 Redis 값 직렬화 양쪽에서 사용된다.
 */
data class EditingSession(
    val userId: String,
    val displayName: String,
    val startedAt: Instant
)
