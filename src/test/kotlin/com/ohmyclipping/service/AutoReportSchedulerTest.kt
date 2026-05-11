package com.ohmyclipping.service

import com.ohmyclipping.model.TrendPeriodType
import com.ohmyclipping.model.TrendRegionType
import com.ohmyclipping.model.TrendSnapshotStatus
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.competitor.CompetitorWatchlistService
import com.ohmyclipping.service.port.SlackDeliveryResult
import com.ohmyclipping.service.dto.ReportSettingsResponse
import com.ohmyclipping.store.ReportDeliveryLogStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class AutoReportSchedulerTest {

    private val reportSettingsService = mockk<ReportSettingsService>()
    private val snapshotService = mockk<AdminTrendSnapshotService>()
    private val keywordTrendService = mockk<KeywordTrendService>(relaxed = true)
    private val competitorWatchlistService = mockk<CompetitorWatchlistService>(relaxed = true)
    private val slackMessageSender = mockk<com.ohmyclipping.service.port.SlackDeliveryPort>(relaxed = true)
    private val keywordAlertService = mockk<KeywordAlertService>(relaxed = true)
    private val reportDeliveryLogStore = mockk<ReportDeliveryLogStore>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>().also {
        every { it.current() } returns mockk(relaxed = true) {
            every { maintenanceMode } returns false
        }
    }

    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }

    private val scheduler = AutoReportScheduler(
        reportSettingsService,
        snapshotService,
        keywordTrendService,
        competitorWatchlistService,
        slackMessageSender,
        keywordAlertService,
        reportDeliveryLogStore,
        runtimeSettingService,
        metrics
    )

    private fun schedulerWith(store: ReportDeliveryLogStore): AutoReportScheduler {
        return AutoReportScheduler(
            reportSettingsService,
            snapshotService,
            keywordTrendService,
            competitorWatchlistService,
            slackMessageSender,
            keywordAlertService,
            store,
            runtimeSettingService,
            metrics
        )
    }

    private fun defaultSettings(
        weeklyEnabled: Boolean = false,
        weeklyDay: String = "MONDAY",
        weeklyHour: Int = 9,
        weeklySlackChannelId: String? = "#test-channel",
        monthlyEnabled: Boolean = false,
        monthlyHour: Int = 9,
        monthlySlackChannelId: String? = "#test-monthly"
    ) = ReportSettingsResponse(
        weeklyEnabled = weeklyEnabled,
        weeklyDay = weeklyDay,
        weeklyHour = weeklyHour,
        weeklySlackChannelId = weeklySlackChannelId,
        weeklyIncludeKeywordTrend = false,
        weeklyIncludeCompetitor = false,
        weeklyIncludeTopArticles = true,
        weeklyIncludeSentiment = false,
        monthlyEnabled = monthlyEnabled,
        monthlyHour = monthlyHour,
        monthlySlackChannelId = monthlySlackChannelId
    )

    private fun snapshotResult(
        id: String = "snapshot-1",
        title: String = "주간 시장 요약"
    ) = TrendSnapshotResult(
        id = id,
        periodType = TrendPeriodType.WEEKLY,
        snapshotFrom = LocalDate.of(2026, 3, 16),
        snapshotTo = LocalDate.of(2026, 3, 22),
        categoryId = null,
        categoryName = "전체",
        regionType = TrendRegionType.ALL,
        title = title,
        summary = "핵심 변화 요약",
        keySignals = listOf("AI", "반도체"),
        actionItems = listOf("메시지 정비"),
        sourceCount = 5,
        itemCount = 12,
        status = TrendSnapshotStatus.DRAFT,
        templateType = "DETAILED",
        generatedBy = "auto-scheduler",
        publishedAt = null,
        createdAt = Instant.parse("2026-03-22T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-22T00:00:00Z")
    )

    @Nested
    inner class `요일 매칭` {

        @Test
        fun `설정 요일과 시간이 일치하면 true`() {
            scheduler.matchesWeekly("MONDAY", 9, DayOfWeek.MONDAY, 9) shouldBe true
        }

        @Test
        fun `요일이 다르면 false`() {
            scheduler.matchesWeekly("MONDAY", 9, DayOfWeek.TUESDAY, 9) shouldBe false
        }

        @Test
        fun `시간이 다르면 false`() {
            scheduler.matchesWeekly("MONDAY", 9, DayOfWeek.MONDAY, 10) shouldBe false
        }

        @Test
        fun `유효하지 않은 요일 문자열이면 false`() {
            scheduler.matchesWeekly("FUNDAY", 9, DayOfWeek.MONDAY, 9) shouldBe false
        }
    }

    @Nested
    inner class `월간 매칭` {

        @Test
        fun `1일 해당 시간이면 true`() {
            scheduler.matchesMonthly(9, LocalDate.of(2026, 3, 1), 9) shouldBe true
        }

        @Test
        fun `1일이 아니면 false`() {
            scheduler.matchesMonthly(9, LocalDate.of(2026, 3, 2), 9) shouldBe false
        }
    }

    @Nested
    inner class `period key 계산` {

        @Test
        fun `주간 period key는 ISO week 기준으로 계산한다`() {
            scheduler.weeklyPeriodKey(LocalDate.of(2025, 12, 29)) shouldBe "2026-W01"
        }

        @Test
        fun `월간 period key는 연월 기준으로 계산한다`() {
            scheduler.monthlyPeriodKey(LocalDate.of(2026, 3, 1)) shouldBe "2026-03"
        }
    }

    @Nested
    inner class `스케줄 발송 정책` {

        @Test
        fun `weeklyEnabled가 false이면 스냅샷을 생성하지 않는다`() {
            every { reportSettingsService.getSettings() } returns defaultSettings(weeklyEnabled = false)

            scheduler.tick(LocalDateTime.of(2026, 3, 23, 9, 0))

            verify(exactly = 0) { snapshotService.runSnapshot(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `같은 주간 슬롯은 재시작 후에도 한 번만 발송한다`() {
            val persistentStore = InMemoryReportDeliveryLogStore()
            val firstScheduler = schedulerWith(persistentStore)
            val secondScheduler = schedulerWith(persistentStore)
            val now = LocalDateTime.of(2026, 3, 23, 9, 0)

            every { reportSettingsService.getSettings() } returns defaultSettings(weeklyEnabled = true)
            every { snapshotService.runSnapshot(any(), any(), any(), any(), any()) } returns snapshotResult()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "1712.123", channelId = "C-any")

            firstScheduler.tick(now)
            secondScheduler.tick(now)

            verify(exactly = 1) { snapshotService.runSnapshot(any(), any(), any(), any(), any()) }
            verify(exactly = 1) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `주간 발송 실패는 FAILED 로그로 남긴다`() {
            val now = LocalDateTime.of(2026, 3, 23, 9, 0)

            every { reportSettingsService.getSettings() } returns defaultSettings(weeklyEnabled = true)
            every { reportDeliveryLogStore.tryReserve("WEEKLY", "2026-W13", "#test-channel") } returns "log-1"
            every { snapshotService.runSnapshot(any(), any(), any(), any(), any()) } returns snapshotResult()
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } throws
                IllegalStateException("slack down")

            scheduler.tick(now)

            verify {
                // durationMs는 실행 시간에 따라 값이 달라지므로 any() 매처로 완화한다
                reportDeliveryLogStore.markFailed("log-1", "snapshot-1", "slack down", any())
            }
        }

        @Test
        fun `월간 발송 성공은 SENT 로그로 남긴다`() {
            val now = LocalDateTime.of(2026, 3, 1, 9, 0)

            every {
                reportSettingsService.getSettings()
            } returns defaultSettings(monthlyEnabled = true, monthlyHour = 9)
            every { reportDeliveryLogStore.tryReserve("MONTHLY", "2026-03", "#test-monthly") } returns "log-2"
            every { snapshotService.runSnapshot(any(), any(), any(), any(), any()) } returns snapshotResult(
                id = "snapshot-2",
                title = "월간 시장 요약"
            )
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "1818.111", channelId = "C-any")

            scheduler.tick(now)

            // durationMs/itemsProcessed는 실행 시간/개수에 따라 달라져 any()로 완화한다
            verify {
                reportDeliveryLogStore.markSent("log-2", "snapshot-2", "1818.111", any(), any())
            }
        }
    }
}

private class InMemoryReportDeliveryLogStore : ReportDeliveryLogStore {

    private val reservedKeys = mutableSetOf<String>()

    override fun tryReserve(
        reportType: String,
        periodKey: String,
        channelId: String
    ): String? {
        val key = "$reportType|$periodKey|$channelId"
        return if (reservedKeys.add(key)) UUID.randomUUID().toString() else null
    }

    override fun markSent(
        id: String,
        snapshotId: String,
        slackMessageTs: String?,
        durationMs: Long?,
        itemsProcessed: Int?
    ) = Unit

    override fun markFailed(
        id: String,
        snapshotId: String?,
        errorMessage: String,
        durationMs: Long?
    ) = Unit

    override fun findFailedByTypeAndPeriod(
        reportType: String,
        periodKey: String
    ): List<ReportDeliveryLogStore.FailedDeliverySlot> = emptyList()

    override fun deleteOlderThan(days: Int): Int = 0

    override fun findByKey(
        reportType: String,
        periodKey: String,
        channelId: String
    ): ReportDeliveryLogStore.SlotInfo? = null

    override fun deleteById(id: String) = Unit

    override fun listHistory(reportType: String?, limit: Int): List<ReportDeliveryLogStore.HistoryEntry> = emptyList()
}
