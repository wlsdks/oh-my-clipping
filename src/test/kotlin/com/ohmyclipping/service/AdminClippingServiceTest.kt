package com.ohmyclipping.service

import com.ohmyclipping.service.pipeline.DeterministicPipelineRunner
import com.ohmyclipping.service.pipeline.RalphPipelineOrchestrator

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.service.dto.clipping.CollectCategoryResult
import com.ohmyclipping.service.dto.clipping.CollectResult
import com.ohmyclipping.model.Category
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.service.dto.clipping.PipelineOrchestrationMode
import com.ohmyclipping.service.dto.clipping.PipelineRunResult
import com.ohmyclipping.service.dto.clipping.SummarizeCategoryResult
import com.ohmyclipping.service.dto.clipping.SummarizeResult
import com.ohmyclipping.service.port.ClippingPipelinePort
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RetentionPolicyStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class AdminClippingServiceTest {

    @Test
    fun `updateSettings should throw conflict when category updatedAt mismatch`() {
        val categoryStore = mockk<CategoryStore>()
        val retentionPolicyStore = mockk<RetentionPolicyStore>()
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val ralphPipelineOrchestrator = mockk<RalphPipelineOrchestrator>()
        val deterministicPipelineRunner = mockk<DeterministicPipelineRunner>()
        val properties = ClippingMcpServerProperties()
        val category = category(id = "cat-1", updatedAt = Instant.parse("2026-03-02T00:00:00Z"))

        every { categoryStore.findById("cat-1") } returns category
        every { categoryStore.updateWithExpectedUpdatedAt(any(), any()) } returns null

        val service = AdminClippingService(
            categoryStore = categoryStore,
            retentionPolicyStore = retentionPolicyStore,
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            ralphPipelineOrchestrator = ralphPipelineOrchestrator,
            deterministicPipelineRunner = deterministicPipelineRunner,
            properties = properties
        )

        val exception = shouldThrow<ConflictException> {
            service.updateSettings(
                categoryId = "cat-1",
                isActive = true,
                slackChannelId = "C0123ABCD",
                maxItems = 5,
                retentionKeepDays = 30,
                retentionEnabled = true,
                expectedCategoryUpdatedAt = Instant.parse("2026-03-01T23:59:59Z")
            )
        }

        exception.message shouldBe "카테고리 설정이 다른 관리자에 의해 변경되었습니다. 새로고침 후 다시 저장해주세요."
        verify(exactly = 1) { categoryStore.updateWithExpectedUpdatedAt(any(), any()) }
        verify(exactly = 0) { categoryStore.update(any()) }
    }

    @Test
    fun `runPipeline should use Ralph orchestration when enabled`() {
        val categoryStore = mockk<CategoryStore>()
        val retentionPolicyStore = mockk<RetentionPolicyStore>()
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val ralphPipelineOrchestrator = mockk<RalphPipelineOrchestrator>()
        val deterministicPipelineRunner = mockk<DeterministicPipelineRunner>()
        val properties = ClippingMcpServerProperties()
        val expected = samplePipelineResult(mode = PipelineOrchestrationMode.RALPH)

        every { runtimeSettingService.current() } returns sampleRuntimeSettings(ralphEnabled = true)
        every {
            ralphPipelineOrchestrator.runPipeline(
                categoryId = "cat-1",
                hoursBack = 24,
                maxItems = 5,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = null,
                loopEnabledOverride = null,
                loopMaxIterationsOverride = null,
                loopStopPhraseOverride = null
            )
        } returns expected

        val service = AdminClippingService(
            categoryStore = categoryStore,
            retentionPolicyStore = retentionPolicyStore,
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            ralphPipelineOrchestrator = ralphPipelineOrchestrator,
            deterministicPipelineRunner = deterministicPipelineRunner,
            properties = properties
        )

        val result = service.runPipeline(
            categoryId = "cat-1",
            hoursBack = 24,
            maxItems = 5,
            unsentOnly = true,
            sendToSlack = false,
            slackChannelId = null
        )

        result.orchestrationMode shouldBe PipelineOrchestrationMode.RALPH
        result.fallbackApplied shouldBe false
        verify(exactly = 1) { ralphPipelineOrchestrator.runPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { deterministicPipelineRunner.run(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `runPipeline should delegate to deterministic runner when Ralph orchestration is disabled`() {
        val categoryStore = mockk<CategoryStore>()
        val retentionPolicyStore = mockk<RetentionPolicyStore>()
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val ralphPipelineOrchestrator = mockk<RalphPipelineOrchestrator>()
        val deterministicPipelineRunner = mockk<DeterministicPipelineRunner>()
        val properties = ClippingMcpServerProperties()
        val expected = samplePipelineResult(mode = PipelineOrchestrationMode.DETERMINISTIC)

        every { runtimeSettingService.current() } returns sampleRuntimeSettings(ralphEnabled = false)
        every {
            deterministicPipelineRunner.run(
                categoryId = "cat-1",
                hoursBack = 24,
                maxItems = 5,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = null,
                fallbackReason = null
            )
        } returns expected

        val service = AdminClippingService(
            categoryStore = categoryStore,
            retentionPolicyStore = retentionPolicyStore,
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            ralphPipelineOrchestrator = ralphPipelineOrchestrator,
            deterministicPipelineRunner = deterministicPipelineRunner,
            properties = properties
        )

        val result = service.runPipeline(
            categoryId = "cat-1",
            hoursBack = 24,
            maxItems = 5,
            unsentOnly = true,
            sendToSlack = false,
            slackChannelId = null
        )

        result.orchestrationMode shouldBe PipelineOrchestrationMode.DETERMINISTIC
        verify(exactly = 0) { ralphPipelineOrchestrator.runPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) {
            deterministicPipelineRunner.run(
                categoryId = "cat-1",
                hoursBack = 24,
                maxItems = 5,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = null,
                fallbackReason = null
            )
        }
    }

    @Test
    fun `runPipeline should fallback to deterministic flow when Ralph orchestration fails`() {
        val categoryStore = mockk<CategoryStore>()
        val retentionPolicyStore = mockk<RetentionPolicyStore>()
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val ralphPipelineOrchestrator = mockk<RalphPipelineOrchestrator>()
        val deterministicPipelineRunner = mockk<DeterministicPipelineRunner>()
        val properties = ClippingMcpServerProperties()
        val expected = samplePipelineResult(mode = PipelineOrchestrationMode.DETERMINISTIC).copy(
            fallbackApplied = true,
            orchestrationWarnings = listOf("Ralph fallback applied: ralph failed")
        )

        every { runtimeSettingService.current() } returns sampleRuntimeSettings(ralphEnabled = true)
        every { ralphPipelineOrchestrator.runPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("ralph failed")
        every {
            deterministicPipelineRunner.run(
                categoryId = "cat-1",
                hoursBack = 12,
                maxItems = 3,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = null,
                fallbackReason = "ralph failed"
            )
        } returns expected

        val service = AdminClippingService(
            categoryStore = categoryStore,
            retentionPolicyStore = retentionPolicyStore,
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            ralphPipelineOrchestrator = ralphPipelineOrchestrator,
            deterministicPipelineRunner = deterministicPipelineRunner,
            properties = properties
        )

        val result = service.runPipeline(
            categoryId = "cat-1",
            hoursBack = 12,
            maxItems = 3,
            unsentOnly = true,
            sendToSlack = false,
            slackChannelId = null
        )

        result.orchestrationMode shouldBe PipelineOrchestrationMode.DETERMINISTIC
        result.fallbackApplied shouldBe true
        result.orchestrationWarnings.size shouldBe 1
        verify(exactly = 1) { ralphPipelineOrchestrator.runPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) {
            deterministicPipelineRunner.run(
                categoryId = "cat-1",
                hoursBack = 12,
                maxItems = 3,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = null,
                fallbackReason = "ralph failed"
            )
        }
    }

    @Test
    fun `runPipeline should forward Ralph loop overrides to orchestrator`() {
        val categoryStore = mockk<CategoryStore>()
        val retentionPolicyStore = mockk<RetentionPolicyStore>()
        val clippingPipelinePort = mockk<ClippingPipelinePort>()
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val ralphPipelineOrchestrator = mockk<RalphPipelineOrchestrator>()
        val deterministicPipelineRunner = mockk<DeterministicPipelineRunner>()
        val properties = ClippingMcpServerProperties()
        val expected = samplePipelineResult(mode = PipelineOrchestrationMode.RALPH)

        every { runtimeSettingService.current() } returns sampleRuntimeSettings(ralphEnabled = true)
        every {
            ralphPipelineOrchestrator.runPipeline(
                categoryId = "cat-1",
                hoursBack = 6,
                maxItems = 3,
                unsentOnly = false,
                sendToSlack = true,
                slackChannelId = "CPIPELINE01",
                loopEnabledOverride = true,
                loopMaxIterationsOverride = 7,
                loopStopPhraseOverride = "DONE_SIGNAL"
            )
        } returns expected

        val service = AdminClippingService(
            categoryStore = categoryStore,
            retentionPolicyStore = retentionPolicyStore,
            clippingPipelinePort = clippingPipelinePort,
            runtimeSettingService = runtimeSettingService,
            ralphPipelineOrchestrator = ralphPipelineOrchestrator,
            deterministicPipelineRunner = deterministicPipelineRunner,
            properties = properties
        )

        val result = service.runPipeline(
            categoryId = "cat-1",
            hoursBack = 6,
            maxItems = 3,
            unsentOnly = false,
            sendToSlack = true,
            slackChannelId = "CPIPELINE01",
            ralphLoopEnabledOverride = true,
            ralphLoopMaxIterationsOverride = 7,
            ralphLoopStopPhraseOverride = "DONE_SIGNAL"
        )

        result.orchestrationMode shouldBe PipelineOrchestrationMode.RALPH
        verify(exactly = 1) {
            ralphPipelineOrchestrator.runPipeline(
                categoryId = "cat-1",
                hoursBack = 6,
                maxItems = 3,
                unsentOnly = false,
                sendToSlack = true,
                slackChannelId = "CPIPELINE01",
                loopEnabledOverride = true,
                loopMaxIterationsOverride = 7,
                loopStopPhraseOverride = "DONE_SIGNAL"
            )
        }
    }

    private fun samplePipelineResult(mode: PipelineOrchestrationMode): PipelineRunResult =
        PipelineRunResult(
            collect = CollectResult(
                totalCollected = 1,
                newItems = 1,
                duplicateSkipped = 0,
                categories = listOf(CollectCategoryResult("cat-1", "카테고리", 1, 1))
            ),
            summarize = SummarizeResult(
                totalSummarized = 1,
                categories = listOf(SummarizeCategoryResult("cat-1", "카테고리", 1))
            ),
            digest = DigestResult(
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
            ),
            orchestrationMode = mode,
            fallbackApplied = false,
            orchestrationWarnings = emptyList()
        )

    private fun sampleRuntimeSettings(ralphEnabled: Boolean): RuntimeSettingService.RuntimeSettings =
        RuntimeSettingService.RuntimeSettings(
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
            slackDailyChannelMessageLimit = 3,
            ralphOrchestrationEnabled = ralphEnabled,
            ralphLoopEnabled = true,
            ralphLoopMaxIterations = 3,
            ralphLoopStopPhrase = "RALPH_STOP",
            updatedAt = null
        )

    private fun category(id: String, updatedAt: Instant): Category =
        Category(
            id = id,
            name = "운영 카테고리",
            description = "설명",
            slackChannelId = "C0123ABCD",
            isActive = true,
            maxItems = 5,
            personaId = null,
            createdAt = Instant.parse("2026-03-01T00:00:00Z"),
            updatedAt = updatedAt
        )
}
