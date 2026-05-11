package com.ohmyclipping.service

import com.ohmyclipping.service.SlackMetadata
import com.ohmyclipping.service.SlackMessageSender.SendResult
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

/**
 * SlackMessageSender 인터페이스 계약 및 SendResult 필드 구조를 검증한다.
 * 구현체 동작은 SlackApiMessageSenderTest에서 별도로 검증한다.
 */
class SlackMessageSenderTest {

    private val sender = mockk<SlackMessageSender>()

    @Nested
    inner class `SendResult 필드 계약` {

        @Test
        fun `SendResult ts + payloadJson + isNew true를 올바르게 담는다`() {
            every {
                sender.sendMessage(any(), any(), any(), any(), any(), any(), any())
            } returns SendResult(
                ts = "1713.12",
                channelId = "C01",
                ok = true,
                payloadJson = """{"blocks":["section"]}""",
                isNew = true
            )

            val result = sender.sendMessage(
                channelId = "C01",
                text = "fallback",
                blocks = listOf(mapOf("type" to "section"))
            )

            result.ts shouldBe "1713.12"
            result.channelId shouldBe "C01"
            result.ok.shouldBeTrue()
            result.payloadJson.shouldContain("\"blocks\"")
            result.isNew.shouldBeTrue()
        }

        @Test
        fun `SendResult 기본값 — ok=true, payloadJson 빈 문자열, isNew=true`() {
            val result = SendResult(ts = "1000.0", channelId = "C01")
            result.ok.shouldBeTrue()
            result.payloadJson shouldBe ""
            result.isNew.shouldBeTrue()
        }

        @Test
        fun `updateMessage가 반환한 SendResult는 isNew=false`() {
            every {
                sender.updateMessage(any(), any(), any(), any(), any())
            } returns SendResult(ts = "1000.0", channelId = "C01", ok = true, isNew = false)

            val result = sender.updateMessage(
                channelId = "C01",
                messageTs = "1000.0",
                blocks = listOf(mapOf("type" to "section")),
                fallbackText = "updated"
            )

            result.isNew.shouldBeFalse()
        }
    }

    @Nested
    inner class `threadTs 파라미터 전달` {

        @Test
        fun `threadTs 전달 시 API 요청에 thread_ts 포함`() {
            var capturedThreadTs: String? = "NOT_SET"
            every {
                sender.sendMessage(any(), any(), any(), any(), any(), any(), any())
            } answers {
                capturedThreadTs = args[3] as? String
                SendResult(ts = "1000.1", channelId = "C01")
            }

            sender.sendMessage(
                channelId = "C01",
                text = "t",
                blocks = emptyList(),
                threadTs = "1000.1"
            )

            capturedThreadTs shouldBe "1000.1"
        }

        @Test
        fun `threadTs 없이 호출 시 null로 전달`() {
            var capturedThreadTs: String? = "NOT_SET"
            every {
                sender.sendMessage(any(), any(), any(), any(), any(), any(), any())
            } answers {
                capturedThreadTs = args[3] as? String
                SendResult(ts = "1000.0", channelId = "C01")
            }

            sender.sendMessage("C01", "text")

            capturedThreadTs shouldBe null
        }
    }

    @Nested
    inner class `replyBroadcast 파라미터 전달` {

        @Test
        fun `replyBroadcast=true 전달 시 true로 캡처`() {
            val capturedBroadcast = slot<Boolean>()
            every {
                sender.sendMessage(any(), any(), any(), any(), capture(capturedBroadcast), any(), any())
            } returns SendResult(ts = "1000.1", channelId = "C01")

            sender.sendMessage(
                channelId = "C01",
                text = "t",
                threadTs = "1000.1",
                replyBroadcast = true
            )

            capturedBroadcast.captured.shouldBeTrue()
        }

        @Test
        fun `replyBroadcast 기본값은 false`() {
            val capturedBroadcast = slot<Boolean>()
            every {
                sender.sendMessage(any(), any(), any(), any(), capture(capturedBroadcast), any(), any())
            } returns SendResult(ts = "1000.0", channelId = "C01")

            sender.sendMessage("C01", "text")

            capturedBroadcast.captured.shouldBeFalse()
        }
    }

    @Nested
    inner class `metadata 파라미터 전달` {

        @Test
        fun `metadata 전달 시 API 요청에 event_type과 event_payload 포함`() {
            var capturedMetadata: SlackMetadata? = null
            every {
                sender.sendMessage(any(), any(), any(), any(), any(), any(), any())
            } answers {
                capturedMetadata = args[5] as? SlackMetadata
                SendResult(ts = "1000.0", channelId = "C01")
            }

            val metadata = SlackMetadata("pipeline_failed", mapOf("run_id" to "abc"))
            sender.sendMessage(
                channelId = "C01",
                text = "t",
                metadata = metadata
            )

            val captured = capturedMetadata!!
            captured.eventType shouldBe "pipeline_failed"
            captured.eventPayload["run_id"] shouldBe "abc"

            val payload = captured.toApiPayload()
            (payload["event_type"] as String) shouldBe "pipeline_failed"
            @Suppress("UNCHECKED_CAST")
            ((payload["event_payload"] as Map<String, String>)["run_id"]) shouldBe "abc"
        }

        @Test
        fun `metadata 없이 호출 시 null로 전달`() {
            var capturedMetadata: SlackMetadata? = SlackMetadata("sentinel", emptyMap())
            every {
                sender.sendMessage(any(), any(), any(), any(), any(), any(), any())
            } answers {
                capturedMetadata = args[5] as? SlackMetadata
                SendResult(ts = "1000.0", channelId = "C01")
            }

            sender.sendMessage("C01", "text")

            capturedMetadata shouldBe null
        }
    }

    @Nested
    inner class `updateMessage blocks List 파라미터` {

        @Test
        fun `updateMessage는 blocks List 타입을 받고 isNew=false를 반환한다`() {
            val capturedBlocks = slot<List<Map<String, Any?>>>()
            every {
                sender.updateMessage(any(), any(), capture(capturedBlocks), any(), any())
            } returns SendResult(ts = "1000.0", channelId = "C01", ok = true, isNew = false)

            val blocks = listOf(mapOf<String, Any?>("type" to "section", "text" to mapOf("type" to "mrkdwn", "text" to "hello")))
            val result = sender.updateMessage(
                channelId = "C01",
                messageTs = "1000.0",
                blocks = blocks,
                fallbackText = "hello"
            )

            capturedBlocks.captured.size shouldBe 1
            capturedBlocks.captured[0]["type"] shouldBe "section"
            result.isNew.shouldBeFalse()
        }
    }

    @Nested
    inner class `backwards compat — 기존 호출자 시그니처` {

        @Test
        fun `botToken만 사용하는 기존 호출 방식도 컴파일된다`() {
            every { sender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                SendResult(ts = "t", channelId = "C01")

            // 기존 4-param 호출 방식 (botToken이 마지막이었던 시절 스타일 — 이제 named param 필요)
            sender.sendMessage(channelId = "C01", text = "hello", blocks = emptyList(), botToken = "xoxb-token")

            verify(exactly = 1) { sender.sendMessage(any(), any(), any(), any(), any(), any(), any(), "xoxb-token") }
        }
    }
}
