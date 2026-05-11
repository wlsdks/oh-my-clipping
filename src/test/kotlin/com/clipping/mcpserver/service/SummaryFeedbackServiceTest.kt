package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.SummaryFeedback
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.SummaryFeedbackStore
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test

class SummaryFeedbackServiceTest {

    @Test
    fun `recordFromSlackPayload should fail when summaryId does not exist`() {
        val feedbackStore = mockk<SummaryFeedbackStore>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        every { batchSummaryStore.findById("missing-summary") } returns null

        val service = SummaryFeedbackService(
            summaryFeedbackStore = feedbackStore,
            batchSummaryStore = batchSummaryStore,
            adminUserStore = mockk<AdminUserStore>(relaxed = true).also {
                every { it.findBySlackMemberId(any()) } returns null
            },
            objectMapper = ObjectMapper()
        )

        val ex = shouldThrow<InvalidInputException> {
            service.recordFromSlackPayload(
                """
                {
                  "type": "block_actions",
                  "user": { "id": "U_TEST" },
                  "actions": [
                    { "action_id": "feedback_like", "value": "missing-summary" }
                  ]
                }
                """.trimIndent()
            )
        }

        ex.message shouldBe "summaryId not found: missing-summary"
        verify(exactly = 0) { feedbackStore.upsert(any()) }
    }

    @Test
    fun `recordFromSlackPayload should upsert feedback when summaryId exists`() {
        val feedbackStore = mockk<SummaryFeedbackStore>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        every { batchSummaryStore.findById("summary-1") } returns BatchSummary(
            id = "summary-1",
            originalTitle = "title",
            translatedTitle = null,
            summary = "summary",
            keywords = emptyList(),
            importanceScore = 0.7f,
            sourceLink = "https://example.com/item",
            categoryId = "cat-1",
            rssItemId = "rss-1"
        )
        val savedSlot = slot<SummaryFeedback>()
        every { feedbackStore.upsert(capture(savedSlot)) } answers { savedSlot.captured.copy(id = "fb-1") }

        val service = SummaryFeedbackService(
            summaryFeedbackStore = feedbackStore,
            batchSummaryStore = batchSummaryStore,
            adminUserStore = mockk<AdminUserStore>(relaxed = true).also {
                every { it.findBySlackMemberId(any()) } returns null
            },
            objectMapper = ObjectMapper()
        )

        val (saved, message) = service.recordFromSlackPayload(
            """
            {
              "type": "block_actions",
              "user": { "id": "U_TEST" },
              "actions": [
                { "action_id": "feedback_like", "value": "summary-1" }
              ]
            }
            """.trimIndent()
        )

        saved.id shouldBe "fb-1"
        saved.summaryId shouldBe "summary-1"
        saved.feedbackType shouldBe "LIKE"
        saved.userId shouldBe "U_TEST"
        message shouldBe "좋아요로 반영되었습니다."
        verify(exactly = 1) { feedbackStore.upsert(any()) }
    }

    @Test
    fun `recordFromSlackPayload should fail on malformed JSON`() {
        val feedbackStore = mockk<SummaryFeedbackStore>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        val service = SummaryFeedbackService(
            summaryFeedbackStore = feedbackStore,
            batchSummaryStore = batchSummaryStore,
            adminUserStore = mockk<AdminUserStore>(relaxed = true).also {
                every { it.findBySlackMemberId(any()) } returns null
            },
            objectMapper = ObjectMapper()
        )

        val ex = shouldThrow<InvalidInputException> {
            service.recordFromSlackPayload("not valid json {{{")
        }
        ex.message shouldBe "Invalid payload format"
    }

    @Test
    fun `recordFromSlackPayload should fail when actions array is missing`() {
        val feedbackStore = mockk<SummaryFeedbackStore>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        val service = SummaryFeedbackService(
            summaryFeedbackStore = feedbackStore,
            batchSummaryStore = batchSummaryStore,
            adminUserStore = mockk<AdminUserStore>(relaxed = true).also {
                every { it.findBySlackMemberId(any()) } returns null
            },
            objectMapper = ObjectMapper()
        )

        val ex = shouldThrow<InvalidInputException> {
            service.recordFromSlackPayload("""{"type":"block_actions","user":{"id":"U1"}}""")
        }
        ex.message shouldBe "No action found in payload"
    }

    @Test
    fun `recordFromSlackPayload should fail on unsupported actionId`() {
        val feedbackStore = mockk<SummaryFeedbackStore>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        every { batchSummaryStore.findById("summary-1") } returns BatchSummary(
            id = "summary-1", originalTitle = "t", translatedTitle = null,
            summary = "s", keywords = emptyList(), importanceScore = 0.5f,
            sourceLink = "https://example.com", categoryId = "c-1", rssItemId = "r-1"
        )
        val service = SummaryFeedbackService(
            summaryFeedbackStore = feedbackStore,
            batchSummaryStore = batchSummaryStore,
            adminUserStore = mockk<AdminUserStore>(relaxed = true).also {
                every { it.findBySlackMemberId(any()) } returns null
            },
            objectMapper = ObjectMapper()
        )

        val ex = shouldThrow<InvalidInputException> {
            service.recordFromSlackPayload(
                """
                {
                  "type": "block_actions",
                  "user": { "id": "U_TEST" },
                  "actions": [
                    { "action_id": "unknown_action", "value": "summary-1" }
                  ]
                }
                """.trimIndent()
            )
        }
        ex.message shouldBe "Unsupported actionId: unknown_action"
    }

    @Test
    fun `recordFromSlackPayload should default userId to anonymous when missing`() {
        val feedbackStore = mockk<SummaryFeedbackStore>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        every { batchSummaryStore.findById("s-1") } returns BatchSummary(
            id = "s-1", originalTitle = "t", translatedTitle = null,
            summary = "s", keywords = emptyList(), importanceScore = 0.5f,
            sourceLink = "https://example.com", categoryId = "c-1", rssItemId = "r-1"
        )
        val savedSlot = slot<SummaryFeedback>()
        every { feedbackStore.upsert(capture(savedSlot)) } answers { savedSlot.captured.copy(id = "fb-2") }

        val service = SummaryFeedbackService(
            summaryFeedbackStore = feedbackStore,
            batchSummaryStore = batchSummaryStore,
            adminUserStore = mockk<AdminUserStore>(relaxed = true).also {
                every { it.findBySlackMemberId(any()) } returns null
            },
            objectMapper = ObjectMapper()
        )

        service.recordFromSlackPayload(
            """{"type":"block_actions","actions":[{"action_id":"feedback_like","value":"s-1"}]}"""
        )

        savedSlot.captured.userId shouldBe "anonymous"
    }

    @Test
    fun `recordFromSlackPayload should handle dislike action`() {
        val feedbackStore = mockk<SummaryFeedbackStore>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        every { batchSummaryStore.findById("s-1") } returns BatchSummary(
            id = "s-1", originalTitle = "t", translatedTitle = null,
            summary = "s", keywords = emptyList(), importanceScore = 0.5f,
            sourceLink = "https://example.com", categoryId = "c-1", rssItemId = "r-1"
        )
        val savedSlot = slot<SummaryFeedback>()
        every { feedbackStore.upsert(capture(savedSlot)) } answers { savedSlot.captured.copy(id = "fb-3") }

        val service = SummaryFeedbackService(
            summaryFeedbackStore = feedbackStore,
            batchSummaryStore = batchSummaryStore,
            adminUserStore = mockk<AdminUserStore>(relaxed = true).also {
                every { it.findBySlackMemberId(any()) } returns null
            },
            objectMapper = ObjectMapper()
        )

        val (saved, message) = service.recordFromSlackPayload(
            """
            {
              "type": "block_actions",
              "user": { "id": "U_TEST" },
              "actions": [
                { "action_id": "feedback_dislike", "value": "s-1" }
              ]
            }
            """.trimIndent()
        )

        saved.feedbackType shouldBe "DISLIKE"
        message shouldBe "별로로 반영되었습니다."
    }
}
