package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.pipeline.RalphPipelineOrchestrator
import com.clipping.mcpserver.service.pipeline.toPipelineCollectResult
import com.clipping.mcpserver.service.pipeline.toPipelineDigestResult
import com.clipping.mcpserver.service.pipeline.toPipelineSummarizeResult

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.service.dto.clipping.CollectCategoryResult
import com.clipping.mcpserver.service.dto.clipping.CollectResult
import com.clipping.mcpserver.service.dto.clipping.DigestResult
import com.clipping.mcpserver.service.dto.clipping.PipelineOrchestrationMode
import com.clipping.mcpserver.service.dto.clipping.RalphLoopStopReason
import com.clipping.mcpserver.service.dto.clipping.PipelineStepStatus
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.ReviewItemDecision
import com.clipping.mcpserver.service.dto.clipping.SummarizeCategoryResult
import com.clipping.mcpserver.service.dto.clipping.SummarizeResult
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.service.port.ClippingPipelinePort
import com.clipping.mcpserver.service.port.PipelineCollectResult
import com.clipping.mcpserver.service.port.PipelineDigestResult
import com.clipping.mcpserver.service.port.PipelineSummarizeResult
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class RalphPipelineOrchestratorTest {

    @Test
    fun `runPipeline should move low-importance summary to review and keep step traces`() {
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()
        val adminReviewQueueService = mockk<AdminReviewQueueService>()
        val metrics = mockk<ClippingMetrics>(relaxed = true)

        every { runtimeSettingService.current() } returns sampleRuntimeSettings(minImportance = 0.6f)
        every { clippingPipelinePort.collect("cat-1", 24) } returns sampleCollectResult()
        every { clippingPipelinePort.summarize("cat-1") } returns sampleSummarizeResult()
        every {
            clippingPipelinePort.digest(
                categoryId = "cat-1",
                maxItems = 5,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = null
            )
        } returns sampleDigestResult()
        every { batchSummaryStore.findUnsent("cat-1") } returns listOf(
            summary(id = "high", title = "High Item", score = 0.92f),
            summary(id = "low", title = "Low Item", score = 0.12f)
        )
        every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
        every {
            adminReviewQueueService.markReview(
                summaryId = "low",
                reason = match { it.contains("중요도") },
                reviewedBy = "ralph-critic"
            )
        } returns ReviewItemDecision(
            summaryId = "low",
            categoryId = "cat-1",
            status = ReviewDecisionStatus.REVIEW,
            reason = "ralph",
            reviewedBy = "ralph-critic",
            reviewedAt = Instant.now()
        )

        val orchestrator = RalphPipelineOrchestrator(
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            batchSummaryStore = batchSummaryStore,
            reviewItemDecisionStore = reviewItemDecisionStore,
            adminReviewQueueService = adminReviewQueueService,
            metrics = metrics
        )

        val result = orchestrator.runPipeline(
            categoryId = "cat-1",
            hoursBack = null,
            maxItems = null,
            unsentOnly = null,
            sendToSlack = null,
            slackChannelId = null
        )

        result.orchestrationMode shouldBe PipelineOrchestrationMode.RALPH
        result.stepTraces.map { it.step } shouldBe listOf(
            "ITERATION_1_PLAN",
            "ITERATION_1_COLLECT",
            "ITERATION_1_SUMMARIZE",
            "ITERATION_1_CRITIC_REVIEW",
            "ITERATION_1_DIGEST"
        )
        result.stepTraces.map { it.status }.toSet() shouldContain PipelineStepStatus.SUCCEEDED

        verify(exactly = 1) {
            adminReviewQueueService.markReview(
                summaryId = "low",
                reason = match { it.contains("중요도") },
                reviewedBy = "ralph-critic"
            )
        }
    }

    @Test
    fun `runPipeline should loop until max iterations when loop is enabled`() {
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()
        val adminReviewQueueService = mockk<AdminReviewQueueService>()
        val metrics = mockk<ClippingMetrics>(relaxed = true)

        every {
            runtimeSettingService.current()
        } returns sampleRuntimeSettings(
            minImportance = 0.1f,
            loopEnabled = true,
            loopMaxIterations = 2
        )
        every { clippingPipelinePort.collect(any(), any()) } returns sampleCollectResult()
        every { clippingPipelinePort.summarize(any()) } returns sampleSummarizeResult()
        every { clippingPipelinePort.digest(any(), any(), any(), any(), any()) } returns sampleDigestResult()
        every { batchSummaryStore.findUnsent("cat-1") } returns emptyList()
        every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()

        val orchestrator = RalphPipelineOrchestrator(
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            batchSummaryStore = batchSummaryStore,
            reviewItemDecisionStore = reviewItemDecisionStore,
            adminReviewQueueService = adminReviewQueueService,
            metrics = metrics
        )

        val result = orchestrator.runPipeline(
            categoryId = "cat-1",
            hoursBack = 12,
            maxItems = 3,
            unsentOnly = true,
            sendToSlack = false,
            slackChannelId = null
        )

        result.loopEnabled shouldBe true
        result.loopIterationCount shouldBe 2
        result.loopStopReason shouldBe RalphLoopStopReason.MAX_ITERATIONS_REACHED
        verify(exactly = 2) { clippingPipelinePort.collect("cat-1", 12) }
    }

    @Test
    fun `runPipeline should stop early when stop phrase is detected`() {
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()
        val adminReviewQueueService = mockk<AdminReviewQueueService>()
        val metrics = mockk<ClippingMetrics>(relaxed = true)

        every {
            runtimeSettingService.current()
        } returns sampleRuntimeSettings(
            minImportance = 0.1f,
            loopEnabled = true,
            loopMaxIterations = 5,
            stopPhrase = "DONE_SIGNAL"
        )
        every { clippingPipelinePort.collect(any(), any()) } returns sampleCollectResult()
        every { clippingPipelinePort.summarize(any()) } returns sampleSummarizeResult()
        every { clippingPipelinePort.digest(any(), any(), any(), any(), any()) } returns sampleDigestResult().copy(
            digestText = "iteration result DONE_SIGNAL"
        )
        every { batchSummaryStore.findUnsent("cat-1") } returns emptyList()
        every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()

        val orchestrator = RalphPipelineOrchestrator(
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            batchSummaryStore = batchSummaryStore,
            reviewItemDecisionStore = reviewItemDecisionStore,
            adminReviewQueueService = adminReviewQueueService,
            metrics = metrics
        )

        val result = orchestrator.runPipeline(
            categoryId = "cat-1",
            hoursBack = null,
            maxItems = null,
            unsentOnly = null,
            sendToSlack = null,
            slackChannelId = null
        )

        result.loopEnabled shouldBe true
        result.loopIterationCount shouldBe 1
        result.loopStopReason shouldBe RalphLoopStopReason.STOP_PHRASE_DETECTED
        result.loopStopPhrase shouldBe "DONE_SIGNAL"
        verify(exactly = 1) { clippingPipelinePort.collect("cat-1", 24) }
        verify(exactly = 1) {
            metrics.recordRalphLoopResult(
                stopReason = RalphLoopStopReason.STOP_PHRASE_DETECTED.name,
                iterationCount = 1
            )
        }
    }

    @Test
    fun `runPipeline should stop with no progress after 2 consecutive iterations`() {
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()
        val adminReviewQueueService = mockk<AdminReviewQueueService>()
        val metrics = mockk<ClippingMetrics>(relaxed = true)

        every {
            runtimeSettingService.current()
        } returns sampleRuntimeSettings(
            minImportance = 0.1f,
            loopEnabled = true,
            loopMaxIterations = 5
        )
        every { clippingPipelinePort.collect(any(), any()) } returns CollectResult(
            totalCollected = 0,
            newItems = 0,
            duplicateSkipped = 0,
            categories = listOf(CollectCategoryResult("cat-1", "카테고리", 0, 0))
        ).toPipelineCollectResult()
        every { clippingPipelinePort.summarize(any()) } returns SummarizeResult(
            totalSummarized = 0,
            categories = listOf(SummarizeCategoryResult("cat-1", "카테고리", 0))
        ).toPipelineSummarizeResult()
        every { clippingPipelinePort.digest(any(), any(), any(), any(), any()) } returns sampleDigestResult()
        every { batchSummaryStore.findUnsent("cat-1") } returns emptyList()
        every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()

        val orchestrator = RalphPipelineOrchestrator(
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            batchSummaryStore = batchSummaryStore,
            reviewItemDecisionStore = reviewItemDecisionStore,
            adminReviewQueueService = adminReviewQueueService,
            metrics = metrics
        )

        val result = orchestrator.runPipeline(
            categoryId = "cat-1",
            hoursBack = null,
            maxItems = null,
            unsentOnly = null,
            sendToSlack = null,
            slackChannelId = null
        )

        result.loopEnabled shouldBe true
        result.loopIterationCount shouldBe 2
        result.loopStopReason shouldBe RalphLoopStopReason.NO_PROGRESS
        result.orchestrationWarnings.any { it.contains("no progress") } shouldBe true
        verify(exactly = 2) { clippingPipelinePort.collect("cat-1", 24) }
        verify(exactly = 1) {
            metrics.recordRalphLoopResult(
                stopReason = RalphLoopStopReason.NO_PROGRESS.name,
                iterationCount = 2
            )
        }
    }

    @Test
    fun `runPipeline should honor loop overrides even when runtime loop is disabled`() {
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()
        val adminReviewQueueService = mockk<AdminReviewQueueService>()
        val metrics = mockk<ClippingMetrics>(relaxed = true)

        every {
            runtimeSettingService.current()
        } returns sampleRuntimeSettings(
            minImportance = 0.1f,
            loopEnabled = false,
            loopMaxIterations = 1
        )
        every { clippingPipelinePort.collect(any(), any()) } returns sampleCollectResult()
        every { clippingPipelinePort.summarize(any()) } returns sampleSummarizeResult()
        every { clippingPipelinePort.digest(any(), any(), any(), any(), any()) } returns sampleDigestResult().copy(
            digestText = "digest with DONE_SIGNAL"
        )
        every { batchSummaryStore.findUnsent("cat-1") } returns emptyList()
        every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()

        val orchestrator = RalphPipelineOrchestrator(
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            batchSummaryStore = batchSummaryStore,
            reviewItemDecisionStore = reviewItemDecisionStore,
            adminReviewQueueService = adminReviewQueueService,
            metrics = metrics
        )

        val result = orchestrator.runPipeline(
            categoryId = "cat-1",
            hoursBack = null,
            maxItems = null,
            unsentOnly = null,
            sendToSlack = null,
            slackChannelId = null,
            loopEnabledOverride = true,
            loopMaxIterationsOverride = 3,
            loopStopPhraseOverride = "DONE_SIGNAL"
        )

        result.loopEnabled shouldBe true
        result.loopIterationCount shouldBe 1
        result.loopStopReason shouldBe RalphLoopStopReason.STOP_PHRASE_DETECTED
        result.loopStopPhrase shouldBe "DONE_SIGNAL"
        verify(exactly = 1) { clippingPipelinePort.collect("cat-1", 24) }
        verify(exactly = 1) {
            metrics.recordRalphLoopResult(
                stopReason = RalphLoopStopReason.STOP_PHRASE_DETECTED.name,
                iterationCount = 1
            )
        }
    }

    @Test
    fun `runPipeline should move duplicate summary to review`() {
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val batchSummaryStore = mockk<BatchSummaryStore>()
        val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()
        val adminReviewQueueService = mockk<AdminReviewQueueService>()
        val metrics = mockk<ClippingMetrics>(relaxed = true)

        every { runtimeSettingService.current() } returns sampleRuntimeSettings(minImportance = 0.1f)
        every { clippingPipelinePort.collect(any(), any()) } returns sampleCollectResult()
        every { clippingPipelinePort.summarize(any()) } returns sampleSummarizeResult()
        every { clippingPipelinePort.digest(any(), any(), any(), any(), any()) } returns sampleDigestResult()
        every { batchSummaryStore.findUnsent("cat-1") } returns listOf(
            summary(id = "primary", title = "Breaking Google launches new AI service", score = 0.9f),
            summary(id = "duplicate", title = "Breaking: Google launches new AI service", score = 0.8f)
        )
        every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
        every {
            adminReviewQueueService.markReview(
                summaryId = "duplicate",
                reason = match { it.contains("중복") },
                reviewedBy = "ralph-critic"
            )
        } returns ReviewItemDecision(
            summaryId = "duplicate",
            categoryId = "cat-1",
            status = ReviewDecisionStatus.REVIEW,
            reason = "ralph",
            reviewedBy = "ralph-critic",
            reviewedAt = Instant.now()
        )

        val orchestrator = RalphPipelineOrchestrator(
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            batchSummaryStore = batchSummaryStore,
            reviewItemDecisionStore = reviewItemDecisionStore,
            adminReviewQueueService = adminReviewQueueService,
            metrics = metrics
        )

        val result = orchestrator.runPipeline(
            categoryId = "cat-1",
            hoursBack = 12,
            maxItems = 3,
            unsentOnly = true,
            sendToSlack = false,
            slackChannelId = null
        )

        result.orchestrationWarnings.any { it.contains("moved") } shouldBe true
        verify(exactly = 1) {
            adminReviewQueueService.markReview(
                summaryId = "duplicate",
                reason = match { it.contains("중복") },
                reviewedBy = "ralph-critic"
            )
        }
    }

    private fun summary(id: String, title: String, score: Float): BatchSummary =
        BatchSummary(
            id = id,
            originalTitle = title,
            translatedTitle = null,
            summary = "요약 내용",
            keywords = listOf("ai", "agent"),
            importanceScore = score,
            sourceLink = "https://example.com/$id",
            categoryId = "cat-1",
            rssItemId = "item-$id",
            createdAt = Instant.parse("2026-03-04T00:00:00Z")
        )

    private fun sampleCollectResult(): PipelineCollectResult =
        CollectResult(
            totalCollected = 2,
            newItems = 2,
            duplicateSkipped = 0,
            categories = listOf(CollectCategoryResult("cat-1", "카테고리", 2, 2))
        ).toPipelineCollectResult()

    private fun sampleSummarizeResult(): PipelineSummarizeResult =
        SummarizeResult(
            totalSummarized = 2,
            categories = listOf(SummarizeCategoryResult("cat-1", "카테고리", 2))
        ).toPipelineSummarizeResult()

    private fun sampleDigestResult(): PipelineDigestResult =
        DigestResult(
            categoryId = "cat-1",
            categoryName = "카테고리",
            unsentOnly = true,
            totalCandidates = 2,
            selectedCount = 1,
            postedToSlack = false,
            slackChannelId = null,
            slackMessageTs = null,
            markedSentCount = 0,
            digestText = "digest",
            items = emptyList()
        ).toPipelineDigestResult()

    private fun sampleRuntimeSettings(
        minImportance: Float,
        loopEnabled: Boolean = false,
        loopMaxIterations: Int = 1,
        stopPhrase: String = "RALPH_STOP"
    ): RuntimeSettingService.RuntimeSettings =
        RuntimeSettingService.RuntimeSettings(
            defaultHoursBack = 24,
            summaryInputMaxChars = 5000,
            digestMinImportanceScore = minImportance,
            digestDefaultMaxItems = 5,
            digestMaxMessageChars = 3500,
            digestItemSummaryMaxChars = 960,
            digestKeywordMaxCount = 6,
            jobWorkerBatchSize = 5,
            jobMaxAttempts = 3,
            jobInitialBackoffSeconds = 30,
            slackBotToken = "",
            slackDigestBlockKitTemplate = "",
            slackAutoDigestEnabled = false,
            slackDigestCron = "-",
            slackAutoDigestMaxItems = 5,
            slackAutoDigestUnsentOnly = true,
            slackDailyChannelMessageLimit = 3,
            ralphOrchestrationEnabled = true,
            ralphLoopEnabled = loopEnabled,
            ralphLoopMaxIterations = loopMaxIterations,
            ralphLoopStopPhrase = stopPhrase,
            updatedAt = null
        )
}
