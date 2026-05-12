package com.ohmyclipping.support

import com.ohmyclipping.error.ConflictException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * IdempotencyKeyService 단위 테스트.
 *
 * Redis 동작을 mockk 로 시뮬레이션하되 "한 번만 실행", "캐시 재사용", "Redis 장애 시 fail-open",
 * "동시성 하에서 supplier 1회 실행" 네 가지 핵심 보장을 검증한다.
 */
class IdempotencyKeyServiceTest {

    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)

    init {
        every { redisTemplate.opsForValue() } returns valueOps
    }

    private val sut = IdempotencyKeyService(redisTemplate, jacksonObjectMapper())

    data class Payload(val id: String, val name: String)

    @Nested
    inner class `executeIfKeyPresent 테스트` {

        @Test
        fun `key가 null이면 supplier만 실행하고 Redis를 건드리지 않는다`() {
            val counter = AtomicInteger(0)

            val result = sut.executeIfKeyPresent(
                actor = "alice",
                key = null,
                resultClass = Payload::class.java
            ) {
                counter.incrementAndGet()
                Payload("1", "first")
            }

            result shouldBe Payload("1", "first")
            counter.get() shouldBe 1
            verify(exactly = 0) { valueOps.get(any()) }
            verify(exactly = 0) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
        }

        @Test
        fun `key가 blank이면 supplier만 실행한다`() {
            val counter = AtomicInteger(0)

            val result = sut.executeIfKeyPresent(
                actor = "alice",
                key = "  ",
                resultClass = Payload::class.java
            ) {
                counter.incrementAndGet()
                Payload("1", "first")
            }

            result shouldBe Payload("1", "first")
            counter.get() shouldBe 1
        }

        @Test
        fun `key가 있으면 executeIdempotent 경로로 위임된다`() {
            val expectedKey = "idempotency:alice:abc"
            every { valueOps.get(expectedKey) } returns null
            every { valueOps.setIfAbsent(expectedKey, any(), any<Duration>()) } returns true

            val result = sut.executeIfKeyPresent(
                actor = "alice",
                key = "abc",
                resultClass = Payload::class.java
            ) { Payload("1", "first") }

            result shouldBe Payload("1", "first")
            verify(exactly = 1) { valueOps.setIfAbsent(expectedKey, any(), any<Duration>()) }
        }
    }

    @Nested
    inner class `executeIdempotent 테스트` {

        @Test
        fun `첫 호출은 supplier를 실행하고 결과를 JSON으로 캐시한다`() {
            val expectedKey = "idempotency:alice:req-1"
            // 1) 캐시 조회 → 없음
            every { valueOps.get(expectedKey) } returns null
            // 2) 락 획득 성공
            every { valueOps.setIfAbsent(expectedKey, "__PROCESSING__", IdempotencyKeyService.LOCK_TTL) } returns true

            val counter = AtomicInteger(0)

            val result = sut.executeIdempotent(
                actor = "alice",
                key = "req-1",
                resultClass = Payload::class.java
            ) {
                counter.incrementAndGet()
                Payload("1", "first")
            }

            result shouldBe Payload("1", "first")
            counter.get() shouldBe 1
            // 최종 결과가 CACHE_TTL 로 저장돼야 한다.
            verify(exactly = 1) {
                valueOps.set(expectedKey, match<String> { it.contains("\"id\":\"1\"") }, IdempotencyKeyService.CACHE_TTL)
            }
        }

        @Test
        fun `같은 key 재호출이면 supplier를 실행하지 않고 캐시를 반환한다`() {
            val expectedKey = "idempotency:alice:req-1"
            val cachedJson = """{"id":"1","name":"first"}"""
            every { valueOps.get(expectedKey) } returns cachedJson

            val counter = AtomicInteger(0)

            val result = sut.executeIdempotent(
                actor = "alice",
                key = "req-1",
                resultClass = Payload::class.java
            ) {
                counter.incrementAndGet()
                Payload("x", "should not run")
            }

            result shouldBe Payload("1", "first")
            counter.get() shouldBe 0
            verify(exactly = 0) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
            verify(exactly = 0) { valueOps.set(any(), any(), any<Duration>()) }
        }

        @Test
        fun `다른 actor가 같은 key를 써도 별개 캐시로 처리된다`() {
            val keyAlice = "idempotency:alice:req-1"
            val keyBob = "idempotency:bob:req-1"
            every { valueOps.get(keyAlice) } returns null
            every { valueOps.get(keyBob) } returns null
            every { valueOps.setIfAbsent(keyAlice, any(), any<Duration>()) } returns true
            every { valueOps.setIfAbsent(keyBob, any(), any<Duration>()) } returns true

            val counter = AtomicInteger(0)
            val make: () -> Payload = {
                counter.incrementAndGet()
                Payload("ok", "run")
            }

            sut.executeIdempotent("alice", "req-1", Payload::class.java, make)
            sut.executeIdempotent("bob", "req-1", Payload::class.java, make)

            // 각자 자기 캐시 경로를 탔으므로 supplier 는 2번 실행된다.
            counter.get() shouldBe 2
        }

        @Test
        fun `캐시된 JSON이 손상되면 supplier를 재실행한다`() {
            val expectedKey = "idempotency:alice:req-1"
            every { valueOps.get(expectedKey) } returns "not-json"

            val counter = AtomicInteger(0)
            val result = sut.executeIdempotent(
                actor = "alice",
                key = "req-1",
                resultClass = Payload::class.java
            ) {
                counter.incrementAndGet()
                Payload("recovered", "recovered")
            }

            result shouldBe Payload("recovered", "recovered")
            counter.get() shouldBe 1
        }

        @Test
        fun `supplier가 예외를 던지면 processing 마커를 제거하고 예외를 그대로 던진다`() {
            val expectedKey = "idempotency:alice:req-1"
            every { valueOps.get(expectedKey) } returns null
            every { valueOps.setIfAbsent(expectedKey, any(), any<Duration>()) } returns true

            val ex = assertThrows(RuntimeException::class.java) {
                sut.executeIdempotent(
                    actor = "alice",
                    key = "req-1",
                    resultClass = Payload::class.java
                ) { throw RuntimeException("boom") }
            }
            ex.message shouldBe "boom"
            verify(exactly = 1) { redisTemplate.delete(expectedKey) }
            verify(exactly = 0) { valueOps.set(any(), any(), any<Duration>()) }
        }
    }

    @Nested
    inner class `Fail-open 동작 테스트` {

        @Test
        fun `Redis GET 실패 시 락 획득을 시도하고 supplier를 실행한다`() {
            val expectedKey = "idempotency:alice:req-1"
            every { valueOps.get(expectedKey) } throws RuntimeException("Redis down")
            every { valueOps.setIfAbsent(expectedKey, any(), any<Duration>()) } returns true

            val counter = AtomicInteger(0)
            val result = sut.executeIdempotent(
                actor = "alice",
                key = "req-1",
                resultClass = Payload::class.java
            ) {
                counter.incrementAndGet()
                Payload("1", "first")
            }

            result shouldBe Payload("1", "first")
            counter.get() shouldBe 1
        }

        @Test
        fun `Redis SET NX 실패 시에도 supplier는 실행된다`() {
            val expectedKey = "idempotency:alice:req-1"
            every { valueOps.get(expectedKey) } returns null
            every { valueOps.setIfAbsent(expectedKey, any(), any<Duration>()) } throws RuntimeException("Redis down")

            val counter = AtomicInteger(0)
            val result = sut.executeIdempotent(
                actor = "alice",
                key = "req-1",
                resultClass = Payload::class.java
            ) {
                counter.incrementAndGet()
                Payload("1", "first")
            }

            // 폴링 경로를 타지만 캐시가 비어 있으면 supplier 재실행한다.
            counter.get() shouldBe 1
            result shouldBe Payload("1", "first")
        }

        @Test
        fun `캐시 SET 실패해도 supplier 결과는 반환한다`() {
            val expectedKey = "idempotency:alice:req-1"
            every { valueOps.get(expectedKey) } returns null
            every { valueOps.setIfAbsent(expectedKey, any(), any<Duration>()) } returns true
            every { valueOps.set(expectedKey, any(), any<Duration>()) } throws RuntimeException("Redis down")

            val result = sut.executeIdempotent(
                actor = "alice",
                key = "req-1",
                resultClass = Payload::class.java
            ) { Payload("1", "first") }

            result shouldBe Payload("1", "first")
        }
    }

    @Nested
    inner class `동시성 테스트` {

        @Test
        fun `동일 key 동시 호출 시 첫 요청만 supplier를 실행하고 나머지는 캐시를 기다린다`() {
            val expectedKey = "idempotency:alice:concurrent"
            val storage = AtomicReference<String?>(null)

            every { valueOps.get(expectedKey) } answers { storage.get() }
            // setIfAbsent 는 최초 호출에만 true 를 반환해 락 1개만 부여한다.
            var lockTaken = false
            every { valueOps.setIfAbsent(expectedKey, any(), any<Duration>()) } answers {
                synchronized(storage) {
                    if (!lockTaken) {
                        lockTaken = true
                        storage.set(IdempotencyKeyService.PROCESSING_MARKER)
                        true
                    } else {
                        false
                    }
                }
            }
            every { valueOps.set(expectedKey, any(), any<Duration>()) } answers {
                storage.set(secondArg() as String)
            }

            val supplierCalls = AtomicInteger(0)
            val executor = Executors.newFixedThreadPool(4)
            val start = CountDownLatch(1)
            val results = List(4) {
                executor.submit<Payload> {
                    start.await()
                    sut.executeIdempotent(
                        actor = "alice",
                        key = "concurrent",
                        resultClass = Payload::class.java
                    ) {
                        supplierCalls.incrementAndGet()
                        TestSleeper.sleep(50, "hold idempotency supplier lock")
                        Payload("1", "only-once")
                    }
                }
            }
            start.countDown()
            val all = results.map { it.get(5, TimeUnit.SECONDS) }
            executor.shutdown()

            // 모든 결과가 동일해야 하고 supplier 는 정확히 1번만 실행돼야 한다.
            supplierCalls.get() shouldBe 1
            all.forEach { it shouldBe Payload("1", "only-once") }
        }

        @Test
        fun `처리 중 마커가 계속 남아 있으면 supplier를 중복 실행하지 않고 충돌로 거부한다`() {
            val expectedKey = "idempotency:alice:slow-request"
            every { valueOps.get(expectedKey) } returns IdempotencyKeyService.PROCESSING_MARKER
            every { valueOps.setIfAbsent(expectedKey, any(), any<Duration>()) } returns false

            mockkObject(InterruptibleSleep)
            every { InterruptibleSleep.sleep(any(), any()) } returns Unit
            val supplierCalls = AtomicInteger(0)

            try {
                val ex = assertThrows(ConflictException::class.java) {
                    sut.executeIdempotent(
                        actor = "alice",
                        key = "slow-request",
                        resultClass = Payload::class.java
                    ) {
                        supplierCalls.incrementAndGet()
                        Payload("duplicated", "should-not-run")
                    }
                }

                ex.message shouldBe "동일한 Idempotency-Key 요청이 아직 처리 중입니다. 잠시 후 다시 시도해 주세요."
                supplierCalls.get() shouldBe 0
            } finally {
                unmockkObject(InterruptibleSleep)
            }
        }
    }

    @Nested
    inner class `remove 테스트` {

        @Test
        fun `remove는 Redis에서 해당 key를 삭제한다`() {
            sut.remove("alice", "req-1")
            verify(exactly = 1) { redisTemplate.delete("idempotency:alice:req-1") }
        }

        @Test
        fun `remove는 Redis 장애 시에도 예외를 던지지 않는다`() {
            every { redisTemplate.delete("idempotency:alice:req-1") } throws RuntimeException("Redis down")
            // when — 예외 없이 정상 종료되어야 한다
            sut.remove("alice", "req-1")
        }
    }

    // JUnit 5 assertThrows 의 짧은 별칭
    private fun <T : Throwable> assertThrows(type: Class<T>, block: () -> Unit): T =
        org.junit.jupiter.api.Assertions.assertThrows(type, block)
}
