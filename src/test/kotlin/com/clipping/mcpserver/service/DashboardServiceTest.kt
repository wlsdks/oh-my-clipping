package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.NotificationSeverity
import com.clipping.mcpserver.service.port.DailyForecast
import com.clipping.mcpserver.service.port.RiskSourceSummary
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.DailyFeedbackCount
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.PersonaStore
import com.clipping.mcpserver.store.PipelineRunStore
import com.clipping.mcpserver.store.UserEventStore
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

class DashboardServiceTest {

    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

    private val dailyOpsForecastScheduler = mockk<DailyOpsForecastScheduler>()
    private val autoCollectionScheduler = mockk<AutoCollectionScheduler>()
    private val analyticsService = mockk<AnalyticsService>()
    private val userEventStore = mockk<UserEventStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val personaStore = mockk<PersonaStore>()
    private val deliveryLogStore = mockk<DeliveryLogStore>()
    private val pipelineRunStore = mockk<PipelineRunStore>()

    private fun serviceWithClock(clock: Clock) = DashboardService(
        dailyOpsForecastScheduler = dailyOpsForecastScheduler,
        autoCollectionScheduler = autoCollectionScheduler,
        analyticsService = analyticsService,
        userEventStore = userEventStore,
        categoryStore = categoryStore,
        personaStore = personaStore,
        deliveryLogStore = deliveryLogStore,
        pipelineRunStore = pipelineRunStore,
        clock = clock,
    )

    @Nested
    inner class `todayForecast` {

        @Test
        fun `예측 데이터를 조합하여 TodayForecastResult를 반환한다`() {
            // 2026-04-18 10:00:00 KST (= 01:00:00 UTC)
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
            val service = serviceWithClock(clock)

            val fakeForecast = DailyForecast(
                forecastDate = LocalDate.of(2026, 4, 18),
                expectedRunCount = 24,
                expectedDigestCount = 150,
                llmMonthlyUsageKrw = 10_000L,
                llmMonthlyBudgetKrw = 100_000L,
                llmProjectedMonthEndKrw = 20_000L,
                riskSources = emptyList<RiskSourceSummary>(),
                severity = NotificationSeverity.INFO,
            )

            every { dailyOpsForecastScheduler.buildForecast(any(), any()) } returns fakeForecast
            // 크론: 매시간 5분에 실행 — 다음 실행은 10:05 KST
            every { autoCollectionScheduler.cronExpression() } returns "0 5 * * * *"

            val result = service.todayForecast()

            result.expectedRunCount shouldBe 24
            result.expectedDigestCount shouldBe 150
            // nextRunAtKst는 ISO-8601 오프셋 형식이어야 한다
            result.nextRunAtKst shouldNotBe null
            result.nextRunAtKst.contains("+09:00") shouldBe true
        }
    }

    @Nested
    inner class `engagementTrend` {

        @Test
        fun `7일 클릭률로 평균·표준편차를 올바르게 계산한다`() {
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, seoulZone)
            val service = serviceWithClock(clock)

            val rates = listOf(10.0, 12.0, 14.0, 15.0, 13.0, 11.0, 16.0)
            val yesterday = LocalDate.of(2026, 4, 17)
            val days = (0L until 7L).map { offset -> yesterday.minusDays(offset) }
            every { analyticsService.getClickRatesForDays(days) } returns days.zip(rates).toMap()
            every { userEventStore.countFeedbackByDay(yesterday) } returns DailyFeedbackCount(positive = 30L, negative = 5L)

            val result = service.engagementTrend()

            result.yesterdayClickRate shouldBe 10.0
            val expectedAvg = rates.average()
            result.sevenDayAvgClickRate shouldBe (expectedAvg plusOrMinus 0.01)

            // 표본 표준편차 검증
            val variance = rates.sumOf { (it - expectedAvg) * (it - expectedAvg) } / (rates.size - 1)
            val expectedStd = sqrt(variance)
            result.sevenDayStdDev shouldBe (expectedStd plusOrMinus 0.01)

            result.feedbackPositiveYesterday shouldBe 30L
            result.feedbackNegativeYesterday shouldBe 5L
        }

        @Test
        fun `클릭 이벤트가 없는 날은 클릭률이 0이다`() {
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, seoulZone)
            val service = serviceWithClock(clock)

            val yesterday = LocalDate.of(2026, 4, 17)
            val days = (0L until 7L).map { offset -> yesterday.minusDays(offset) }
            every { analyticsService.getClickRatesForDays(days) } returns days.associateWith { 0.0 }
            every { userEventStore.countFeedbackByDay(yesterday) } returns DailyFeedbackCount(0L, 0L)

            val result = service.engagementTrend()

            result.yesterdayClickRate shouldBe 0.0
            result.sevenDayAvgClickRate shouldBe 0.0
        }

        @Test
        fun `7일 클릭률을 일별 반복 호출하지 않고 범위 집계로 조회한다`() {
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, seoulZone)
            val service = serviceWithClock(clock)

            val yesterday = LocalDate.of(2026, 4, 17)
            val days = (0L until 7L).map { offset -> yesterday.minusDays(offset) }
            every { analyticsService.getClickRatesForDays(days) } returns days.associateWith { 0.0 }
            every { userEventStore.countFeedbackByDay(yesterday) } returns DailyFeedbackCount(0L, 0L)

            service.engagementTrend()

            verify(exactly = 1) { analyticsService.getClickRatesForDays(days) }
            verify(exactly = 0) { analyticsService.getClickRateForDay(any()) }
        }
    }

    @Nested
    inner class `activeSubscriptionsSummary` {

        @Test
        fun `활성 구독 수와 신규·비활성화 건수를 조합하여 반환한다`() {
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
            val service = serviceWithClock(clock)

            every { personaStore.countTotalActiveSubscriptions() } returns 500L
            every { categoryStore.countNewSince(any()) } returns 12L
            every { categoryStore.countDeactivatedSince(any()) } returns 3L

            val result = service.activeSubscriptionsSummary()

            result.activeCount shouldBe 500L
            result.newThisWeek shouldBe 12L
            result.deactivatedThisWeek shouldBe 3L
            result.netChange shouldBe 9L  // 12 - 3
        }

        @Test
        fun `신규와 비활성화가 동일하면 netChange가 0이다`() {
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
            val service = serviceWithClock(clock)

            every { personaStore.countTotalActiveSubscriptions() } returns 200L
            every { categoryStore.countNewSince(any()) } returns 5L
            every { categoryStore.countDeactivatedSince(any()) } returns 5L

            val result = service.activeSubscriptionsSummary()

            result.netChange shouldBe 0L
        }

        @Test
        fun `비활성화가 더 많으면 netChange가 음수이다`() {
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
            val service = serviceWithClock(clock)

            every { personaStore.countTotalActiveSubscriptions() } returns 100L
            every { categoryStore.countNewSince(any()) } returns 2L
            every { categoryStore.countDeactivatedSince(any()) } returns 10L

            val result = service.activeSubscriptionsSummary()

            result.netChange shouldBe -8L
        }
    }

    @Nested
    inner class `getOpsSummary` {

        @Test
        fun `delivery 와 pipeline 을 status 별로 합쳐 반환한다`() {
            // 2026-04-18 10:00 KST (= 01:00 UTC)
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
            val service = serviceWithClock(clock)

            every { deliveryLogStore.countByStatusOn(any()) } returns mapOf(
                "SENT" to 42L,
                "FAILED" to 3L,
                "SKIPPED" to 1L,
            )
            every { pipelineRunStore.countByStatusSince(any()) } returns mapOf(
                "SUCCEEDED" to 10L,
                "FAILED" to 2L,
                "RUNNING" to 1L,
            )

            val summary = service.getOpsSummary()

            summary.delivery.total shouldBe 46L
            summary.delivery.sent shouldBe 42L
            summary.delivery.failed shouldBe 3L
            summary.pipeline.total shouldBe 13L
            summary.pipeline.success shouldBe 10L
            summary.pipeline.failed shouldBe 2L
        }

        @Test
        fun `빈 결과면 total 과 각 필드가 0 이다`() {
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
            val service = serviceWithClock(clock)

            every { deliveryLogStore.countByStatusOn(any()) } returns emptyMap()
            every { pipelineRunStore.countByStatusSince(any()) } returns emptyMap()

            val summary = service.getOpsSummary()

            summary.delivery.total shouldBe 0L
            summary.delivery.sent shouldBe 0L
            summary.delivery.failed shouldBe 0L
            summary.pipeline.total shouldBe 0L
            summary.pipeline.success shouldBe 0L
            summary.pipeline.failed shouldBe 0L
        }

        @Test
        fun `pipeline since 파라미터는 오늘 KST 자정 Instant 이다`() {
            // 2026-04-18 10:00 KST = 01:00 UTC
            // TODAY_KST_MIDNIGHT = 2026-04-18 00:00 KST = 2026-04-17 15:00 UTC
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
            val service = serviceWithClock(clock)

            val sinceSlot = slot<Instant>()
            every { deliveryLogStore.countByStatusOn(any()) } returns emptyMap()
            every { pipelineRunStore.countByStatusSince(capture(sinceSlot)) } returns emptyMap()

            service.getOpsSummary()

            verify(exactly = 1) { pipelineRunStore.countByStatusSince(any()) }
            sinceSlot.captured shouldBe Instant.parse("2026-04-17T15:00:00Z")
        }

        @Test
        fun `pipeline 의 SUCCESS key 는 매칭되지 않고 SUCCEEDED 만 집계된다`() {
            // PipelineRunStatus enum 은 SUCCEEDED 를 사용하므로 SUCCESS 문자열은 무시되어야 한다
            val fixedInstant = Instant.parse("2026-04-18T01:00:00Z")
            val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
            val service = serviceWithClock(clock)

            every { deliveryLogStore.countByStatusOn(any()) } returns emptyMap()
            every { pipelineRunStore.countByStatusSince(any()) } returns mapOf(
                "SUCCESS" to 99L, // 잘못된 key — 집계되지 않아야 한다
                "SUCCEEDED" to 5L,
            )

            val summary = service.getOpsSummary()

            summary.pipeline.success shouldBe 5L
            summary.pipeline.total shouldBe 104L // values.sum() 은 키와 무관하게 모든 카운트를 더한다
        }
    }
}
