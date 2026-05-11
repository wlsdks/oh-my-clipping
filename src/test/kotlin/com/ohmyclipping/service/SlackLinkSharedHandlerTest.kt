package com.ohmyclipping.service

import com.ohmyclipping.config.AppProperties
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException

class SlackLinkSharedHandlerTest {

    private val userEventService = mockk<UserEventService>(relaxed = true)
    private val registry = SimpleMeterRegistry()
    private val trackingUrlParser = TrackingUrlParser(
        AppProperties(baseUrl = "https://clipping.example.com")
    )
    private val handler = SlackLinkSharedHandler(
        trackingUrlParser = trackingUrlParser,
        userEventService = userEventService,
        meterRegistry = registry,
    )

    private fun matchedCount(): Double = registry.counter("share.passive.matched").count()
    private fun rejectedCount(): Double = registry.counter("share.passive.rejected").count()

    @Nested
    inner class `매칭 성공 경로` {

        @Test
        fun `tracking URL 1건이면 savePassiveShare 가 한 번 호출되고 matched 가 증가한다`() {
            val url = "https://clipping.example.com/api/track/click/slack/sum-123"
            handler.handle(
                userId = "U1",
                channelId = "C1",
                messageTs = "1713.1",
                urls = listOf(url)
            )

            verify(exactly = 1) {
                userEventService.savePassiveShare(
                    userId = "U1",
                    summaryId = "sum-123",
                    targetChannelId = "C1",
                    slackMessageTs = "1713.1",
                )
            }
            matchedCount() shouldBe 1.0
            rejectedCount() shouldBe 0.0
        }

        @Test
        fun `legacy query URL 도 매칭된다`() {
            val url = "https://clipping.example.com/api/track/click?sid=sum-legacy&url=x"
            handler.handle("U1", "C1", "1.0", listOf(url))

            verify(exactly = 1) {
                userEventService.savePassiveShare(
                    userId = "U1",
                    summaryId = "sum-legacy",
                    targetChannelId = "C1",
                    slackMessageTs = "1.0",
                )
            }
            matchedCount() shouldBe 1.0
        }

        @Test
        fun `다중 URL 중 일부만 tracking 이면 매칭된 것만 저장된다`() {
            val tracking = "https://clipping.example.com/api/track/click/slack/sum-A"
            val external = "https://news.example.com/article/123"
            val trackingB = "https://clipping.example.com/api/track/click?sid=sum-B"

            handler.handle("U1", "C1", "2.0", listOf(tracking, external, trackingB))

            verify(exactly = 1) {
                userEventService.savePassiveShare(
                    userId = any(),
                    summaryId = "sum-A",
                    targetChannelId = any(),
                    slackMessageTs = any(),
                )
            }
            verify(exactly = 1) {
                userEventService.savePassiveShare(
                    userId = any(),
                    summaryId = "sum-B",
                    targetChannelId = any(),
                    slackMessageTs = any(),
                )
            }
            matchedCount() shouldBe 2.0
            rejectedCount() shouldBe 1.0
        }
    }

    @Nested
    inner class `매칭 실패 경로` {

        @Test
        fun `tracking URL 이 아니면 저장하지 않고 rejected 가 증가한다`() {
            handler.handle(
                userId = "U1",
                channelId = "C1",
                messageTs = "1.0",
                urls = listOf("https://news.example.com/article/123")
            )

            verify(exactly = 0) { userEventService.savePassiveShare(any(), any(), any(), any()) }
            matchedCount() shouldBe 0.0
            rejectedCount() shouldBe 1.0
        }

        @Test
        fun `다른 host 의 tracking-like URL 도 rejected`() {
            handler.handle(
                userId = "U1",
                channelId = "C1",
                messageTs = "1.0",
                urls = listOf("https://evil.example.com/api/track/click/slack/sum-1")
            )

            verify(exactly = 0) { userEventService.savePassiveShare(any(), any(), any(), any()) }
            rejectedCount() shouldBe 1.0
        }

        @Test
        fun `urls 가 비어있으면 아무 일도 하지 않는다`() {
            handler.handle("U1", "C1", "1.0", emptyList())

            verify(exactly = 0) { userEventService.savePassiveShare(any(), any(), any(), any()) }
            matchedCount() shouldBe 0.0
            rejectedCount() shouldBe 0.0
        }
    }

    @Nested
    inner class `dedup idempotency` {

        @Test
        fun `DuplicateKeyException 이 던져지면 조용히 무시하고 counter 증가 없음`() {
            every {
                userEventService.savePassiveShare(any(), any(), any(), any())
            } throws DuplicateKeyException("ux_user_events_share_dedup 위반")

            val url = "https://clipping.example.com/api/track/click/slack/sum-dup"
            handler.handle("U1", "C1", "1.0", listOf(url))

            verify(exactly = 1) { userEventService.savePassiveShare(any(), any(), any(), any()) }
            // 중복 저장 시도였지만 matched 로 카운트하지 않고, rejected 로도 카운트하지 않는다.
            matchedCount() shouldBe 0.0
            rejectedCount() shouldBe 0.0
        }

        @Test
        fun `저장 중 예기치 못한 예외는 rejected 로 집계`() {
            every {
                userEventService.savePassiveShare(any(), any(), any(), any())
            } throws RuntimeException("boom")

            val url = "https://clipping.example.com/api/track/click/slack/sum-x"
            handler.handle("U1", "C1", "1.0", listOf(url))

            matchedCount() shouldBe 0.0
            rejectedCount() shouldBe 1.0
        }
    }

    @Nested
    inner class `식별자 guard` {

        @Test
        fun `userId 가 비어있으면 전체 URL 을 rejected 로 처리`() {
            val url = "https://clipping.example.com/api/track/click/slack/sum-1"
            handler.handle(userId = "", channelId = "C1", messageTs = "1.0", urls = listOf(url))

            verify(exactly = 0) { userEventService.savePassiveShare(any(), any(), any(), any()) }
            rejectedCount() shouldBe 1.0
        }

        @Test
        fun `messageTs 가 비어있으면 저장하지 않는다`() {
            val url = "https://clipping.example.com/api/track/click/slack/sum-1"
            handler.handle(userId = "U1", channelId = "C1", messageTs = "", urls = listOf(url))

            verify(exactly = 0) { userEventService.savePassiveShare(any(), any(), any(), any()) }
        }
    }
}
