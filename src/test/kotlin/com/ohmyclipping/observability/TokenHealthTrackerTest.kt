package com.ohmyclipping.observability

import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

/**
 * TokenHealthTracker 단위 테스트.
 *
 * Redis 기반 토큰 헬스 상태 캐시가 아래 네 가지 계약을 지키는지 검증한다.
 * 1) OK ↔ EXPIRED/QUOTA_EXHAUSTED/SCOPE_MISMATCH 상태 전이 (쓰기/읽기 정합)
 * 2) OK 기록 시 키가 삭제돼 즉시 OK 로 내려감 (TTL 의존 금지)
 * 3) 비-OK 기록 시 TTL 1시간이 정확히 부여됨
 * 4) Redis 장애 시 인메모리 폴백으로 상태가 유지됨
 */
class TokenHealthTrackerTest {

    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // 매 테스트마다 mock 상태를 초기화해 상호 간섭을 막는다.
        clearMocks(redisTemplate, valueOps, answers = false)
        every { redisTemplate.opsForValue() } returns valueOps
    }

    private fun newTracker(): TokenHealthTracker = TokenHealthTracker(redisTemplate)

    @Nested
    inner class `getStatus 기본 동작` {

        @Test
        fun `Redis에 값이 없으면 Slack 토큰 상태는 OK로 반환된다`() {
            // 저장된 값이 전혀 없을 때 안전한 기본값(OK)으로 폴백하는지 확인.
            every { valueOps.get(TokenHealthTracker.KEY_SLACK_BOT) } returns null

            val sut = newTracker()

            sut.slackBotStatus() shouldBe TokenStatus.OK
        }

        @Test
        fun `Redis에 값이 없으면 Gemini 토큰 상태도 OK로 반환된다`() {
            every { valueOps.get(TokenHealthTracker.KEY_GEMINI) } returns null

            val sut = newTracker()

            sut.geminiStatus() shouldBe TokenStatus.OK
        }

        @Test
        fun `알 수 없는 raw 문자열은 OK로 폴백한다`() {
            // 과거 버전에서 남긴 값이 현재 enum 과 맞지 않을 경우에도 배너가 깨지지 않아야 한다.
            every { valueOps.get(TokenHealthTracker.KEY_SLACK_BOT) } returns "gibberish"

            val sut = newTracker()

            sut.slackBotStatus() shouldBe TokenStatus.OK
        }
    }

    @Nested
    inner class `Slack 토큰 상태 전이` {

        @Test
        fun `OK에서 EXPIRED로 전이하고 다시 OK로 auto-reset된다`() {
            val sut = newTracker()

            // 초기 상태: 저장된 값 없음 -> OK
            every { valueOps.get(TokenHealthTracker.KEY_SLACK_BOT) } returns null
            sut.slackBotStatus() shouldBe TokenStatus.OK

            // EXPIRED 로 전이 -> Redis 에 TTL 과 함께 기록
            sut.recordSlackBot(TokenStatus.EXPIRED)
            verify(exactly = 1) {
                valueOps.set(TokenHealthTracker.KEY_SLACK_BOT, "EXPIRED", Duration.ofHours(1))
            }

            // Redis 가 EXPIRED 를 돌려주면 상태 조회도 EXPIRED
            every { valueOps.get(TokenHealthTracker.KEY_SLACK_BOT) } returns "EXPIRED"
            sut.slackBotStatus() shouldBe TokenStatus.EXPIRED

            // OK 기록 시 키 삭제로 auto-reset
            sut.recordSlackBot(TokenStatus.OK)
            verify(exactly = 1) { redisTemplate.delete(TokenHealthTracker.KEY_SLACK_BOT) }

            every { valueOps.get(TokenHealthTracker.KEY_SLACK_BOT) } returns null
            sut.slackBotStatus() shouldBe TokenStatus.OK
        }

        @Test
        fun `OK에서 SCOPE_MISMATCH로 전이한다`() {
            // Slack 전용 오류 - scope 부족 (예: chat:write 누락).
            val sut = newTracker()

            sut.recordSlackBot(TokenStatus.SCOPE_MISMATCH)

            verify(exactly = 1) {
                valueOps.set(
                    TokenHealthTracker.KEY_SLACK_BOT,
                    "SCOPE_MISMATCH",
                    Duration.ofHours(1)
                )
            }

            every { valueOps.get(TokenHealthTracker.KEY_SLACK_BOT) } returns "SCOPE_MISMATCH"
            sut.slackBotStatus() shouldBe TokenStatus.SCOPE_MISMATCH
        }
    }

    @Nested
    inner class `Gemini 토큰 상태 전이` {

        @Test
        fun `OK에서 QUOTA_EXHAUSTED로 전이하고 다시 OK로 auto-reset된다`() {
            val sut = newTracker()

            // QUOTA_EXHAUSTED 로 전이
            sut.recordGemini(TokenStatus.QUOTA_EXHAUSTED)
            verify(exactly = 1) {
                valueOps.set(
                    TokenHealthTracker.KEY_GEMINI,
                    "QUOTA_EXHAUSTED",
                    Duration.ofHours(1)
                )
            }

            every { valueOps.get(TokenHealthTracker.KEY_GEMINI) } returns "QUOTA_EXHAUSTED"
            sut.geminiStatus() shouldBe TokenStatus.QUOTA_EXHAUSTED

            // OK 복구 -> 키 삭제
            sut.recordGemini(TokenStatus.OK)
            verify(exactly = 1) { redisTemplate.delete(TokenHealthTracker.KEY_GEMINI) }

            every { valueOps.get(TokenHealthTracker.KEY_GEMINI) } returns null
            sut.geminiStatus() shouldBe TokenStatus.OK
        }
    }

    @Nested
    inner class `TTL 부여 검증` {

        @Test
        fun `비-OK 상태 기록 시 TTL이 정확히 1시간으로 설정된다`() {
            // 관리자 수동 개입 전까지 상태가 살아있어야 하므로 TTL 이 1시간인지 명시적으로 확인한다.
            val sut = newTracker()
            val ttlSlot = slot<Duration>()
            every { valueOps.set(any(), any(), capture(ttlSlot)) } returns Unit

            sut.recordSlackBot(TokenStatus.EXPIRED)

            ttlSlot.captured shouldBe Duration.ofHours(1)
        }

        @Test
        fun `OK 기록 시 set이 호출되지 않고 delete만 호출된다`() {
            // OK 는 TTL 에 의존하지 않고 즉시 키 삭제로 표현된다.
            val sut = newTracker()

            sut.recordSlackBot(TokenStatus.OK)

            verify(exactly = 0) { valueOps.set(any(), any(), any<Duration>()) }
            verify(exactly = 1) { redisTemplate.delete(TokenHealthTracker.KEY_SLACK_BOT) }
        }
    }

    @Nested
    inner class `Redis 장애 시 인메모리 폴백` {

        @Test
        fun `Redis set 실패 시에도 인메모리 폴백으로 상태가 조회된다`() {
            // 쓰기 경로에서 Redis 가 죽어도 인메모리에는 남아 다음 조회가 제대로 동작해야 한다.
            every {
                valueOps.set(any<String>(), any<String>(), any<Duration>())
            } throws RuntimeException("redis-down")

            val sut = newTracker()
            sut.recordSlackBot(TokenStatus.EXPIRED)

            // 이후 조회 시 Redis 가 여전히 죽어있어도 인메모리 폴백에서 EXPIRED 를 돌려준다.
            every { valueOps.get(TokenHealthTracker.KEY_SLACK_BOT) } throws RuntimeException("redis-down")

            sut.slackBotStatus() shouldBe TokenStatus.EXPIRED
        }

        @Test
        fun `Redis get 실패 시 인메모리에 값이 없으면 OK로 폴백한다`() {
            // Redis 도 죽고 인메모리도 비어있으면 배너를 띄우지 않도록 OK 로 방어한다.
            every { valueOps.get(any()) } throws RuntimeException("redis-down")

            val sut = newTracker()

            sut.slackBotStatus() shouldBe TokenStatus.OK
            sut.geminiStatus() shouldBe TokenStatus.OK
        }

        @Test
        fun `Redis delete 실패해도 인메모리 폴백은 OK로 복구된다`() {
            // 인메모리에 EXPIRED 가 들어간 상태에서 OK 기록 시 delete 가 실패해도 메모리는 비워진다.
            val sut = newTracker()

            // Step 1: Redis set 실패 -> 인메모리에 EXPIRED 저장
            every {
                valueOps.set(any<String>(), any<String>(), any<Duration>())
            } throws RuntimeException("redis-down")
            sut.recordSlackBot(TokenStatus.EXPIRED)

            // Step 2: Redis delete 도 실패하지만 인메모리는 비워져야 한다
            every { redisTemplate.delete(TokenHealthTracker.KEY_SLACK_BOT) } throws
                RuntimeException("redis-down")
            sut.recordSlackBot(TokenStatus.OK)

            // Step 3: Redis 가 여전히 죽어도 인메모리에서 null 을 돌려주므로 OK
            every { valueOps.get(TokenHealthTracker.KEY_SLACK_BOT) } throws RuntimeException("redis-down")
            sut.slackBotStatus() shouldBe TokenStatus.OK
        }
    }

    @Nested
    inner class `Slack 과 Gemini 키 분리` {

        @Test
        fun `Slack 상태를 기록해도 Gemini 상태에는 영향이 없다`() {
            // 서로 다른 토큰이 같은 키 공간을 공유하면 안 되므로 키 분리를 명시적으로 검증한다.
            val sut = newTracker()

            sut.recordSlackBot(TokenStatus.EXPIRED)

            verify(exactly = 0) {
                valueOps.set(TokenHealthTracker.KEY_GEMINI, any(), any<Duration>())
            }

            every { valueOps.get(TokenHealthTracker.KEY_GEMINI) } returns null
            sut.geminiStatus() shouldBe TokenStatus.OK
        }
    }
}

/**
 * TokenStatus enum 의 raw 파싱 규칙을 독립적으로 검증한다.
 */
class TokenStatusTest {

    @Test
    fun `fromRaw는 null과 blank 입력에 대해 OK를 돌려준다`() {
        TokenStatus.fromRaw(null) shouldBe TokenStatus.OK
        TokenStatus.fromRaw("") shouldBe TokenStatus.OK
        TokenStatus.fromRaw("   ") shouldBe TokenStatus.OK
    }

    @Test
    fun `fromRaw는 대소문자와 공백을 무시하고 enum 이름을 매칭한다`() {
        TokenStatus.fromRaw("expired") shouldBe TokenStatus.EXPIRED
        TokenStatus.fromRaw("  EXPIRED  ") shouldBe TokenStatus.EXPIRED
        TokenStatus.fromRaw("Scope_Mismatch") shouldBe TokenStatus.SCOPE_MISMATCH
        TokenStatus.fromRaw("QUOTA_EXHAUSTED") shouldBe TokenStatus.QUOTA_EXHAUSTED
    }

    @Test
    fun `fromRaw는 알 수 없는 값에 대해 OK로 폴백한다`() {
        // 운영 중 enum 이 바뀌더라도 배너가 죽지 않도록 안전한 기본값을 돌려준다.
        TokenStatus.fromRaw("mystery") shouldBe TokenStatus.OK
        TokenStatus.fromRaw("12345") shouldBe TokenStatus.OK
    }
}
