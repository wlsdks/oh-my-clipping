package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.dto.clipping.CollectResult
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerRunTracker
import com.clipping.mcpserver.service.pipeline.toPipelineCollectResult
import com.clipping.mcpserver.service.port.ClippingPipelinePort
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class AsyncClipJobMetricsIntegrationTest {

    private val autoCollectionScheduler = mockk<AutoCollectionScheduler>(relaxed = true)

    @Test
    fun `enqueue and process should update queue and processed job metrics`() {
        val registry = SimpleMeterRegistry()
        val metrics = ClippingMetrics(null, registry, SchedulerRunTracker())
        val store = InMemoryAsyncJobStore()
        val clippingService = mockk<ClippingPipelinePort>()
        every { clippingService.collect(null, null) } returns CollectResult(0, 0, 0, emptyList()).toPipelineCollectResult()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        every { runtimeSettingService.current() } returns RuntimeSettingService.RuntimeSettings(
            defaultHoursBack = 24,
            summaryInputMaxChars = 5000,
            digestMinImportanceScore = 0.5f,
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
            slackDailyChannelMessageLimit = 5,
            ralphOrchestrationEnabled = false,
            ralphLoopEnabled = true,
            ralphLoopMaxIterations = 4,
            ralphLoopStopPhrase = "RALPH_STOP",
            updatedAt = null
        )

        val service = AsyncClipJobService(
            jobStore = store,
            clippingPipelinePort = clippingService,
            runtimeSettingService = runtimeSettingService,
            metrics = metrics,
            autoCollectionScheduler = autoCollectionScheduler
        )

        service.enqueueCollect(null, null)
        registry.get("clipping.jobs.pending").gauge().value() shouldBe 1.0

        service.processDueJobs(1)

        registry.get("clipping.jobs.pending").gauge().value() shouldBe 0.0
        registry.get("clipping.jobs.processed")
            .tag("jobType", "COLLECT")
            .tag("result", "SUCCEEDED")
            .counter().count() shouldBe 1.0
    }
}
