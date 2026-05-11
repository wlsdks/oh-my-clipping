package com.clipping.mcpserver.service.analytics

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.service.AuditActorResolver
import com.clipping.mcpserver.service.ResolvedActor
import com.clipping.mcpserver.store.analytics.dto.PersonaBatchRun
import com.clipping.mcpserver.store.analytics.dto.TriggerType
import com.clipping.mcpserver.service.analytics.steps.WeeklyPersonaSnapshotStep
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.PersonaAnalyticsStore
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
import java.time.LocalDate

class PersonaAnalyticsBackfillServiceTest {

    private lateinit var snapshotStep: WeeklyPersonaSnapshotStep
    private lateinit var analyticsStore: PersonaAnalyticsStore
    private lateinit var auditLogStore: AuditLogStore
    private lateinit var service: PersonaAnalyticsBackfillService

    private val adminUserId = "admin-test-user"

    @BeforeEach
    fun setUp() {
        snapshotStep = mockk()
        analyticsStore = mockk()
        auditLogStore = mockk()

        justRun { analyticsStore.insertBatchRun(any()) }
        justRun { analyticsStore.finalizeBatchRun(any(), any(), any(), any()) }
        justRun { auditLogStore.log(any(), any(), any(), any(), any(), any(), any()) }

        // backfill 성공/실패 finally 블록에서 캐시를 비우므로 relaxed mock 으로 주입한다.
        val cacheManager = mockk<org.springframework.cache.CacheManager>(relaxed = true)
        // Principal → actorId passthrough: auditLogStore.log(actorId = adminUserId, ...) 검증이 동작하도록 한다.
        val auditActorResolver = mockk<AuditActorResolver>().apply {
            every { resolve(any()) } answers {
                val arg = firstArg<String?>()
                ResolvedActor(id = arg, name = arg ?: "system")
            }
        }
        service = PersonaAnalyticsBackfillService(snapshotStep, analyticsStore, auditLogStore, cacheManager, auditActorResolver)
    }

    @Nested
    inner class `정상 백필 실행` {

        @Test
        fun `backfill 12주 요청 시 snapshotStep 12번 호출`() {
            // snapshotStep 이 매번 3개 페르소나를 반환하도록 설정한다.
            every { snapshotStep.execute(any<LocalDate>(), any()) } returns 3

            service.backfill(12, adminUserId)

            verify(exactly = 12) { snapshotStep.execute(any(), any()) }
        }

        @Test
        fun `backfill 결과에 총 rowCount 포함`() {
            // 각 주 실행마다 4개 페르소나를 반환하도록 설정한다.
            every { snapshotStep.execute(any<LocalDate>(), any()) } returns 4

            val result = service.backfill(3, adminUserId)

            // 3주 × 4 = 12 행이 생성되어야 한다.
            assertThat(result.snapshotRowsCreated).isEqualTo(12)
            assertThat(result.weeksProcessed).isEqualTo(3)
        }
    }

    @Nested
    inner class `weeks 범위 검증` {

        @Test
        fun `weeks 가 0 이면 InvalidInputException`() {
            assertThatThrownBy { service.backfill(0, adminUserId) }
                .isInstanceOf(InvalidInputException::class.java)
                .hasMessageContaining("weeks")
        }

        @Test
        fun `weeks 가 53 이면 InvalidInputException`() {
            assertThatThrownBy { service.backfill(53, adminUserId) }
                .isInstanceOf(InvalidInputException::class.java)
                .hasMessageContaining("weeks")
        }
    }

    @Nested
    inner class `감사 로그 기록` {

        @Test
        fun `audit_log 에 actor 와 metadata 가 기록된다`() {
            every { snapshotStep.execute(any<LocalDate>(), any()) } returns 2

            service.backfill(2, adminUserId)

            // actorId 와 action 이 올바르게 기록되어야 한다.
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = adminUserId,
                    actorName = adminUserId,
                    action = "PERSONA_ANALYTICS_BACKFILL",
                    targetType = "PERSONA_BATCH_RUN",
                    targetId = any(),
                    detail = match { detail -> detail.contains("weeks=2") && detail.contains("rowsCreated=4") },
                    targetName = null
                )
            }
        }

        @Test
        fun `principal 이 null 이면 actor_name=system 으로 기록된다`() {
            // 스케줄러나 시스템 호출처럼 SecurityContext 가 비어있는 상황.
            every { snapshotStep.execute(any<LocalDate>(), any()) } returns 1

            service.backfill(1, null)

            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = null,
                    actorName = "system",
                    action = "PERSONA_ANALYTICS_BACKFILL",
                    targetType = "PERSONA_BATCH_RUN",
                    targetId = any(),
                    detail = any(),
                    targetName = null
                )
            }
        }
    }

    @Nested
    inner class `persona_batch_run 기록` {

        @Test
        fun `persona_batch_run 에 trigger_type = BACKFILL 로 기록된다`() {
            every { snapshotStep.execute(any<LocalDate>(), any()) } returns 1
            val runSlot = slot<PersonaBatchRun>()
            // insertBatchRun 호출 시 인자를 캡처한다.
            every { analyticsStore.insertBatchRun(capture(runSlot)) } returns Unit

            service.backfill(1, adminUserId)

            assertThat(runSlot.captured.triggerType).isEqualTo(TriggerType.BACKFILL)
            assertThat(runSlot.captured.triggeredBy).isEqualTo(adminUserId)
            assertThat(runSlot.captured.overallStatus).isEqualTo("RUNNING")
        }

        @Test
        fun `백필 완료 후 finalizeBatchRun 이 SUCCESS 로 호출된다`() {
            every { snapshotStep.execute(any<LocalDate>(), any()) } returns 1

            service.backfill(1, adminUserId)

            verify(exactly = 1) {
                analyticsStore.finalizeBatchRun(any(), any(), "SUCCESS", null)
            }
        }
    }
}
