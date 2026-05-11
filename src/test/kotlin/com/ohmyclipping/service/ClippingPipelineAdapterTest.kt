package com.ohmyclipping.service

import com.ohmyclipping.service.pipeline.ClippingPipelineAdapter

import com.ohmyclipping.service.dto.clipping.CollectCategoryResult
import com.ohmyclipping.service.dto.clipping.CollectResult
import com.ohmyclipping.service.dto.clipping.DigestItemResult
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.service.dto.clipping.SummarizeCategoryResult
import com.ohmyclipping.service.dto.clipping.SummarizeResult
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ClippingPipelineAdapterTest {

    @Test
    fun `adapter should map clipping app results to independent pipeline DTOs`() {
        val clippingService = mockk<ClippingService>()
        val adapter = ClippingPipelineAdapter(clippingService)

        every { clippingService.collect("cat-1", 24) } returns CollectResult(
            totalCollected = 3,
            newItems = 2,
            duplicateSkipped = 1,
            categories = listOf(CollectCategoryResult("cat-1", "AI", 3, 2))
        )
        every { clippingService.summarize("cat-1") } returns SummarizeResult(
            totalSummarized = 2,
            categories = listOf(SummarizeCategoryResult("cat-1", "AI", 2))
        )
        every {
            clippingService.digest(
                categoryId = "cat-1",
                maxItems = 5,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = "C123"
            )
        } returns DigestResult(
            categoryId = "cat-1",
            categoryName = "AI",
            unsentOnly = true,
            totalCandidates = 2,
            selectedCount = 1,
            postedToSlack = false,
            slackChannelId = "C123",
            slackMessageTs = null,
            markedSentCount = 0,
            digestText = "digest",
            items = listOf(
                DigestItemResult(
                    summaryId = "sum-1",
                    title = "Title",
                    summary = "Summary",
                    keywords = listOf("ai", "batch"),
                    importanceScore = 0.9f,
                    whyImportant = "important",
                    sourceLink = "https://example.com/a",
                    createdAt = "2026-05-02T00:00:00Z",
                    isFallback = true
                )
            ),
            fallbackUsed = true
        )

        val collect = adapter.collect("cat-1", 24)
        val summarize = adapter.summarize("cat-1")
        val digest = adapter.digest("cat-1", 5, true, false, "C123")

        collect.totalCollected shouldBe 3
        collect.newItems shouldBe 2
        collect.duplicateSkipped shouldBe 1
        collect.categories.single().categoryName shouldBe "AI"
        summarize.totalSummarized shouldBe 2
        digest.categoryId shouldBe "cat-1"
        digest.slackChannelId shouldBe "C123"
        digest.fallbackUsed shouldBe true
        digest.items.map { it.keywords }.single() shouldContainExactly listOf("ai", "batch")
        digest.items.single().isFallback shouldBe true
    }
}
