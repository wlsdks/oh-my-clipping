package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.McpCallerContext
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.service.SlackMessageSender
import com.ohmyclipping.service.port.ClippingPipelinePort
import com.ohmyclipping.service.pipeline.toPipelineDigestResult
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * admin_send_digest 단위 테스트.
 *
 * 검증 포인트:
 *  - 멱등성: 같은 (actor, categoryId, KST 날짜, channel) 조합 재호출은
 *    ConflictException(JSON-RPC -32020 ConflictException) 으로 차단되고
 *    두 번째 호출은 ClippingService 에 닿지 않는다.
 *  - TTL 경과 후에는 다시 발송이 허용된다.
 *  - confirmationSummary 가 maxItems / 채널명과 일치하지 않으면 InvalidInputException.
 *  - confirmationSummary 를 생략하면 기존처럼 발송이 그대로 진행된다 (backward compatible).
 */
class AdminSendDigestToolTest {

    private val clippingService = mockk<ClippingPipelinePort>()
    private val slackMessageSender = mockk<SlackMessageSender>(relaxed = true)
    private val rateLimiter = mockk<McpRateLimiter>()
    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)

    // 고정 시계로 멱등성 TTL 만료 시나리오를 결정적으로 검증한다.
    private val fixedNow = Instant.parse("2026-04-08T12:00:00Z")
    private var currentNow: Instant = fixedNow
    private val mutableClock: Clock = object : Clock() {
        override fun instant(): Instant = currentNow
        override fun withZone(zone: java.time.ZoneId?): Clock = this
        override fun getZone(): java.time.ZoneId = ZoneOffset.UTC
    }
    private val idempotencyCache = AdminSendDigestIdempotencyCache(redisTemplate, mutableClock)

    private val tool = AdminSendDigestTool(
        clippingPipelinePort = clippingService,
        slackMessageSender = slackMessageSender,
        rateLimiter = rateLimiter,
        idempotencyCache = idempotencyCache,
    )

    private val digestResult = DigestResult(
        categoryId = "c1",
        categoryName = "Tech",
        unsentOnly = true,
        totalCandidates = 5,
        selectedCount = 3,
        postedToSlack = true,
        slackChannelId = "C0123ABC",
        slackMessageTs = "1234.5678",
        markedSentCount = 3,
        digestText = "digest",
        items = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        currentNow = fixedNow
        idempotencyCache.clear()
        McpCallerContext.setTokenKid("tokenA")
        every { redisTemplate.opsForValue() } returns valueOps
        every { redisTemplate.connectionFactory } returns null
        every { valueOps.setIfAbsent(any(), any(), any<java.time.Duration>()) } throws RuntimeException("redis down")
        every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
        every { clippingService.digest(any(), any(), any(), any(), any()) } returns digestResult.toPipelineDigestResult()
    }

    @AfterEach
    fun tearDown() {
        McpCallerContext.clear()
    }

    @Nested
    inner class `멱등성` {

        @Test
        fun `같은 actor 카테고리 채널 조합 재호출은 ConflictException 으로 차단된다`() {
            every {
                slackMessageSender.getChannelInfo(botToken = null, channelId = "C0123ABC")
            } returns SlackMessageSender.SlackChannel(id = "C0123ABC", name = "tech-news", isPrivate = false)

            // 첫 번째 호출 — 발송 성공
            tool.admin_send_digest(
                categoryId = "c1",
                maxItems = null,
                unsentOnly = null,
                slackChannelId = "C0123ABC",
                confirmationSummary = null,
            )
            // 두 번째 호출 — 멱등성 차단
            val second = tool.admin_send_digest(
                categoryId = "c1",
                maxItems = null,
                unsentOnly = null,
                slackChannelId = "C0123ABC",
                confirmationSummary = null,
            )

            second shouldContain "\"error\""
            second shouldContain "이미 같은 카테고리·채널·오늘자 조합"
            // ClippingService 는 첫 번째 호출에서만 실행된다. sendToSlack 은 내부적으로 true 고정.
            verify(exactly = 1) { clippingService.digest("c1", null, null, true, "C0123ABC") }
        }

        @Test
        fun `다른 토큰은 각자 다른 멱등성 쿼터를 갖는다`() {
            McpCallerContext.setTokenKid("tokenA")
            tool.admin_send_digest(
                categoryId = "c1", maxItems = null, unsentOnly = null,
                slackChannelId = null, confirmationSummary = null,
            )
            McpCallerContext.setTokenKid("tokenB")
            tool.admin_send_digest(
                categoryId = "c1", maxItems = null, unsentOnly = null,
                slackChannelId = null, confirmationSummary = null,
            )

            verify(exactly = 2) { clippingService.digest("c1", null, null, true, null) }
        }

        @Test
        fun `TTL 24시간 이내 재호출은 차단된다 (10분 TTL 이던 이전 설계의 중복 발송 구멍 차단)`() {
            tool.admin_send_digest(
                categoryId = "c1", maxItems = null, unsentOnly = null,
                slackChannelId = null, confirmationSummary = null,
            )
            // 이전 10분 TTL 에서는 이 시점에 통과되었지만, 24h TTL 로 연장되면서 같은 하루 내
            // 중복 발송이 원천 차단된다 — (카테고리, 채널, KST 날짜) 조합은 하루 한 번.
            currentNow = fixedNow.plusSeconds(12 * 60 * 60)
            val second = tool.admin_send_digest(
                categoryId = "c1", maxItems = null, unsentOnly = null,
                slackChannelId = null, confirmationSummary = null,
            )

            second shouldContain "\"error\""
            second shouldContain "이미 같은 카테고리·채널·오늘자 조합으로 발송됨"
            verify(exactly = 1) { clippingService.digest("c1", null, null, true, null) }
        }

        @Test
        fun `TTL 24시간 경과 후에는 동일 조합도 다시 허용된다 (다음 날 발송 경로)`() {
            tool.admin_send_digest(
                categoryId = "c1", maxItems = null, unsentOnly = null,
                slackChannelId = null, confirmationSummary = null,
            )
            // 24시간 + 1초 경과 — 캐시 expiresAt 만 보는 테스트. 실 운영에선 KST 날짜가 바뀌며
            // 키 자체가 달라져 자연스럽게 새 엔트리가 된다.
            currentNow = fixedNow.plusSeconds(24 * 60 * 60 + 1)
            tool.admin_send_digest(
                categoryId = "c1", maxItems = null, unsentOnly = null,
                slackChannelId = null, confirmationSummary = null,
            )

            verify(exactly = 2) { clippingService.digest("c1", null, null, true, null) }
        }
    }

    @Nested
    inner class `확인 요약 검증` {

        @Test
        fun `confirmationSummary 의 N건 이 maxItems 와 다르면 InvalidInputException`() {
            every {
                slackMessageSender.getChannelInfo(botToken = null, channelId = "C0123ABC")
            } returns SlackMessageSender.SlackChannel(id = "C0123ABC", name = "tech-news", isPrivate = false)

            val json = tool.admin_send_digest(
                categoryId = "c1",
                maxItems = 3,
                unsentOnly = null,
                slackChannelId = "C0123ABC",
                confirmationSummary = "5건 to #tech-news",
            )

            json shouldContain "\"error\""
            json shouldContain "확인 요약의 아이템 수"
            verify(exactly = 0) { clippingService.digest(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `confirmationSummary 채널명이 실제 채널과 다르면 InvalidInputException`() {
            every {
                slackMessageSender.getChannelInfo(botToken = null, channelId = "C0123ABC")
            } returns SlackMessageSender.SlackChannel(id = "C0123ABC", name = "tech-news", isPrivate = false)

            val json = tool.admin_send_digest(
                categoryId = "c1",
                maxItems = 3,
                unsentOnly = null,
                slackChannelId = "C0123ABC",
                confirmationSummary = "3건 to #product-launch",
            )

            json shouldContain "\"error\""
            json shouldContain "채널명"
        }

        @Test
        fun `confirmationSummary 가 없으면 검증 없이 발송이 진행된다`() {
            tool.admin_send_digest(
                categoryId = "c1",
                maxItems = 3,
                unsentOnly = null,
                slackChannelId = null,
                confirmationSummary = null,
            )

            verify(exactly = 1) { clippingService.digest("c1", 3, null, true, null) }
        }

        @Test
        fun `올바른 confirmationSummary 는 그대로 통과한다`() {
            every {
                slackMessageSender.getChannelInfo(botToken = null, channelId = "C0123ABC")
            } returns SlackMessageSender.SlackChannel(id = "C0123ABC", name = "tech-news", isPrivate = false)

            tool.admin_send_digest(
                categoryId = "c1",
                maxItems = 3,
                unsentOnly = null,
                slackChannelId = "C0123ABC",
                confirmationSummary = "3건 to #tech-news",
            )

            verify(exactly = 1) { clippingService.digest("c1", 3, null, true, "C0123ABC") }
        }

        @Test
        fun `포맷이 전혀 다른 confirmationSummary 는 형식 오류로 거부한다`() {
            val json = tool.admin_send_digest(
                categoryId = "c1",
                maxItems = 3,
                unsentOnly = null,
                slackChannelId = null,
                confirmationSummary = "보내줘",
            )

            json shouldContain "\"error\""
            json shouldContain "형식이 올바르지 않습니다"
        }

        @Test
        fun `sendToSlack 파라미터는 PR-06 에서 제거됐으며 항상 Slack 에 게시한다`() {
            // tools/list 에 sendToSlack 이 노출되지 않는지와, 내부에서 true 고정됨을 검증.
            tool.admin_send_digest(
                categoryId = "c1", maxItems = null, unsentOnly = null,
                slackChannelId = null, confirmationSummary = null,
            )
            // sendToSlack 위치(4번째 arg) 가 true 로 들어가야 한다.
            verify(exactly = 1) { clippingService.digest("c1", null, null, true, null) }
        }
    }
}
