package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingFeatureFlags
import com.ohmyclipping.config.SlackProperties
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SlackSocketModeServiceTest {

    private val slackProperties = SlackProperties(
        botToken = "xoxb-test",
        appLevelToken = "xapp-test",
        socketModeEnabled = false
    )
    private val summaryFeedbackService = mockk<SummaryFeedbackService>()
    private val slackMessageSender = mockk<SlackMessageSender>()
    private val categoryStore = mockk<CategoryStore>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val linkSharedHandler = mockk<SlackLinkSharedHandler>(relaxed = true)

    private val service = SlackSocketModeService(
        slackProperties = slackProperties,
        summaryFeedbackService = summaryFeedbackService,
        slackMessageSender = slackMessageSender,
        categoryStore = categoryStore,
        batchSummaryStore = batchSummaryStore,
        featureFlags = ClippingFeatureFlags(),
        linkSharedHandler = linkSharedHandler
    )

    private val mapper = jacksonObjectMapper().apply { findAndRegisterModules() }

    @Nested
    inner class `feedbackConfirmationText 테스트` {

        @Test
        fun `LIKE 피드백은 좋아요 확인 텍스트를 반환한다`() {
            service.feedbackConfirmationText("LIKE") shouldBe "\uD83D\uDC4D 좋아요로 반영했어요"
        }

        @Test
        fun `NEUTRAL 피드백은 보통 확인 텍스트를 반환한다`() {
            service.feedbackConfirmationText("NEUTRAL") shouldBe "\uD83D\uDE10 보통으로 반영했어요"
        }

        @Test
        fun `DISLIKE 피드백은 별로 확인 텍스트를 반환한다`() {
            service.feedbackConfirmationText("DISLIKE") shouldBe "\uD83D\uDC4E 별로로 반영했어요"
        }

        @Test
        fun `알 수 없는 피드백 타입은 일반 확인 텍스트를 반환한다`() {
            service.feedbackConfirmationText("UNKNOWN") shouldBe "\u2705 피드백이 반영되었어요"
        }
    }

    @Nested
    inner class `replaceFeedbackBlocks 테스트` {

        @Test
        fun `피드백 actions 블록을 확인 section으로 교체한다`() {
            val blocks = mapper.readTree(
                """
                [
                    {"type": "section", "text": {"type": "mrkdwn", "text": "뉴스 제목"}},
                    {
                        "type": "actions",
                        "block_id": "feedback_summary-123",
                        "elements": [
                            {"type": "button", "action_id": "feedback_like:summary-123", "text": {"type": "plain_text", "text": "좋아요"}},
                            {"type": "button", "action_id": "feedback_neutral:summary-123", "text": {"type": "plain_text", "text": "보통"}},
                            {"type": "button", "action_id": "feedback_dislike:summary-123", "text": {"type": "plain_text", "text": "별로"}}
                        ]
                    },
                    {"type": "divider"}
                ]
                """.trimIndent()
            )

            val result = service.replaceFeedbackBlocks(blocks, "\uD83D\uDC4D 좋아요로 반영했어요", "feedback_summary-123")

            result.size shouldBe 3

            // 첫 번째 블록은 원본 유지
            @Suppress("UNCHECKED_CAST")
            val firstBlock = result[0] as Map<String, Any?>
            firstBlock["type"] shouldBe "section"

            // 두 번째 블록은 확인 메시지로 교체됨
            @Suppress("UNCHECKED_CAST")
            val replacedBlock = result[1] as Map<String, Any?>
            replacedBlock["type"] shouldBe "section"
            @Suppress("UNCHECKED_CAST")
            val textObj = replacedBlock["text"] as Map<String, Any?>
            textObj["type"] shouldBe "mrkdwn"
            (textObj["text"] as String) shouldContain "좋아요로 반영했어요"

            // 세 번째 블록은 원본 유지
            @Suppress("UNCHECKED_CAST")
            val thirdBlock = result[2] as Map<String, Any?>
            thirdBlock["type"] shouldBe "divider"
        }

        @Test
        fun `피드백 블록이 없으면 모든 블록을 그대로 유지한다`() {
            val blocks = mapper.readTree(
                """
                [
                    {"type": "section", "text": {"type": "mrkdwn", "text": "뉴스 제목"}},
                    {"type": "actions", "elements": [{"type": "button", "action_id": "open_source_123"}]},
                    {"type": "divider"}
                ]
                """.trimIndent()
            )

            val result = service.replaceFeedbackBlocks(blocks, "\uD83D\uDC4D 좋아요로 반영했어요", "feedback_summary-123")

            result.size shouldBe 3
            // actions 블록은 feedback_ 패턴이 아니므로 교체되지 않음
            @Suppress("UNCHECKED_CAST")
            val actionsBlock = result[1] as Map<String, Any?>
            actionsBlock["type"] shouldBe "actions"
        }

        @Test
        fun `타겟 block_id와 일치하는 피드백 블록만 교체하고 다른 기사 피드백은 유지한다`() {
            val blocks = mapper.readTree(
                """
                [
                    {"type": "section", "text": {"type": "mrkdwn", "text": "아이템 1"}},
                    {"type": "actions", "block_id": "feedback_s1", "elements": [{"type": "button", "action_id": "feedback_like:s1"}]},
                    {"type": "divider"},
                    {"type": "section", "text": {"type": "mrkdwn", "text": "아이템 2"}},
                    {"type": "actions", "block_id": "feedback_s2", "elements": [{"type": "button", "action_id": "feedback_like:s2"}]}
                ]
                """.trimIndent()
            )

            val confirmText = "\uD83D\uDC4D 좋아요로 반영했어요"
            val result = service.replaceFeedbackBlocks(blocks, confirmText, "feedback_s1")

            result.size shouldBe 5
            // s1 블록만 section으로 교체, s2는 actions 그대로
            @Suppress("UNCHECKED_CAST")
            val second = result[1] as Map<String, Any?>
            second["type"] shouldBe "section"
            @Suppress("UNCHECKED_CAST")
            val fifth = result[4] as Map<String, Any?>
            fifth["type"] shouldBe "actions"
            fifth["block_id"] shouldBe "feedback_s2"
        }

        @Test
        fun `타겟 block_id가 null이면 모든 피드백 블록을 교체한다 (backwards compatible)`() {
            val blocks = mapper.readTree(
                """
                [
                    {"type": "actions", "block_id": "feedback_s1", "elements": [{"type": "button", "action_id": "feedback_like:s1"}]},
                    {"type": "actions", "block_id": "feedback_s2", "elements": [{"type": "button", "action_id": "feedback_like:s2"}]}
                ]
                """.trimIndent()
            )

            val result = service.replaceFeedbackBlocks(blocks, "\uD83D\uDC4D 좋아요로 반영했어요", null)

            result.size shouldBe 2
            @Suppress("UNCHECKED_CAST")
            val first = result[0] as Map<String, Any?>
            first["type"] shouldBe "section"
            @Suppress("UNCHECKED_CAST")
            val second = result[1] as Map<String, Any?>
            second["type"] shouldBe "section"
        }
    }

    @Nested
    inner class `replaceShareBlocks 테스트` {

        @Test
        fun `share_ block_id를 가진 actions 블록을 context 블록으로 교체한다`() {
            val blocks = mapper.readTree(
                """
                [
                    {"type": "section", "text": {"type": "mrkdwn", "text": "뉴스 제목"}},
                    {
                        "type": "actions",
                        "block_id": "feedback_summary-123",
                        "elements": [
                            {"type": "button", "action_id": "feedback_like:summary-123"}
                        ]
                    },
                    {
                        "type": "actions",
                        "block_id": "share_summary-123",
                        "elements": [
                            {"type": "button", "action_id": "share_to_channel:summary-123", "value": "summary-123:cat-1"}
                        ]
                    },
                    {"type": "divider"}
                ]
                """.trimIndent()
            )

            val result = service.replaceShareBlocks(blocks, "✅ #general 에 공유했어요")

            result.size shouldBe 4

            // 피드백 블록은 그대로 유지
            @Suppress("UNCHECKED_CAST")
            val feedbackBlock = result[1] as Map<String, Any?>
            feedbackBlock["type"] shouldBe "actions"

            // 공유 블록은 context로 교체됨
            @Suppress("UNCHECKED_CAST")
            val shareBlock = result[2] as Map<String, Any?>
            shareBlock["type"] shouldBe "context"
            @Suppress("UNCHECKED_CAST")
            val elements = shareBlock["elements"] as List<Map<String, Any?>>
            (elements[0]["text"] as String) shouldContain "공유했어요"
        }

        @Test
        fun `share_to_channel action_id로도 공유 블록을 식별한다`() {
            val blocks = mapper.readTree(
                """
                [
                    {
                        "type": "actions",
                        "elements": [
                            {"type": "button", "action_id": "share_to_channel:s1", "value": "s1:cat-1"}
                        ]
                    }
                ]
                """.trimIndent()
            )

            val result = service.replaceShareBlocks(blocks, "⚠️ 채널이 설정되지 않았어요")

            result.size shouldBe 1
            @Suppress("UNCHECKED_CAST")
            val replaced = result[0] as Map<String, Any?>
            replaced["type"] shouldBe "context"
        }

        @Test
        fun `공유 블록이 없으면 모든 블록을 그대로 유지한다`() {
            val blocks = mapper.readTree(
                """
                [
                    {"type": "section", "text": {"type": "mrkdwn", "text": "뉴스 제목"}},
                    {"type": "actions", "block_id": "feedback_s1", "elements": [{"type": "button", "action_id": "feedback_like:s1"}]},
                    {"type": "divider"}
                ]
                """.trimIndent()
            )

            val result = service.replaceShareBlocks(blocks, "✅ 공유됨")

            result.size shouldBe 3
            // 피드백 블록은 교체 안 됨
            @Suppress("UNCHECKED_CAST")
            val actionsBlock = result[1] as Map<String, Any?>
            actionsBlock["type"] shouldBe "actions"
        }
    }

    @Nested
    inner class `tryDispatchEventsApi - link_shared` {

        @Test
        fun `link_shared event 를 SlackLinkSharedHandler 로 전달한다`() {
            val envelope = mapper.readTree(
                """
                {
                    "envelope_id": "env-1",
                    "type": "events_api",
                    "payload": {
                        "type": "event_callback",
                        "event": {
                            "type": "link_shared",
                            "user": "U123",
                            "channel": "C456",
                            "message_ts": "1713.42",
                            "links": [
                                {"url": "https://clipping.example.com/api/track/click/slack/sum-1", "domain": "clipping.example.com"},
                                {"url": "https://news.example.com/article/abc", "domain": "news.example.com"}
                            ]
                        }
                    }
                }
                """.trimIndent()
            )

            val handled = service.tryDispatchEventsApi(envelope)

            handled shouldBe true
            verify(exactly = 1) {
                linkSharedHandler.handle(
                    userId = "U123",
                    channelId = "C456",
                    messageTs = "1713.42",
                    urls = listOf(
                        "https://clipping.example.com/api/track/click/slack/sum-1",
                        "https://news.example.com/article/abc"
                    )
                )
            }
        }

        @Test
        fun `interactive payload 는 처리하지 않는다`() {
            val envelope = mapper.readTree(
                """
                {
                    "envelope_id": "env-2",
                    "type": "interactive",
                    "payload": {
                        "type": "block_actions",
                        "user": {"id": "U1"},
                        "actions": [{"action_id": "feedback_like:s1"}]
                    }
                }
                """.trimIndent()
            )

            val handled = service.tryDispatchEventsApi(envelope)

            handled shouldBe false
            verify(exactly = 0) { linkSharedHandler.handle(any(), any(), any(), any()) }
        }

        @Test
        fun `event_callback 이지만 다른 event type 은 처리하지 않는다`() {
            val envelope = mapper.readTree(
                """
                {
                    "envelope_id": "env-3",
                    "type": "events_api",
                    "payload": {
                        "type": "event_callback",
                        "event": {"type": "message", "text": "hi"}
                    }
                }
                """.trimIndent()
            )

            val handled = service.tryDispatchEventsApi(envelope)

            handled shouldBe false
            verify(exactly = 0) { linkSharedHandler.handle(any(), any(), any(), any()) }
        }

        @Test
        fun `payload 가 없는 envelope 는 false 를 반환한다`() {
            val envelope = mapper.readTree(
                """
                {"envelope_id": "env-4"}
                """.trimIndent()
            )

            val handled = service.tryDispatchEventsApi(envelope)

            handled shouldBe false
            verify(exactly = 0) { linkSharedHandler.handle(any(), any(), any(), any()) }
        }

        @Test
        fun `links 배열이 비어있어도 dispatch 는 이뤄지고 빈 URL 목록이 전달된다`() {
            val envelope = mapper.readTree(
                """
                {
                    "envelope_id": "env-5",
                    "payload": {
                        "type": "event_callback",
                        "event": {
                            "type": "link_shared",
                            "user": "U1",
                            "channel": "C1",
                            "message_ts": "1.0",
                            "links": []
                        }
                    }
                }
                """.trimIndent()
            )

            val handled = service.tryDispatchEventsApi(envelope)

            handled shouldBe true
            verify(exactly = 1) {
                linkSharedHandler.handle(
                    userId = "U1",
                    channelId = "C1",
                    messageTs = "1.0",
                    urls = emptyList()
                )
            }
        }
    }
}
