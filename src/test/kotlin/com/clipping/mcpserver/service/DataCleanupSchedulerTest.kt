package com.clipping.mcpserver.service

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerRunTracker
import com.clipping.mcpserver.store.AsyncJobStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.SummaryRetentionStore
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.LlmRunStore
import com.clipping.mcpserver.store.McpAuditLogStore
import com.clipping.mcpserver.store.OriginalContentStore
import com.clipping.mcpserver.store.ReportDeliveryLogStore
import com.clipping.mcpserver.store.ReviewItemAuditStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.SummaryCacheStore
import com.clipping.mcpserver.store.UserEventStore
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class DataCleanupSchedulerTest {

    private val asyncJobStore = mockk<AsyncJobStore>()
    private val llmRunStore = mockk<LlmRunStore>()
    private val originalContentStore = mockk<OriginalContentStore>()
    private val deliveryLogStore = mockk<DeliveryLogStore>()
    private val reportDeliveryLogStore = mockk<ReportDeliveryLogStore>()
    private val userEventStore = mockk<UserEventStore>()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val adminUserStore = mockk<com.clipping.mcpserver.store.AdminUserStore>(relaxed = true)
    private val summaryCacheStore = mockk<SummaryCacheStore>(relaxed = true)
    private val mcpAuditLogStore = mockk<McpAuditLogStore>(relaxed = true)
    private val reviewItemAuditStore = mockk<ReviewItemAuditStore>(relaxed = true)
    private val summaryRetentionStore = mockk<SummaryRetentionStore>(relaxed = true)
    private val rssItemStore = mockk<RssItemStore>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())

    /** RuntimeSettings 기본값 — 개별 테스트에서 override 가능. */
    private val defaultSettings = RuntimeSettingService.RuntimeSettings(
        defaultHoursBack = 24,
        summaryInputMaxChars = 4000,
        digestMinImportanceScore = 0.5f,
        digestDefaultMaxItems = 3,
        digestMaxMessageChars = 3000,
        digestItemSummaryMaxChars = 500,
        digestKeywordMaxCount = 5,
        jobWorkerBatchSize = 10,
        jobMaxAttempts = 3,
        jobInitialBackoffSeconds = 30,
        slackBotToken = "",
        slackDigestBlockKitTemplate = "",
        slackAutoDigestEnabled = false,
        slackDigestCron = "-",
        slackAutoDigestMaxItems = 3,
        slackAutoDigestUnsentOnly = true,
        slackDailyChannelMessageLimit = 10,
        retentionRssItemsDays = RuntimeSettingService.RETENTION_RSS_ITEMS_DAYS_DEFAULT,
        retentionBatchSummariesDays = RuntimeSettingService.RETENTION_BATCH_SUMMARIES_DAYS_DEFAULT,
        updatedAt = null
    )

    init {
        // 기본 설정값을 반환하도록 미리 stubbing — 개별 테스트에서 재정의 가능.
        every { runtimeSettingService.current() } returns defaultSettings
    }

    private val scheduler = DataCleanupScheduler(
        asyncJobStore = asyncJobStore,
        llmRunStore = llmRunStore,
        originalContentStore = originalContentStore,
        deliveryLogStore = deliveryLogStore,
        reportDeliveryLogStore = reportDeliveryLogStore,
        userEventStore = userEventStore,
        auditLogStore = auditLogStore,
        adminUserStore = adminUserStore,
        mcpAuditLogStore = mcpAuditLogStore,
        metrics = metrics,
        summaryCacheStore = summaryCacheStore,
        reviewItemAuditStore = reviewItemAuditStore,
        summaryRetentionStore = summaryRetentionStore,
        rssItemStore = rssItemStore,
        runtimeSettingService = runtimeSettingService,
        chunkPauseSleeper = { _, _ -> },
    )

    @Nested
    inner class `cleanup 정상 동작` {

        @Test
        fun `모든 store의 삭제 메서드를 호출한다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 5
            every { llmRunStore.deleteOlderThan(any()) } returns 10
            every { originalContentStore.deleteOlderThan(any()) } returns 3
            every { deliveryLogStore.deleteOlderThan(any()) } returns 1
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 2
            every { userEventStore.deleteOlderThan(any()) } returns 4

            scheduler.cleanup()

            verify(exactly = 1) { asyncJobStore.deleteCompletedOlderThan(any()) }
            verify(exactly = 1) { llmRunStore.deleteOlderThan(any()) }
            verify(exactly = 1) { originalContentStore.deleteOlderThan(any()) }
            verify(exactly = 1) { deliveryLogStore.deleteOlderThan(any()) }
            verify(exactly = 1) { reportDeliveryLogStore.deleteOlderThan(any()) }
            verify(exactly = 1) { userEventStore.deleteOlderThan(any()) }
        }

        @Test
        fun `cleanup 시 summary_cache 7일 TTL 삭제를 호출한다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 0
            every { llmRunStore.deleteOlderThan(any()) } returns 0
            every { originalContentStore.deleteOlderThan(any()) } returns 0
            every { deliveryLogStore.deleteOlderThan(any()) } returns 0
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 0
            every { userEventStore.deleteOlderThan(any()) } returns 0
            every { summaryCacheStore.deleteOlderThan(any()) } returns 5

            scheduler.cleanup()

            verify(exactly = 1) { summaryCacheStore.deleteOlderThan(any()) }
        }

        @Test
        fun `cutoff 시점이 각 보관 기간에 맞게 계산된다`() {
            val jobCutoff = slot<Instant>()
            val llmCutoff = slot<Instant>()
            val contentCutoff = slot<Instant>()
            val userEventCutoff = slot<Instant>()

            every { asyncJobStore.deleteCompletedOlderThan(capture(jobCutoff)) } returns 0
            every { llmRunStore.deleteOlderThan(capture(llmCutoff)) } returns 0
            every { originalContentStore.deleteOlderThan(capture(contentCutoff)) } returns 0
            every { deliveryLogStore.deleteOlderThan(any()) } returns 0
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 0
            every { userEventStore.deleteOlderThan(capture(userEventCutoff)) } returns 0

            val before = Instant.now()
            scheduler.cleanup()
            val after = Instant.now()

            // 비동기 작업: 7일 보관
            val jobCutoffValue = jobCutoff.captured
            jobCutoffValue.isBefore(before.minusSeconds(6 * 86400)) shouldBe true
            jobCutoffValue.isAfter(before.minusSeconds(8 * 86400)) shouldBe true

            // LLM 실행: 90일 보관
            val llmCutoffValue = llmCutoff.captured
            llmCutoffValue.isBefore(before.minusSeconds(89 * 86400)) shouldBe true
            llmCutoffValue.isAfter(before.minusSeconds(91 * 86400)) shouldBe true

            // 원본 콘텐츠: 30일 보관
            val contentCutoffValue = contentCutoff.captured
            contentCutoffValue.isBefore(before.minusSeconds(29 * 86400)) shouldBe true
            contentCutoffValue.isAfter(before.minusSeconds(31 * 86400)) shouldBe true

            // 사용자 행동 이벤트: 90일 보관
            val userEventCutoffValue = userEventCutoff.captured
            userEventCutoffValue.isBefore(after.minusSeconds(89 * 86400)) shouldBe true
            userEventCutoffValue.isAfter(before.minusSeconds(91 * 86400)) shouldBe true
        }
    }

    @Nested
    inner class `cleanup 에러 격리` {

        @Test
        fun `asyncJobStore 실패 시 나머지 store는 정상 호출된다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } throws RuntimeException("DB error")
            every { llmRunStore.deleteOlderThan(any()) } returns 10
            every { originalContentStore.deleteOlderThan(any()) } returns 3
            every { deliveryLogStore.deleteOlderThan(any()) } returns 1
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 1
            every { userEventStore.deleteOlderThan(any()) } returns 1

            scheduler.cleanup()

            verify(exactly = 1) { asyncJobStore.deleteCompletedOlderThan(any()) }
            verify(exactly = 1) { llmRunStore.deleteOlderThan(any()) }
            verify(exactly = 1) { originalContentStore.deleteOlderThan(any()) }
            verify(exactly = 1) { deliveryLogStore.deleteOlderThan(any()) }
            verify(exactly = 1) { reportDeliveryLogStore.deleteOlderThan(any()) }
            verify(exactly = 1) { userEventStore.deleteOlderThan(any()) }
        }

        @Test
        fun `llmRunStore 실패 시 나머지 store는 정상 호출된다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 5
            every { llmRunStore.deleteOlderThan(any()) } throws RuntimeException("DB error")
            every { originalContentStore.deleteOlderThan(any()) } returns 3
            every { deliveryLogStore.deleteOlderThan(any()) } returns 1
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 1
            every { userEventStore.deleteOlderThan(any()) } returns 1

            scheduler.cleanup()

            verify(exactly = 1) { asyncJobStore.deleteCompletedOlderThan(any()) }
            verify(exactly = 1) { llmRunStore.deleteOlderThan(any()) }
            verify(exactly = 1) { originalContentStore.deleteOlderThan(any()) }
            verify(exactly = 1) { deliveryLogStore.deleteOlderThan(any()) }
            verify(exactly = 1) { reportDeliveryLogStore.deleteOlderThan(any()) }
            verify(exactly = 1) { userEventStore.deleteOlderThan(any()) }
        }

        @Test
        fun `originalContentStore 실패 시 나머지 store는 정상 호출된다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 5
            every { llmRunStore.deleteOlderThan(any()) } returns 10
            every { originalContentStore.deleteOlderThan(any()) } throws RuntimeException("DB error")
            every { deliveryLogStore.deleteOlderThan(any()) } returns 1
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 1
            every { userEventStore.deleteOlderThan(any()) } returns 1

            scheduler.cleanup()

            verify(exactly = 1) { asyncJobStore.deleteCompletedOlderThan(any()) }
            verify(exactly = 1) { llmRunStore.deleteOlderThan(any()) }
            verify(exactly = 1) { originalContentStore.deleteOlderThan(any()) }
            verify(exactly = 1) { reportDeliveryLogStore.deleteOlderThan(any()) }
            verify(exactly = 1) { userEventStore.deleteOlderThan(any()) }
        }

        @Test
        fun `모든 store 실패해도 예외가 전파되지 않는다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } throws RuntimeException("error 1")
            every { llmRunStore.deleteOlderThan(any()) } throws RuntimeException("error 2")
            every { originalContentStore.deleteOlderThan(any()) } throws RuntimeException("error 3")
            every { deliveryLogStore.deleteOlderThan(any()) } throws RuntimeException("error 4")
            every { reportDeliveryLogStore.deleteOlderThan(any()) } throws RuntimeException("error 5")
            every { userEventStore.deleteOlderThan(any()) } throws RuntimeException("error 6")

            // 예외 없이 정상 종료되어야 한다
            scheduler.cleanup()

            verify(exactly = 1) { asyncJobStore.deleteCompletedOlderThan(any()) }
            verify(exactly = 1) { llmRunStore.deleteOlderThan(any()) }
            verify(exactly = 1) { originalContentStore.deleteOlderThan(any()) }
            verify(exactly = 1) { reportDeliveryLogStore.deleteOlderThan(any()) }
            verify(exactly = 1) { userEventStore.deleteOlderThan(any()) }
        }
    }

    @Nested
    inner class `MCP 감사 로그 정리` {

        @Test
        fun `cleanupMcpAuditLog가 90일 이전 로그를 삭제한다`() {
            val cutoff = slot<Instant>()
            every { mcpAuditLogStore.deleteOlderThan(capture(cutoff)) } returns 15

            val before = Instant.now()
            scheduler.cleanupMcpAuditLog()

            verify(exactly = 1) { mcpAuditLogStore.deleteOlderThan(any()) }

            // cutoff이 약 90일 전인지 검증한다
            val cutoffValue = cutoff.captured
            cutoffValue.isBefore(before.minusSeconds(89 * 86400)) shouldBe true
            cutoffValue.isAfter(before.minusSeconds(91 * 86400)) shouldBe true
        }

        @Test
        fun `mcpAuditLogStore 실패 시 예외가 전파되지 않는다`() {
            every { mcpAuditLogStore.deleteOlderThan(any()) } throws RuntimeException("DB error")

            // 예외 없이 정상 종료되어야 한다
            scheduler.cleanupMcpAuditLog()

            verify(exactly = 1) { mcpAuditLogStore.deleteOlderThan(any()) }
        }

        @Test
        fun `삭제 건수가 0이면 메트릭을 기록하지 않는다`() {
            every { mcpAuditLogStore.deleteOlderThan(any()) } returns 0

            scheduler.cleanupMcpAuditLog()

            verify(exactly = 1) { mcpAuditLogStore.deleteOlderThan(any()) }
        }
    }

    @Nested
    inner class `review_item_audits 3년 retention 정리` {

        @Test
        fun `15000건을 2 청크로 나눠 삭제하고 총 15000건을 반환한다`() {
            // 기본 mock 설정: 다른 store는 noop
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 0
            every { llmRunStore.deleteOlderThan(any()) } returns 0
            every { originalContentStore.deleteOlderThan(any()) } returns 0
            every { deliveryLogStore.deleteOlderThan(any()) } returns 0
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 0
            every { userEventStore.deleteOlderThan(any()) } returns 0

            // 첫 청크 10000건, 두 번째 청크 5000건, 세 번째는 0건 → 종료
            every {
                reviewItemAuditStore.deleteOlderThan(any(), 10_000)
            } returnsMany listOf(10_000, 5_000, 0)

            scheduler.cleanup()

            // 0건을 받고 종료하므로 총 3회 호출 (10000 + 5000 + 0)
            verify(exactly = 3) { reviewItemAuditStore.deleteOlderThan(any(), 10_000) }
        }

        @Test
        fun `정확히 10000건(1 청크)이면 2회 호출하여 0건으로 종료한다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 0
            every { llmRunStore.deleteOlderThan(any()) } returns 0
            every { originalContentStore.deleteOlderThan(any()) } returns 0
            every { deliveryLogStore.deleteOlderThan(any()) } returns 0
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 0
            every { userEventStore.deleteOlderThan(any()) } returns 0

            every {
                reviewItemAuditStore.deleteOlderThan(any(), 10_000)
            } returnsMany listOf(10_000, 0)

            scheduler.cleanup()

            verify(exactly = 2) { reviewItemAuditStore.deleteOlderThan(any(), 10_000) }
        }

        @Test
        fun `삭제 대상이 없으면 1회 호출 후 조기 종료한다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 0
            every { llmRunStore.deleteOlderThan(any()) } returns 0
            every { originalContentStore.deleteOlderThan(any()) } returns 0
            every { deliveryLogStore.deleteOlderThan(any()) } returns 0
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 0
            every { userEventStore.deleteOlderThan(any()) } returns 0

            every { reviewItemAuditStore.deleteOlderThan(any(), 10_000) } returns 0

            scheduler.cleanup()

            verify(exactly = 1) { reviewItemAuditStore.deleteOlderThan(any(), 10_000) }
        }

        @Test
        fun `cutoff 시점은 3년(1095일) 전으로 계산된다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 0
            every { llmRunStore.deleteOlderThan(any()) } returns 0
            every { originalContentStore.deleteOlderThan(any()) } returns 0
            every { deliveryLogStore.deleteOlderThan(any()) } returns 0
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 0
            every { userEventStore.deleteOlderThan(any()) } returns 0

            val cutoff = slot<Instant>()
            every { reviewItemAuditStore.deleteOlderThan(capture(cutoff), 10_000) } returns 0

            val before = Instant.now()
            scheduler.cleanup()

            val captured = cutoff.captured
            // 1094일 전보다 더 과거여야 함 (정확히 1095일)
            captured.isBefore(before.minusSeconds(1094L * 86400)) shouldBe true
            // 1096일 전보다는 최근이어야 함
            captured.isAfter(before.minusSeconds(1096L * 86400)) shouldBe true
        }

        @Test
        fun `reviewItemAuditStore 실패 시 예외가 전파되지 않는다`() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 0
            every { llmRunStore.deleteOlderThan(any()) } returns 0
            every { originalContentStore.deleteOlderThan(any()) } returns 0
            every { deliveryLogStore.deleteOlderThan(any()) } returns 0
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 0
            every { userEventStore.deleteOlderThan(any()) } returns 0

            every {
                reviewItemAuditStore.deleteOlderThan(any(), any())
            } throws RuntimeException("DB error")

            // 예외 없이 정상 종료되어야 한다 (run catching 격리)
            scheduler.cleanup()

            verify(atLeast = 1) { reviewItemAuditStore.deleteOlderThan(any(), 10_000) }
        }
    }

    @Nested
    inner class `데이터 retention` {

        /** 모든 non-relaxed store 를 0 반환으로 noop 설정. */
        private fun stubNonRetentionStoresAsNoop() {
            every { asyncJobStore.deleteCompletedOlderThan(any()) } returns 0
            every { llmRunStore.deleteOlderThan(any()) } returns 0
            every { originalContentStore.deleteOlderThan(any()) } returns 0
            every { deliveryLogStore.deleteOlderThan(any()) } returns 0
            every { reportDeliveryLogStore.deleteOlderThan(any()) } returns 0
            every { userEventStore.deleteOlderThan(any()) } returns 0
        }

        @Test
        fun `retention 설정 override — retentionBatchSummariesDays=120 이면 cutoff 이 120일 전이 된다`() {
            stubNonRetentionStoresAsNoop()
            every { runtimeSettingService.current() } returns defaultSettings.copy(retentionBatchSummariesDays = 120)
            val cutoff = slot<Instant>()
            every { summaryRetentionStore.deleteOlderThanExcludingAnchored(capture(cutoff), any()) } returns 0

            val before = Instant.now()
            scheduler.cleanup()

            // 120일 전 cutoff: 119일보다 과거, 121일보다는 최근
            cutoff.captured.isBefore(before.minusSeconds(119L * 86400)) shouldBe true
            cutoff.captured.isAfter(before.minusSeconds(121L * 86400)) shouldBe true
        }

        @Test
        fun `default fallback — 설정 없으면 batch_summaries 90일, rss_items 30일 기본값 적용`() {
            stubNonRetentionStoresAsNoop()
            val bsCutoff = slot<Instant>()
            val rssCutoff = slot<Instant>()
            every { summaryRetentionStore.deleteOlderThanExcludingAnchored(capture(bsCutoff), any()) } returns 0
            every { rssItemStore.deleteOlderThan(capture(rssCutoff), any(), any()) } returns 0

            val before = Instant.now()
            scheduler.cleanup()

            // batch_summaries: 기본 90일
            bsCutoff.captured.isBefore(before.minusSeconds(89L * 86400)) shouldBe true
            bsCutoff.captured.isAfter(before.minusSeconds(91L * 86400)) shouldBe true

            // rss_items: 기본 30일
            rssCutoff.captured.isBefore(before.minusSeconds(29L * 86400)) shouldBe true
            rssCutoff.captured.isAfter(before.minusSeconds(31L * 86400)) shouldBe true
        }

        @Test
        fun `ordering — batch_summaries 가 rss_items 보다 먼저 호출된다`() {
            stubNonRetentionStoresAsNoop()
            every { summaryRetentionStore.deleteOlderThanExcludingAnchored(any(), any()) } returns 0
            every { rssItemStore.deleteOlderThan(any(), any(), any()) } returns 0

            scheduler.cleanup()

            verifyOrder {
                summaryRetentionStore.deleteOlderThanExcludingAnchored(any(), any())
                rssItemStore.deleteOlderThan(any(), any(), any())
            }
        }

        @Test
        fun `chunked delete — store 가 CHUNK_SIZE 만큼 반환하면 재호출, 0 반환 시 중단`() {
            stubNonRetentionStoresAsNoop()
            // 첫 청크 10000건, 두 번째 5000건, 세 번째 0건 → 중단
            every {
                summaryRetentionStore.deleteOlderThanExcludingAnchored(any(), DataCleanupScheduler.BATCH_SUMMARIES_CHUNK_SIZE)
            } returnsMany listOf(10_000, 5_000, 0)
            every { rssItemStore.deleteOlderThan(any(), any(), DataCleanupScheduler.RSS_ITEMS_CHUNK_SIZE) } returns 0

            scheduler.cleanup()

            // 0건을 받고 종료하므로 3회 호출 (10000 + 5000 + 0)
            verify(exactly = 3) {
                summaryRetentionStore.deleteOlderThanExcludingAnchored(any(), DataCleanupScheduler.BATCH_SUMMARIES_CHUNK_SIZE)
            }
        }

        @Test
        fun `MAX_CHUNKS 도달 — 경고 로그 후 현재 cycle 종료 (더 이상 호출하지 않는다)`() {
            stubNonRetentionStoresAsNoop()
            // 항상 CHUNK_SIZE 반환 → MAX_CHUNKS 에 도달
            every {
                summaryRetentionStore.deleteOlderThanExcludingAnchored(any(), DataCleanupScheduler.BATCH_SUMMARIES_CHUNK_SIZE)
            } returns DataCleanupScheduler.BATCH_SUMMARIES_CHUNK_SIZE
            every { rssItemStore.deleteOlderThan(any(), any(), any()) } returns 0

            // MAX_CHUNKS 도달 후 루프 종료 — 예외 없이 정상 종료
            scheduler.cleanup()

            verify(exactly = DataCleanupScheduler.BATCH_SUMMARIES_MAX_CHUNKS) {
                summaryRetentionStore.deleteOlderThanExcludingAnchored(any(), DataCleanupScheduler.BATCH_SUMMARIES_CHUNK_SIZE)
            }
        }

        @Test
        fun `metrics 기록 — recordRetentionRun 이 batch_summaries 와 rss_items 각각에 대해 호출된다`() {
            stubNonRetentionStoresAsNoop()
            every { summaryRetentionStore.deleteOlderThanExcludingAnchored(any(), any()) } returns 0
            every { rssItemStore.deleteOlderThan(any(), any(), any()) } returns 0

            // metrics를 spy로 감싸서 실제 메서드 호출을 검증
            val metricsSpy = io.mockk.spyk(metrics)
            val schedulerWithSpyMetrics = DataCleanupScheduler(
                asyncJobStore = asyncJobStore,
                llmRunStore = llmRunStore,
                originalContentStore = originalContentStore,
                deliveryLogStore = deliveryLogStore,
                reportDeliveryLogStore = reportDeliveryLogStore,
                userEventStore = userEventStore,
                auditLogStore = auditLogStore,
                adminUserStore = adminUserStore,
                mcpAuditLogStore = mcpAuditLogStore,
                metrics = metricsSpy,
                summaryCacheStore = summaryCacheStore,
                reviewItemAuditStore = reviewItemAuditStore,
                summaryRetentionStore = summaryRetentionStore,
                rssItemStore = rssItemStore,
                runtimeSettingService = runtimeSettingService,
                chunkPauseSleeper = { _, _ -> },
            )

            schedulerWithSpyMetrics.cleanup()

            // recordRetentionRun 이 "batch_summaries" 와 "rss_items" 각각에 대해 호출되었음을 검증
            verify { metricsSpy.recordRetentionRun("batch_summaries", any(), any()) }
            verify { metricsSpy.recordRetentionRun("rss_items", any(), any()) }
        }

        @Test
        fun `store 예외 — 한 테이블 실패해도 다른 테이블은 계속 진행한다`() {
            stubNonRetentionStoresAsNoop()
            // batch_summaries 는 실패
            every {
                summaryRetentionStore.deleteOlderThanExcludingAnchored(any(), any())
            } throws RuntimeException("batch_summaries DB error")
            // rss_items 는 정상
            every { rssItemStore.deleteOlderThan(any(), any(), any()) } returns 0

            // 예외 없이 정상 종료 (runCatching 격리)
            scheduler.cleanup()

            // rss_items 도 호출되어야 한다
            verify(atLeast = 1) { rssItemStore.deleteOlderThan(any(), any(), any()) }
        }
    }
}
