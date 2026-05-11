package com.ohmyclipping.service

import com.ohmyclipping.service.dto.clipping.CollectResult
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.observability.SchedulerRunTracker
import com.ohmyclipping.service.pipeline.toPipelineCollectResult
import com.ohmyclipping.service.port.ClippingPipelinePort
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class AsyncClipJobServiceTest {

    private val autoCollectionScheduler = mockk<AutoCollectionScheduler>(relaxed = true)

    private fun runtimeSettings(
        jobWorkerBatchSize: Int = 5,
        jobMaxAttempts: Int = 3,
        jobInitialBackoffSeconds: Int = 30
    ) = RuntimeSettingService.RuntimeSettings(
        defaultHoursBack = 24,
        summaryInputMaxChars = 5000,
        digestMinImportanceScore = 0.5f,
        digestDefaultMaxItems = 5,
        digestMaxMessageChars = 3500,
        digestItemSummaryMaxChars = 960,
        digestKeywordMaxCount = 6,
        jobWorkerBatchSize = jobWorkerBatchSize,
        jobMaxAttempts = jobMaxAttempts,
        jobInitialBackoffSeconds = jobInitialBackoffSeconds,
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

    @Test
    fun `processDueJobs should mark collect job success when collect succeeds`() {
        val store = InMemoryAsyncJobStore()
        val clippingService = mockk<ClippingPipelinePort>()
        every { clippingService.collect(null, null) } returns CollectResult(0, 0, 0, emptyList()).toPipelineCollectResult()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        every { runtimeSettingService.current() } returns runtimeSettings()
        val metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        val service = AsyncClipJobService(
            jobStore = store,
            clippingPipelinePort = clippingService,
            runtimeSettingService = runtimeSettingService,
            metrics = metrics,
            autoCollectionScheduler = autoCollectionScheduler
        )

        val queued = service.enqueueCollect(null, null)
        val processed = service.processDueJobs(maxJobs = 1)

        processed shouldBe 1
        val status = service.getJobStatus(queued.jobId)
        status.status shouldBe "SUCCEEDED"
    }

    @Test
    fun `processDueJobs should schedule retry on failure`() {
        val store = InMemoryAsyncJobStore()
        val clippingService = mockk<ClippingPipelinePort>()
        every { clippingService.collect(null, null) } throws RuntimeException("boom")
        val runtimeSettingService = mockk<RuntimeSettingService>()
        every { runtimeSettingService.current() } returns runtimeSettings(
            jobMaxAttempts = 3,
            jobInitialBackoffSeconds = 1
        )
        val metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        val service = AsyncClipJobService(
            jobStore = store,
            clippingPipelinePort = clippingService,
            runtimeSettingService = runtimeSettingService,
            metrics = metrics,
            autoCollectionScheduler = autoCollectionScheduler
        )

        val queued = service.enqueueCollect(null, null)
        val processed = service.processDueJobs(maxJobs = 1)

        processed shouldBe 1
        val status = service.getJobStatus(queued.jobId)
        status.status shouldBe "PENDING"
        status.attempts shouldBe 1
        status.lastError shouldBe "boom"
    }

    @Test
    fun `processDueJobs should mark failed after max attempts`() {
        val store = InMemoryAsyncJobStore()
        val clippingService = mockk<ClippingPipelinePort>()
        every { clippingService.collect(null, null) } throws RuntimeException("boom")
        val runtimeSettingService = mockk<RuntimeSettingService>()
        every { runtimeSettingService.current() } returns runtimeSettings(
            jobMaxAttempts = 1,
            jobInitialBackoffSeconds = 1
        )
        val metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        val service = AsyncClipJobService(
            jobStore = store,
            clippingPipelinePort = clippingService,
            runtimeSettingService = runtimeSettingService,
            metrics = metrics,
            autoCollectionScheduler = autoCollectionScheduler
        )

        val queued = service.enqueueCollect(null, null)
        service.processDueJobs(maxJobs = 1)

        val status = service.getJobStatus(queued.jobId)
        status.status shouldBe "FAILED"
        status.attempts shouldBe 1
        status.lastError shouldBe "boom"
    }
}
