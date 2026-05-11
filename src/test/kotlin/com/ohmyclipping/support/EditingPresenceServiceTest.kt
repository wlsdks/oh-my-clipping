package com.ohmyclipping.support

import com.ohmyclipping.support.EditingPresenceService.Companion.defaultMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisKeyCommands
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * EditingPresenceService 단위 테스트.
 *
 * Redis 동작을 mockk 로 시뮬레이션해 heartbeat 최초/재호출, release, listActive, 본인 제외 필터,
 * fail-open 동작을 검증한다.
 */
class EditingPresenceServiceTest {

    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
    private val connectionFactory = mockk<RedisConnectionFactory>(relaxed = true)
    private val connection = mockk<RedisConnection>(relaxed = true)
    private val keyCommands = mockk<RedisKeyCommands>(relaxed = true)
    private val fixedInstant: Instant = Instant.parse("2026-04-17T09:00:00Z")
    private val laterInstant: Instant = Instant.parse("2026-04-17T09:02:00Z")
    private val mapper = defaultMapper()

    private val firstCallClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val laterClock = Clock.fixed(laterInstant, ZoneOffset.UTC)

    init {
        every { redisTemplate.opsForValue() } returns valueOps
        every { redisTemplate.connectionFactory } returns connectionFactory
        every { connectionFactory.connection } returns connection
        every { connection.keyCommands() } returns keyCommands
    }

    private fun createService(clock: Clock = firstCallClock) =
        EditingPresenceService(redisTemplate, clock, mapper)

    @Nested
    inner class `heartbeat` {

        @Test
        fun `최초 호출이면 새 세션을 저장하고 TTL 을 지정한다`() {
            every { valueOps.get(any()) } returns null

            val payloadSlot = slot<String>()
            val ttlSlot = slot<Duration>()
            every { valueOps.set(any(), capture(payloadSlot), capture(ttlSlot)) } just Runs

            createService().heartbeat(
                resourceType = "persona",
                resourceId = "p-1",
                userId = "alice",
                displayName = "앨리스"
            )

            val stored = mapper.readValue(payloadSlot.captured, EditingSession::class.java)
            stored.userId shouldBe "alice"
            stored.displayName shouldBe "앨리스"
            stored.startedAt shouldBe fixedInstant
            ttlSlot.captured shouldBe EditingPresenceService.PRESENCE_TTL
            verify(exactly = 1) { valueOps.set("editing:persona:p-1:alice", any(), EditingPresenceService.PRESENCE_TTL) }
        }

        @Test
        fun `재호출이면 기존 startedAt 을 유지하고 TTL 만 연장한다`() {
            val existing = EditingSession(
                userId = "alice",
                displayName = "앨리스",
                startedAt = fixedInstant
            )
            every { valueOps.get("editing:persona:p-1:alice") } returns mapper.writeValueAsString(existing)

            val payloadSlot = slot<String>()
            every { valueOps.set(any(), capture(payloadSlot), any<Duration>()) } just Runs

            createService(laterClock).heartbeat(
                resourceType = "persona",
                resourceId = "p-1",
                userId = "alice",
                displayName = "앨리스"
            )

            val stored = mapper.readValue(payloadSlot.captured, EditingSession::class.java)
            stored.startedAt shouldBe fixedInstant
            stored.startedAt shouldNotBe laterInstant
        }

        @Test
        fun `빈 displayName 은 '관리자' 로 대체된다`() {
            every { valueOps.get(any()) } returns null
            val payloadSlot = slot<String>()
            every { valueOps.set(any(), capture(payloadSlot), any<Duration>()) } just Runs

            createService().heartbeat("persona", "p-1", "u-1", " ")

            val stored = mapper.readValue(payloadSlot.captured, EditingSession::class.java)
            stored.displayName shouldBe "관리자"
        }

        @Test
        fun `허용되지 않은 resourceType 은 즉시 거부된다`() {
            assertThrows<IllegalArgumentException> {
                createService().heartbeat("unknown", "p-1", "u-1", "A")
            }
        }

        @Test
        fun `resourceId 가 blank 면 거부된다`() {
            assertThrows<IllegalArgumentException> {
                createService().heartbeat("persona", "  ", "u-1", "A")
            }
        }

        @Test
        fun `Redis 장애 시 예외를 삼키고 fail-open 한다`() {
            every { valueOps.get(any()) } throws RuntimeException("redis down")

            createService().heartbeat("persona", "p-1", "u-1", "A")
            // 예외가 밖으로 전파되지 않는다. set 은 호출되지 않음(해당 경로는 try 내부에서 중단됨).
            verify(exactly = 0) { valueOps.set(any(), any(), any<Duration>()) }
        }
    }

    @Nested
    inner class `release` {

        @Test
        fun `지정된 키를 Redis 에서 삭제한다`() {
            createService().release("rssSource", "s-1", "alice")
            verify(exactly = 1) { redisTemplate.delete("editing:rssSource:s-1:alice") }
        }

        @Test
        fun `허용되지 않은 resourceType 은 거부된다`() {
            assertThrows<IllegalArgumentException> {
                createService().release("bogus", "s-1", "alice")
            }
        }

        @Test
        fun `Redis 장애 시 예외를 삼킨다`() {
            every { redisTemplate.delete(any<String>()) } throws RuntimeException("redis down")
            createService().release("rssSource", "s-1", "alice")
        }
    }

    @Nested
    inner class `listActive` {

        @Test
        fun `SCAN 으로 일치하는 세션을 수집한다`() {
            val alice = EditingSession("alice", "앨리스", fixedInstant)
            val bob = EditingSession("bob", "밥", fixedInstant.plusSeconds(10))

            mockCursor(
                "editing:persona:p-1:alice",
                "editing:persona:p-1:bob"
            )
            every { valueOps.get("editing:persona:p-1:alice") } returns mapper.writeValueAsString(alice)
            every { valueOps.get("editing:persona:p-1:bob") } returns mapper.writeValueAsString(bob)

            val sessions = createService().listActive("persona", "p-1")

            sessions shouldHaveSize 2
            // startedAt 오름차순 정렬 — alice 가 먼저
            sessions.map { it.userId } shouldContainExactly listOf("alice", "bob")
        }

        @Test
        fun `excludeUserId 에 일치하는 세션은 필터링된다`() {
            val alice = EditingSession("alice", "앨리스", fixedInstant)
            val bob = EditingSession("bob", "밥", fixedInstant.plusSeconds(10))

            mockCursor(
                "editing:persona:p-1:alice",
                "editing:persona:p-1:bob"
            )
            every { valueOps.get("editing:persona:p-1:alice") } returns mapper.writeValueAsString(alice)
            every { valueOps.get("editing:persona:p-1:bob") } returns mapper.writeValueAsString(bob)

            val sessions = createService().listActive("persona", "p-1", excludeUserId = "alice")

            sessions shouldHaveSize 1
            sessions.single().userId shouldBe "bob"
        }

        @Test
        fun `값이 null 이거나 깨진 JSON 은 건너뛴다`() {
            val goodSession = EditingSession("alice", "앨리스", fixedInstant)
            mockCursor(
                "editing:persona:p-1:alice",
                "editing:persona:p-1:ghost",
                "editing:persona:p-1:garbage"
            )
            every { valueOps.get("editing:persona:p-1:alice") } returns mapper.writeValueAsString(goodSession)
            every { valueOps.get("editing:persona:p-1:ghost") } returns null
            every { valueOps.get("editing:persona:p-1:garbage") } returns "not-json"

            val sessions = createService().listActive("persona", "p-1")

            sessions.map { it.userId } shouldContainExactly listOf("alice")
        }

        @Test
        fun `Redis 장애 시 빈 리스트를 반환한다`() {
            every { connectionFactory.connection } throws RuntimeException("redis down")

            val sessions = createService().listActive("persona", "p-1")

            sessions.shouldBeEmpty()
        }

        @Test
        fun `허용되지 않은 resourceType 은 거부된다`() {
            assertThrows<IllegalArgumentException> {
                createService().listActive("bogus", "p-1")
            }
        }
    }

    /**
     * 주어진 키 목록을 순회하는 mock cursor 를 구성한다.
     */
    private fun mockCursor(vararg keys: String) {
        val cursor = mockk<Cursor<ByteArray>>(relaxed = true)
        val iterator = keys.iterator()
        every { cursor.hasNext() } answers { iterator.hasNext() }
        every { cursor.next() } answers { iterator.next().toByteArray() }
        every { keyCommands.scan(any<ScanOptions>()) } returns cursor
    }
}
