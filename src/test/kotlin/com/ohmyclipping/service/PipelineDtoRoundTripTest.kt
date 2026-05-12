package com.ohmyclipping.service

import com.ohmyclipping.service.digest.*

import com.ohmyclipping.service.dto.clipping.CollectCategoryResult
import com.ohmyclipping.service.dto.clipping.CollectResult
import com.ohmyclipping.service.dto.clipping.DigestItemResult
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.service.dto.clipping.SummarizeCategoryResult
import com.ohmyclipping.service.dto.clipping.SummarizeResult
import com.ohmyclipping.service.pipeline.toCollectResult
import com.ohmyclipping.service.pipeline.toDigestResult
import com.ohmyclipping.service.pipeline.toPipelineCollectResult
import com.ohmyclipping.service.pipeline.toPipelineDigestResult
import com.ohmyclipping.service.pipeline.toPipelineSummarizeResult
import com.ohmyclipping.service.pipeline.toSummarizeResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PipelineDtoRoundTripTest {

    @Test
    fun `pipeline DTO round trip preserves collect result contract`() {
        val original = CollectResult(
            totalCollected = 7,
            newItems = 5,
            duplicateSkipped = 2,
            categories = listOf(
                CollectCategoryResult(
                    categoryId = "cat-1",
                    categoryName = "AI",
                    collected = 7,
                    newItems = 5,
                )
            ),
        )

        original.toPipelineCollectResult().toCollectResult() shouldBe original
    }

    @Test
    fun `pipeline DTO round trip preserves summarize result contract`() {
        val original = SummarizeResult(
            totalSummarized = 3,
            categories = listOf(
                SummarizeCategoryResult(
                    categoryId = "cat-1",
                    categoryName = "AI",
                    summarized = 3,
                )
            ),
        )

        original.toPipelineSummarizeResult().toSummarizeResult() shouldBe original
    }

    @Test
    fun `pipeline DTO round trip preserves digest result contract`() {
        val original = sampleDigestResult()

        original.toPipelineDigestResult().toDigestResult() shouldBe original
    }

    private fun sampleDigestResult(): DigestResult =
        DigestResult(
            categoryId = "cat-1",
            categoryName = "AI",
            unsentOnly = true,
            totalCandidates = 4,
            selectedCount = 2,
            postedToSlack = true,
            slackChannelId = "C123",
            slackMessageTs = "123.456",
            markedSentCount = 2,
            digestText = "digest text",
            items = listOf(
                DigestItemResult(
                    summaryId = "summary-1",
                    title = "Title",
                    summary = "Summary",
                    keywords = listOf("AI", "Search"),
                    importanceScore = 0.87f,
                    whyImportant = "Important reason",
                    sourceLink = "https://example.com/article",
                    createdAt = "2026-05-02T01:02:03Z",
                    isFallback = true,
                )
            ),
            fallbackUsed = true,
        )
}
