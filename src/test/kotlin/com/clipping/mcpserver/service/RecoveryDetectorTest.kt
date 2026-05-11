package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.pipeline.RecoveryDetector

import com.clipping.mcpserver.service.dto.pipeline.PipelineRunEntity as ModelPipelineRunEntity
import com.clipping.mcpserver.service.dto.pipeline.PipelineRunStatus
import com.clipping.mcpserver.service.port.PipelineRunOpsEvent
import com.clipping.mcpserver.service.port.OpsLogNotifier
import com.clipping.mcpserver.store.PipelineRunStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class RecoveryDetectorTest {

    private lateinit var runStore: PipelineRunStore
    private lateinit var runtime: RuntimeSettingService
    private lateinit var notifier: OpsLogNotifier
    private lateinit var detector: RecoveryDetector

    @BeforeEach
    fun setUp() {
        runStore = mockk()
        runtime = mockk()
        notifier = mockk(relaxed = true)
        detector = RecoveryDetector(runStore, runtime, notifier)
    }

    private fun defaultSettings(streak: Int = 3) =
        mockk<RuntimeSettingService.RuntimeSettings>(relaxed = true) {
            every { opsRecoveryStreakThreshold } returns streak
        }

    private fun modelRun(
        id: String,
        categoryId: String,
        status: PipelineRunStatus,
    ) = ModelPipelineRunEntity(
        id = id,
        categoryId = categoryId,
        categoryName = "테스트 카테고리",
        triggeredBy = "auto",
        status = status,
        orchestrationMode = "auto",
        startedAt = Instant.parse("2026-04-15T05:00:00Z"),
        endedAt = Instant.parse("2026-04-15T05:30:00Z"),
    )

    private fun opsRun(
        id: String = "run-001",
        categoryId: String = "cat-001",
        status: String = "SUCCEEDED",
    ) = PipelineRunOpsEvent(
        id = id,
        categoryId = categoryId,
        categoryName = "테스트 카테고리",
        status = status,
        totalCollected = null,
        totalSummarized = null,
        totalDigestSelected = null,
        endedAt = null,
    )

    @Nested
    inner class `직전 3개가 모두 FAILED 이후 SUCCEEDED` {

        @Test
        fun `직전 3개가 모두 FAILED이고 현재가 SUCCEEDED이면 발송`() {
            every { runtime.current() } returns defaultSettings(streak = 3)
            val run = opsRun(id = "run-004", categoryId = "cat-001")
            every { runStore.findRecentByCategory("cat-001", 4) } returns listOf(
                modelRun("run-004", "cat-001", PipelineRunStatus.SUCCEEDED),
                modelRun("run-003", "cat-001", PipelineRunStatus.FAILED),
                modelRun("run-002", "cat-001", PipelineRunStatus.FAILED),
                modelRun("run-001", "cat-001", PipelineRunStatus.FAILED),
            )

            detector.onRunCompleted(run)

            verify(exactly = 1) { notifier.postPipelineRecovery("cat-001", run, 3) }
        }

        @Test
        fun `직전 3개 중 1개라도 SUCCEEDED이면 발송 안 함`() {
            every { runtime.current() } returns defaultSettings(streak = 3)
            val run = opsRun(id = "run-004", categoryId = "cat-001")
            every { runStore.findRecentByCategory("cat-001", 4) } returns listOf(
                modelRun("run-004", "cat-001", PipelineRunStatus.SUCCEEDED),
                modelRun("run-003", "cat-001", PipelineRunStatus.FAILED),
                modelRun("run-002", "cat-001", PipelineRunStatus.SUCCEEDED),  // 중간 성공
                modelRun("run-001", "cat-001", PipelineRunStatus.FAILED),
            )

            detector.onRunCompleted(run)

            verify(exactly = 0) { notifier.postPipelineRecovery(any(), any(), any()) }
        }
    }

    @Nested
    inner class `현재 run 상태가 SUCCEEDED가 아닌 경우` {

        @Test
        fun `현재 run이 SUCCEEDED가 아니면 store 조회 없이 발송 안 함`() {
            val run = opsRun(status = "FAILED")

            detector.onRunCompleted(run)

            verify(exactly = 0) { runStore.findRecentByCategory(any(), any()) }
            verify(exactly = 0) { notifier.postPipelineRecovery(any(), any(), any()) }
        }

        @Test
        fun `현재 run이 RUNNING이면 발송 안 함`() {
            val run = opsRun(status = "RUNNING")

            detector.onRunCompleted(run)

            verify(exactly = 0) { notifier.postPipelineRecovery(any(), any(), any()) }
        }
    }

    @Nested
    inner class `recent 개수가 부족한 경우` {

        @Test
        fun `recent 개수가 streak+1 미만이면(부팅 직후) 발송 안 함`() {
            every { runtime.current() } returns defaultSettings(streak = 3)
            val run = opsRun(id = "run-001", categoryId = "cat-001")
            // streak+1 = 4건 미만 — 단 2건만 반환
            every { runStore.findRecentByCategory("cat-001", 4) } returns listOf(
                modelRun("run-001", "cat-001", PipelineRunStatus.SUCCEEDED),
                modelRun("run-000", "cat-001", PipelineRunStatus.FAILED),
            )

            detector.onRunCompleted(run)

            verify(exactly = 0) { notifier.postPipelineRecovery(any(), any(), any()) }
        }

        @Test
        fun `recent가 정확히 streak+1건이면 복구 판정이 정상 동작한다`() {
            every { runtime.current() } returns defaultSettings(streak = 2)
            val run = opsRun(id = "run-003", categoryId = "cat-001")
            every { runStore.findRecentByCategory("cat-001", 3) } returns listOf(
                modelRun("run-003", "cat-001", PipelineRunStatus.SUCCEEDED),
                modelRun("run-002", "cat-001", PipelineRunStatus.FAILED),
                modelRun("run-001", "cat-001", PipelineRunStatus.FAILED),
            )

            detector.onRunCompleted(run)

            verify(exactly = 1) { notifier.postPipelineRecovery("cat-001", run, 2) }
        }
    }
}
