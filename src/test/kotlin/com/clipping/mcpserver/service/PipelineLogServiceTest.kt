package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.pipeline.PipelineLogService
import com.clipping.mcpserver.service.pipeline.RecoveryDetector

import com.clipping.mcpserver.service.dto.clipping.CollectCategoryResult
import com.clipping.mcpserver.service.dto.clipping.CollectResult
import com.clipping.mcpserver.service.dto.clipping.DigestItemResult
import com.clipping.mcpserver.service.dto.clipping.DigestResult
import com.clipping.mcpserver.service.dto.clipping.PipelineRunResult
import com.clipping.mcpserver.service.dto.clipping.PipelineStepStatus
import com.clipping.mcpserver.service.dto.clipping.PipelineStepTrace
import com.clipping.mcpserver.service.dto.clipping.SummarizeCategoryResult
import com.clipping.mcpserver.service.dto.clipping.SummarizeResult
import com.clipping.mcpserver.service.port.PipelineRunOpsEvent
import com.clipping.mcpserver.service.port.PipelineStepTraceOpsEvent
import com.clipping.mcpserver.service.port.OpsLogNotifier
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class PipelineLogServiceTest {

    private val slackMessageSender = mockk<com.clipping.mcpserver.service.port.SlackDeliveryPort>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val opsLogNotifier = mockk<OpsLogNotifier>(relaxed = true)
    private val incidentWindowTracker = mockk<IncidentWindowTracker>(relaxed = true)
    private val recoveryDetector = mockk<RecoveryDetector>(relaxed = true)

    private val service = PipelineLogService(
        slackMessageSender,
        runtimeSettingService,
        opsLogNotifier,
        incidentWindowTracker,
        recoveryDetector,
    )

    private fun defaultSettings(opsLogChannelId: String = "") =
        RuntimeSettingService.RuntimeSettings(
            defaultHoursBack = 24,
            summaryInputMaxChars = 5000,
            digestMinImportanceScore = 0.3f,
            digestDefaultMaxItems = 3,
            digestMaxMessageChars = 3000,
            digestItemSummaryMaxChars = 500,
            digestKeywordMaxCount = 5,
            jobWorkerBatchSize = 10,
            jobMaxAttempts = 3,
            jobInitialBackoffSeconds = 30,
            slackBotToken = "xoxb-test",
            slackDigestBlockKitTemplate = "",
            slackAutoDigestEnabled = false,
            slackDigestCron = "0 0 9 * * MON-FRI",
            slackAutoDigestMaxItems = 3,
            slackAutoDigestUnsentOnly = true,
            slackDailyChannelMessageLimit = 3,
            opsLogChannelId = opsLogChannelId,
            updatedAt = null
        )

    private fun sampleResult(
        collectedItems: Int = 47,
        duplicateSkipped: Int = 3,
        summarized: Int = 42,
        selectedCount: Int = 8,
        postedToSlack: Boolean = true,
        totalCandidates: Int = 9,
        failedSteps: List<String> = emptyList()
    ): PipelineRunResult {
        val now = Instant.now()
        val later = now.plusSeconds(12)

        val traces = mutableListOf(
            PipelineStepTrace("COLLECT", PipelineStepStatus.SUCCEEDED, now.toString(), later.toString()),
            PipelineStepTrace("SUMMARIZE", PipelineStepStatus.SUCCEEDED, later.toString(), later.plusSeconds(18).toString()),
            PipelineStepTrace("DIGEST", PipelineStepStatus.SUCCEEDED, later.plusSeconds(18).toString(), later.plusSeconds(20).toString())
        )

        // 실패 단계 덮어쓰기
        failedSteps.forEach { stepName ->
            val idx = traces.indexOfFirst { it.step == stepName }
            if (idx >= 0) {
                traces[idx] = traces[idx].copy(status = PipelineStepStatus.FAILED, detail = "API 타임아웃")
            }
        }

        return PipelineRunResult(
            collect = CollectResult(
                totalCollected = collectedItems,
                newItems = collectedItems - duplicateSkipped,
                duplicateSkipped = duplicateSkipped,
                categories = listOf(
                    CollectCategoryResult("cat1", "경제뉴스", collectedItems, collectedItems - duplicateSkipped)
                )
            ),
            summarize = SummarizeResult(
                totalSummarized = summarized,
                categories = listOf(
                    SummarizeCategoryResult("cat1", "경제뉴스", summarized)
                )
            ),
            digest = DigestResult(
                categoryId = "cat1",
                categoryName = "경제뉴스",
                unsentOnly = true,
                totalCandidates = totalCandidates,
                selectedCount = selectedCount,
                postedToSlack = postedToSlack,
                slackChannelId = "C123",
                slackMessageTs = "1234567890.123456",
                markedSentCount = selectedCount,
                digestText = "digest text",
                items = emptyList()
            ),
            stepTraces = traces
        )
    }

    private fun sampleRunEvent(
        id: String = "run-1",
        categoryId: String = "cat-1",
        status: String = "SUCCEEDED",
    ) = PipelineRunOpsEvent(
        id = id,
        categoryId = categoryId,
        categoryName = "경제뉴스",
        status = status,
        totalCollected = null,
        totalSummarized = null,
        totalDigestSelected = null,
        endedAt = null,
    )

    @Nested
    inner class `채널 ID 해석` {

        @Test
        fun `채널 ID가 설정되어 있으면 해당 값을 반환한다`() {
            every { runtimeSettingService.current() } returns defaultSettings("C_OPS_LOG")
            service.resolveOpsLogChannelId() shouldBe "C_OPS_LOG"
        }

        @Test
        fun `채널 ID가 빈 문자열이면 null을 반환한다`() {
            every { runtimeSettingService.current() } returns defaultSettings("")
            service.resolveOpsLogChannelId() shouldBe null
        }

        @Test
        fun `채널 ID가 공백만 있으면 null을 반환한다`() {
            every { runtimeSettingService.current() } returns defaultSettings("   ")
            service.resolveOpsLogChannelId() shouldBe null
        }
    }

    @Nested
    inner class `메시지 포맷` {

        @Test
        fun `정상 실행 시 수집, 요약, 발송 정보를 포함한다`() {
            val result = sampleResult()
            val message = service.formatMessage(result, "경제뉴스", 30_000)

            message shouldContain "파이프라인 실행 완료"
            message shouldContain "경제뉴스"
            message shouldContain "47건 수집"
            message shouldContain "3건 중복"
            message shouldContain "42건 완료"
            message shouldContain "8건 발송"
            message shouldContain "1건 건너뜀"
        }

        @Test
        fun `실패 단계가 있으면 경고 섹션이 추가된다`() {
            val result = sampleResult(failedSteps = listOf("SUMMARIZE"))
            val message = service.formatMessage(result, "경제뉴스", 30_000)

            message shouldContain "⚠️"
            message shouldContain "요약 실패"
            message shouldContain "API 타임아웃"
        }

        @Test
        fun `실패 단계가 없으면 경고가 표시되지 않는다`() {
            val result = sampleResult()
            val message = service.formatMessage(result, "경제뉴스", 30_000)

            message shouldNotContain "⚠️"
        }
    }

    @Nested
    inner class `실패 메시지 포맷` {

        @Test
        fun `에러 메시지를 포함한 실패 로그를 포맷한다`() {
            val message = service.formatFailureMessage("경제뉴스", "Connection timeout", 5_000)

            message shouldContain "파이프라인 실행 실패"
            message shouldContain "경제뉴스"
            message shouldContain "Connection timeout"
        }

        @Test
        fun `에러 메시지가 null이면 알 수 없는 오류로 대체한다`() {
            val message = service.formatFailureMessage("경제뉴스", null, 5_000)

            message shouldContain "알 수 없는 오류"
        }
    }

    @Nested
    inner class `파이프라인 실패 알림` {

        @Test
        fun `실패 시 OpsLogNotifier postPipelineFailure를 호출한다`() {
            val run = sampleRunEvent(status = "FAILED")
            val failedSteps = listOf<PipelineStepTraceOpsEvent>()

            // TX 없는 환경에서 publishAfterCommit은 즉시 실행한다
            service.onPipelineFailure(run, failedSteps)

            verify(exactly = 1) { opsLogNotifier.postPipelineFailure(run, failedSteps) }
        }

        @Test
        fun `실패 시 IncidentWindowTracker에 실패를 기록한다`() {
            val run = sampleRunEvent(id = "run-fail", categoryId = "cat-2", status = "FAILED")

            service.onPipelineFailure(run, emptyList())

            verify(exactly = 1) { incidentWindowTracker.recordFailure("cat-2", "run-fail") }
        }

        @Test
        fun `OpsLogNotifier 예외는 전파되지 않는다`() {
            val run = sampleRunEvent(status = "FAILED")
            every { opsLogNotifier.postPipelineFailure(any(), any()) } throws RuntimeException("Slack down")

            // 예외가 전파되지 않아야 한다
            service.onPipelineFailure(run, emptyList())
        }
    }

    @Nested
    inner class `파이프라인 완료 알림` {

        @Test
        fun `완료 시 OpsLogNotifier postPipelineSuccess를 호출한다`() {
            val run = sampleRunEvent(status = "SUCCEEDED")

            service.onPipelineCompleted(run)

            verify(exactly = 1) { opsLogNotifier.postPipelineSuccess(run) }
        }

        @Test
        fun `완료 시 RecoveryDetector에 알린다`() {
            val run = sampleRunEvent(status = "SUCCEEDED")

            service.onPipelineCompleted(run)

            verify(exactly = 1) { recoveryDetector.onRunCompleted(run) }
        }

        @Test
        fun `OpsLogNotifier 예외는 전파되지 않는다`() {
            val run = sampleRunEvent(status = "SUCCEEDED")
            every { opsLogNotifier.postPipelineSuccess(any()) } throws RuntimeException("Slack down")

            // 예외가 전파되지 않아야 한다
            service.onPipelineCompleted(run)
        }
    }

    @Nested
    inner class `레거시 로그 전송` {

        @Test
        fun `채널이 설정되어 있으면 메시지를 전송한다`() {
            every { runtimeSettingService.current() } returns defaultSettings("C_OPS_LOG")

            service.sendPipelineLog(sampleResult(), "경제뉴스", 30_000)

            val messageSlot = slot<String>()
            verify(exactly = 1) {
                slackMessageSender.sendMessage("C_OPS_LOG", capture(messageSlot), any(), any(), any(), any(), any())
            }
            messageSlot.captured shouldContain "파이프라인 실행 완료"
        }

        @Test
        fun `채널이 미설정이면 전송하지 않는다`() {
            every { runtimeSettingService.current() } returns defaultSettings("")

            service.sendPipelineLog(sampleResult(), "경제뉴스", 30_000)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `전송 중 예외가 발생해도 전파하지 않는다`() {
            every { runtimeSettingService.current() } returns defaultSettings("C_OPS_LOG")
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("Slack API error")

            // 예외가 전파되지 않는지 확인
            service.sendPipelineLog(sampleResult(), "경제뉴스", 30_000)
        }

        @Test
        fun `실패 로그도 채널이 설정되어 있으면 전송한다`() {
            every { runtimeSettingService.current() } returns defaultSettings("C_OPS_LOG")

            service.sendPipelineFailureLog("경제뉴스", "timeout", 5_000)

            val messageSlot = slot<String>()
            verify(exactly = 1) {
                slackMessageSender.sendMessage("C_OPS_LOG", capture(messageSlot), any(), any(), any(), any(), any())
            }
            messageSlot.captured shouldContain "파이프라인 실행 실패"
        }

        @Test
        fun `실패 로그도 채널 미설정 시 전송하지 않는다`() {
            every { runtimeSettingService.current() } returns defaultSettings("")

            service.sendPipelineFailureLog("경제뉴스", "timeout", 5_000)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }
    }
}
