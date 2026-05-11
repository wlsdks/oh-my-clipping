package com.ohmyclipping.service.analytics

import com.ohmyclipping.store.analytics.dto.PersonaBatchRun
import com.ohmyclipping.store.analytics.dto.TriggerType
import com.ohmyclipping.service.analytics.exception.BatchAlreadyRunningException
import com.ohmyclipping.service.analytics.steps.WeeklyPersonaSnapshotStep
import com.ohmyclipping.store.PersonaAnalyticsStore
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.dao.DataAccessException
import java.time.LocalDate

class PersonaAnalyticsMondayBatchTest {

    private lateinit var snapshotStep: WeeklyPersonaSnapshotStep
    private lateinit var analyticsStore: PersonaAnalyticsStore
    private lateinit var cacheManager: CacheManager
    private lateinit var personaLiveCache: Cache
    private lateinit var personaTrendsCache: Cache
    private lateinit var batch: PersonaAnalyticsMondayBatch

    private val weekStart = LocalDate.of(2026, 3, 30)

    @BeforeEach
    fun setUp() {
        snapshotStep = mockk()
        analyticsStore = mockk()
        cacheManager = mockk()
        personaLiveCache = mockk()
        personaTrendsCache = mockk()

        every { cacheManager.getCache("persona-live") } returns personaLiveCache
        every { cacheManager.getCache("persona-trends") } returns personaTrendsCache
        // persona-signals 캐시는 V스펙 §4.3 에서 추가되어 배치 종료 시에도 clear 된다.
        every { cacheManager.getCache("persona-signals") } returns mockk(relaxed = true)
        justRun { personaLiveCache.clear() }
        justRun { personaTrendsCache.clear() }

        batch = PersonaAnalyticsMondayBatch(snapshotStep, analyticsStore, cacheManager)
    }

    @Nested
    inner class `정상 실행` {

        @Test
        fun `정상 실행 시 persona_batch_run 상태가 SUCCESS 로 전이한다`() {
            // RUNNING 상태 배치 없음을 반환한다.
            every { analyticsStore.hasRunningBatch(weekStart) } returns false
            justRun { analyticsStore.insertBatchRun(any()) }
            every { snapshotStep.execute(weekStart, any()) } returns 5
            justRun { analyticsStore.updateStepStatus(any(), any(), any()) }
            justRun { analyticsStore.finalizeBatchRun(any(), any(), "SUCCESS", any()) }

            val result = batch.run(weekStart, TriggerType.SCHEDULED, null)

            assertThat(result.overallStatus).isEqualTo("SUCCESS")
            assertThat(result.runId).isNotBlank()
            verify(exactly = 1) { analyticsStore.finalizeBatchRun(any(), any(), "SUCCESS", any()) }
        }

        @Test
        fun `MANUAL 트리거 시 triggered_by 가 기록된다`() {
            every { analyticsStore.hasRunningBatch(weekStart) } returns false
            val runSlot = slot<PersonaBatchRun>()
            every { analyticsStore.insertBatchRun(capture(runSlot)) } returns Unit
            every { snapshotStep.execute(weekStart, any()) } returns 3
            justRun { analyticsStore.updateStepStatus(any(), any(), any()) }
            justRun { analyticsStore.finalizeBatchRun(any(), any(), "SUCCESS", any()) }

            batch.run(weekStart, TriggerType.MANUAL, "admin@clipping.io")

            assertThat(runSlot.captured.triggeredBy).isEqualTo("admin@clipping.io")
            assertThat(runSlot.captured.triggerType).isEqualTo(TriggerType.MANUAL)
        }

        @Test
        fun `Step 2~4 는 SKIPPED 로 기록된다 (Slice 2 범위)`() {
            every { analyticsStore.hasRunningBatch(weekStart) } returns false
            justRun { analyticsStore.insertBatchRun(any()) }
            every { snapshotStep.execute(weekStart, any()) } returns 2
            justRun { analyticsStore.updateStepStatus(any(), any(), any()) }
            justRun { analyticsStore.finalizeBatchRun(any(), any(), "SUCCESS", any()) }

            batch.run(weekStart, TriggerType.SCHEDULED, null)

            verify(exactly = 1) { analyticsStore.updateStepStatus(any(), "SNAPSHOT", "SUCCESS") }
            verify(exactly = 1) { analyticsStore.updateStepStatus(any(), "ANOMALY", "SKIPPED") }
            verify(exactly = 1) { analyticsStore.updateStepStatus(any(), "CLUSTERING", "SKIPPED") }
            verify(exactly = 1) { analyticsStore.updateStepStatus(any(), "REPORT", "SKIPPED") }
        }
    }

    @Nested
    inner class `실패 시나리오` {

        @Test
        fun `Step 1 DataAccessException 발생 시 overall FAILED 로 마무리한다`() {
            every { analyticsStore.hasRunningBatch(weekStart) } returns false
            justRun { analyticsStore.insertBatchRun(any()) }
            every { snapshotStep.execute(weekStart, any()) } throws object : DataAccessException("DB 연결 오류") {}
            justRun { analyticsStore.finalizeBatchRun(any(), any(), "FAILED", any()) }

            val result = batch.run(weekStart, TriggerType.SCHEDULED, null)

            assertThat(result.overallStatus).isEqualTo("FAILED")
            verify(exactly = 1) { analyticsStore.finalizeBatchRun(any(), any(), "FAILED", match { it.contains("DB:") }) }
        }

        @Test
        fun `같은 주차에 RUNNING 상태 배치가 있으면 BatchAlreadyRunningException`() {
            every { analyticsStore.hasRunningBatch(weekStart) } returns true

            assertThatThrownBy { batch.run(weekStart, TriggerType.SCHEDULED, null) }
                .isInstanceOf(BatchAlreadyRunningException::class.java)
                .hasMessageContaining(weekStart.toString())

            // insertBatchRun 이 호출되지 않아야 한다.
            verify(exactly = 0) { analyticsStore.insertBatchRun(any()) }
        }
    }

    @Nested
    inner class `캐시 clear` {

        @Test
        fun `finally 블록에서 persona-live 와 persona-trends 캐시를 모두 clear 한다`() {
            every { analyticsStore.hasRunningBatch(weekStart) } returns false
            justRun { analyticsStore.insertBatchRun(any()) }
            every { snapshotStep.execute(weekStart, any()) } returns 1
            justRun { analyticsStore.updateStepStatus(any(), any(), any()) }
            justRun { analyticsStore.finalizeBatchRun(any(), any(), "SUCCESS", any()) }

            batch.run(weekStart, TriggerType.SCHEDULED, null)

            // 성공 경로에서도 캐시가 clear 되어야 한다.
            verify(exactly = 1) { personaLiveCache.clear() }
            verify(exactly = 1) { personaTrendsCache.clear() }
        }
    }
}
