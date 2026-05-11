package com.clipping.mcpserver.mcp

import com.clipping.mcpserver.config.RedisRateLimitService
import com.clipping.mcpserver.error.RateLimitExceededException
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerRunTracker
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [McpRateLimiter] 단위 테스트.
 *
 * - actor 미지정 시 [McpCallerContext] 의 tokenKid 를 사용하는지,
 * - tokenKid 도 없으면 "anonymous" 로 폴백하는지,
 * - 서로 다른 tokenKid 는 서로 다른 쿼터 키를 생성하는지를 검증한다.
 */
class McpRateLimiterTest {

    private val redisRateLimitService = mockk<RedisRateLimitService>()
    private val metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())

    // KST 기준 2026-04-21 09:00 (= UTC 00:00) 로 고정 — 재시도 시각 포맷을 재현 가능하게 검증.
    private val fixedNow: Instant = Instant.parse("2026-04-21T00:00:00Z")
    private val fixedClock: Clock = object : Clock() {
        override fun instant(): Instant = fixedNow
        override fun withZone(zone: ZoneId?): Clock = this
        override fun getZone(): ZoneId = ZoneOffset.UTC
    }
    private val sut = McpRateLimiter(redisRateLimitService, metrics, fixedClock)

    @AfterEach
    fun tearDown() {
        // 다음 테스트에 ThreadLocal 이 누수되지 않도록 정리한다.
        McpCallerContext.clear()
    }

    @Nested
    inner class `actor 자동 해석` {

        @Test
        fun `tokenKid 가 바인딩되어 있으면 그 값을 actor 로 사용한다`() {
            McpCallerContext.setTokenKid("abcd1234")
            every {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_collect",
                    actor = "abcd1234",
                    dimension = "c1",
                    maxRequests = 5,
                    windowSeconds = 60,
                )
            } returns false

            sut.checkOrThrow(
                toolName = "admin_collect",
                maxRequests = 5,
                windowSeconds = 60,
                dimension = "c1",
            )

            verify(exactly = 1) {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_collect",
                    actor = "abcd1234",
                    dimension = "c1",
                    maxRequests = 5,
                    windowSeconds = 60,
                )
            }
        }

        @Test
        fun `tokenKid 가 없으면 anonymous 로 폴백한다`() {
            every {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_collect",
                    actor = "anonymous",
                    dimension = null,
                    maxRequests = 5,
                    windowSeconds = 60,
                )
            } returns false

            sut.checkOrThrow("admin_collect", maxRequests = 5, windowSeconds = 60)

            verify(exactly = 1) {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_collect",
                    actor = "anonymous",
                    dimension = null,
                    maxRequests = 5,
                    windowSeconds = 60,
                )
            }
        }

        @Test
        fun `명시된 actor 가 있으면 tokenKid 보다 우선한다`() {
            McpCallerContext.setTokenKid("abcd1234")
            every {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_collect",
                    actor = "override",
                    dimension = null,
                    maxRequests = 5,
                    windowSeconds = 60,
                )
            } returns false

            sut.checkOrThrow(
                "admin_collect",
                maxRequests = 5,
                windowSeconds = 60,
                actor = "override",
            )

            verify(exactly = 1) {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_collect",
                    actor = "override",
                    dimension = null,
                    maxRequests = 5,
                    windowSeconds = 60,
                )
            }
        }
    }

    @Nested
    inner class `토큰 별 쿼터 격리` {

        @Test
        fun `두 토큰은 서로 다른 actor 로 쿼터를 소비한다`() {
            // 토큰 A 에는 한도 초과, 토큰 B 에는 여유가 있는 상황을 구성한다.
            every {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_send_digest",
                    actor = "tokenA",
                    dimension = null,
                    maxRequests = 2,
                    windowSeconds = 3600,
                )
            } returns true
            every {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_send_digest",
                    actor = "tokenB",
                    dimension = null,
                    maxRequests = 2,
                    windowSeconds = 3600,
                )
            } returns false

            // A 호출 → 차단
            McpCallerContext.setTokenKid("tokenA")
            assertThrows<RateLimitExceededException> {
                sut.checkOrThrow("admin_send_digest", maxRequests = 2, windowSeconds = 3600)
            }

            // B 호출 → 통과 (A 쿼터와 무관)
            McpCallerContext.setTokenKid("tokenB")
            sut.checkOrThrow("admin_send_digest", maxRequests = 2, windowSeconds = 3600)

            verify(exactly = 1) {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_send_digest",
                    actor = "tokenA",
                    dimension = null,
                    maxRequests = 2,
                    windowSeconds = 3600,
                )
            }
            verify(exactly = 1) {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_send_digest",
                    actor = "tokenB",
                    dimension = null,
                    maxRequests = 2,
                    windowSeconds = 3600,
                )
            }
        }

        @Test
        fun `같은 토큰 반복 호출은 동일 actor 로 공유 쿼터에 들어간다`() {
            McpCallerContext.setTokenKid("tokenA")
            every {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_collect",
                    actor = "tokenA",
                    dimension = "c1",
                    maxRequests = 5,
                    windowSeconds = 3600,
                )
            } returnsMany listOf(false, false, true)

            sut.checkOrThrow(
                "admin_collect", maxRequests = 5, windowSeconds = 3600, dimension = "c1",
            )
            sut.checkOrThrow(
                "admin_collect", maxRequests = 5, windowSeconds = 3600, dimension = "c1",
            )
            val ex = assertThrows<RateLimitExceededException> {
                sut.checkOrThrow(
                    "admin_collect", maxRequests = 5, windowSeconds = 3600, dimension = "c1",
                )
            }

            ex.retryAfterSeconds shouldBe 3600L
            verify(exactly = 3) {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_collect",
                    actor = "tokenA",
                    dimension = "c1",
                    maxRequests = 5,
                    windowSeconds = 3600,
                )
            }
        }
    }

    @Nested
    inner class `retry 시각 메시지 보강` {

        @Test
        fun `거부 메시지는 한글 도구명 + 한도 + KST 절대 재시도 시각을 담는다`() {
            McpCallerContext.setTokenKid("tokenA")
            every {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_send_digest",
                    actor = "tokenA",
                    dimension = null,
                    maxRequests = 2,
                    windowSeconds = 3600,
                )
            } returns true

            val ex = assertThrows<RateLimitExceededException> {
                sut.checkOrThrow("admin_send_digest", maxRequests = 2, windowSeconds = 3600)
            }

            // fixedNow = UTC 00:00 = KST 09:00. 1시간 후 = KST 10:00.
            ex.message shouldContain "admin_send_digest"
            ex.message shouldContain "3600"
            ex.message shouldContain "2회"
            ex.message shouldContain "10:00:00 KST"
        }

        @Test
        fun `retryAt 필드가 now + windowSeconds 로 채워진다`() {
            every {
                redisRateLimitService.isRateLimitedForTool(
                    toolName = "admin_export",
                    actor = "anonymous",
                    dimension = "c1",
                    maxRequests = 5,
                    windowSeconds = 3600,
                )
            } returns true

            val ex = assertThrows<RateLimitExceededException> {
                sut.checkOrThrow("admin_export", maxRequests = 5, windowSeconds = 3600, dimension = "c1")
            }

            ex.retryAt shouldBe fixedNow.plusSeconds(3600)
            ex.retryAfterSeconds shouldBe 3600L
        }
    }
}
