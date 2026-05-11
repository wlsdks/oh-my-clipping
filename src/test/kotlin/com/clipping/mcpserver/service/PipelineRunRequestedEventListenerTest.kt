package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.pipeline.PipelineAsyncWorker

import com.clipping.mcpserver.service.dto.pipeline.PipelineRunEntity
import com.clipping.mcpserver.service.dto.pipeline.PipelineRunStatus
import com.clipping.mcpserver.service.event.PipelineRunRequestedEvent
import com.clipping.mcpserver.service.event.PipelineRunRequestedEventListener
import io.mockk.justRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.RejectedExecutionException

class PipelineRunRequestedEventListenerTest {

    private val pipelineAsyncWorker = mockk<PipelineAsyncWorker>()
    private val pipelineRunStore = mockk<com.clipping.mcpserver.store.PipelineRunStore>()
    private val listener = PipelineRunRequestedEventListener(pipelineAsyncWorker, pipelineRunStore)

    @Test
    fun `커밋 이후 이벤트는 비동기 워커에 동일한 실행 파라미터를 전달한다`() {
        justRun {
            pipelineAsyncWorker.execute(
                runId = "run-1",
                categoryId = "cat-1",
                hoursBack = 24,
                maxItems = 10,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = "C123",
                ralphLoopEnabled = true,
                ralphLoopMaxIterations = 2,
                ralphLoopStopPhrase = "중단"
            )
        }

        listener.handle(
            PipelineRunRequestedEvent(
                runId = "run-1",
                categoryId = "cat-1",
                hoursBack = 24,
                maxItems = 10,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = "C123",
                ralphLoopEnabled = true,
                ralphLoopMaxIterations = 2,
                ralphLoopStopPhrase = "중단"
            )
        )

        verify(exactly = 1) {
            pipelineAsyncWorker.execute(
                runId = "run-1",
                categoryId = "cat-1",
                hoursBack = 24,
                maxItems = 10,
                unsentOnly = true,
                sendToSlack = false,
                slackChannelId = "C123",
                ralphLoopEnabled = true,
                ralphLoopMaxIterations = 2,
                ralphLoopStopPhrase = "중단"
            )
        }
    }

    @Test
    fun `비동기 워커 제출이 거부되면 run을 FAILED로 종료한다`() {
        every {
            pipelineAsyncWorker.execute(
                runId = "run-rejected",
                categoryId = "cat-1",
                hoursBack = null,
                maxItems = null,
                unsentOnly = null,
                sendToSlack = null,
                slackChannelId = null,
                ralphLoopEnabled = null,
                ralphLoopMaxIterations = null,
                ralphLoopStopPhrase = null
            )
        } throws RejectedExecutionException("queue full")

        val startedAt = Instant.parse("2026-05-01T00:00:00Z")
        every { pipelineRunStore.findById("run-rejected") } returns PipelineRunEntity(
            id = "run-rejected",
            categoryId = "cat-1",
            categoryName = "테스트",
            triggeredBy = "admin",
            status = PipelineRunStatus.RUNNING,
            orchestrationMode = "PENDING",
            startedAt = startedAt,
        )
        val updatedSlot = slot<PipelineRunEntity>()
        justRun { pipelineRunStore.update(capture(updatedSlot)) }

        listener.handle(
            PipelineRunRequestedEvent(
                runId = "run-rejected",
                categoryId = "cat-1",
                hoursBack = null,
                maxItems = null,
                unsentOnly = null,
                sendToSlack = null,
                slackChannelId = null,
                ralphLoopEnabled = null,
                ralphLoopMaxIterations = null,
                ralphLoopStopPhrase = null
            )
        )

        updatedSlot.captured.status shouldBe PipelineRunStatus.FAILED
        updatedSlot.captured.endedAt?.isAfter(startedAt) shouldBe true
        updatedSlot.captured.errorMessage shouldBe "Pipeline async execution rejected: queue full"
    }

    @Test
    fun `비동기 워커 제출 거부는 이미 종료된 run 상태를 덮어쓰지 않는다`() {
        every {
            pipelineAsyncWorker.execute(
                runId = "run-already-done",
                categoryId = "cat-1",
                hoursBack = null,
                maxItems = null,
                unsentOnly = null,
                sendToSlack = null,
                slackChannelId = null,
                ralphLoopEnabled = null,
                ralphLoopMaxIterations = null,
                ralphLoopStopPhrase = null
            )
        } throws RejectedExecutionException("queue full")

        every { pipelineRunStore.findById("run-already-done") } returns PipelineRunEntity(
            id = "run-already-done",
            categoryId = "cat-1",
            categoryName = "테스트",
            triggeredBy = "admin",
            status = PipelineRunStatus.SUCCEEDED,
            orchestrationMode = "DONE",
            startedAt = Instant.parse("2026-05-01T00:00:00Z"),
            endedAt = Instant.parse("2026-05-01T00:01:00Z"),
            durationMs = 60_000,
        )

        listener.handle(
            PipelineRunRequestedEvent(
                runId = "run-already-done",
                categoryId = "cat-1",
                hoursBack = null,
                maxItems = null,
                unsentOnly = null,
                sendToSlack = null,
                slackChannelId = null,
                ralphLoopEnabled = null,
                ralphLoopMaxIterations = null,
                ralphLoopStopPhrase = null
            )
        )

        verify(exactly = 0) { pipelineRunStore.update(any()) }
    }
}
