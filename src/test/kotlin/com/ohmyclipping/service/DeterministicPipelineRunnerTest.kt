package com.ohmyclipping.service

import com.ohmyclipping.service.pipeline.DeterministicPipelineRunner
import com.ohmyclipping.service.pipeline.toPipelineCollectResult
import com.ohmyclipping.service.pipeline.toPipelineDigestResult
import com.ohmyclipping.service.pipeline.toPipelineSummarizeResult

import com.ohmyclipping.service.dto.clipping.CollectCategoryResult
import com.ohmyclipping.service.dto.clipping.CollectResult
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.service.dto.clipping.PipelineOrchestrationMode
import com.ohmyclipping.service.dto.clipping.PipelineStepStatus
import com.ohmyclipping.service.dto.clipping.SummarizeCategoryResult
import com.ohmyclipping.service.dto.clipping.SummarizeResult
import com.ohmyclipping.service.port.ClippingPipelinePort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test

class DeterministicPipelineRunnerTest {

    @Test
    fun `run should execute collect summarize digest in order and keep trace details`() {
        val pipelinePort = mockk<ClippingPipelinePort>()
        val runner = DeterministicPipelineRunner(pipelinePort)

        every { pipelinePort.collect("cat-1", 12) } returns sampleCollectResult().toPipelineCollectResult()
        every { pipelinePort.summarize("cat-1") } returns sampleSummarizeResult().toPipelineSummarizeResult()
        every { pipelinePort.digest("cat-1", 3, true, false, null) } returns sampleDigestResult().toPipelineDigestResult()

        val result = runner.run(
            categoryId = "cat-1",
            hoursBack = 12,
            maxItems = 3,
            unsentOnly = true,
            sendToSlack = false,
            slackChannelId = null,
            fallbackReason = "ralph failed"
        )

        result.orchestrationMode shouldBe PipelineOrchestrationMode.DETERMINISTIC
        result.fallbackApplied shouldBe true
        result.orchestrationWarnings shouldContainExactly listOf("Ralph fallback applied: ralph failed")
        result.stepTraces.map { it.step } shouldContainExactly listOf("COLLECT", "SUMMARIZE", "DIGEST")
        result.stepTraces.map { it.status }.toSet() shouldBe setOf(PipelineStepStatus.SUCCEEDED)
        result.stepTraces.map { it.detail } shouldContainExactly listOf(
            "newItems=1, duplicateSkipped=0",
            "totalSummarized=1",
            "selectedCount=1, postedToSlack=false"
        )
        verifyOrder {
            pipelinePort.collect("cat-1", 12)
            pipelinePort.summarize("cat-1")
            pipelinePort.digest("cat-1", 3, true, false, null)
        }
    }

    @Test
    fun `run should stop before summarize when collect fails`() {
        val pipelinePort = mockk<ClippingPipelinePort>()
        val runner = DeterministicPipelineRunner(pipelinePort)

        every { pipelinePort.collect("cat-1", 12) } throws IllegalStateException("collect failed")

        val exception = shouldThrow<IllegalStateException> {
            runner.run(
                categoryId = "cat-1",
                hoursBack = 12,
                maxItems = 3,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = null,
                fallbackReason = null
            )
        }

        exception.message shouldBe "collect failed"
        verify(exactly = 1) { pipelinePort.collect("cat-1", 12) }
        verify(exactly = 0) { pipelinePort.summarize(any()) }
        verify(exactly = 0) { pipelinePort.digest(any(), any(), any(), any(), any()) }
    }

    private fun sampleCollectResult(): CollectResult =
        CollectResult(
            totalCollected = 1,
            newItems = 1,
            duplicateSkipped = 0,
            categories = listOf(CollectCategoryResult("cat-1", "카테고리", 1, 1))
        )

    private fun sampleSummarizeResult(): SummarizeResult =
        SummarizeResult(
            totalSummarized = 1,
            categories = listOf(SummarizeCategoryResult("cat-1", "카테고리", 1))
        )

    private fun sampleDigestResult(): DigestResult =
        DigestResult(
            categoryId = "cat-1",
            categoryName = "카테고리",
            unsentOnly = true,
            totalCandidates = 1,
            selectedCount = 1,
            postedToSlack = false,
            slackChannelId = null,
            slackMessageTs = null,
            markedSentCount = 0,
            digestText = "digest",
            items = emptyList()
        )
}
