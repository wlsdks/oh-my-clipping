package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.ActiveSubscriptionsSummaryDto
import com.clipping.mcpserver.admin.dto.ForecastDto
import com.clipping.mcpserver.admin.dto.UserEngagementTrendDto
import com.clipping.mcpserver.service.DashboardService
import com.clipping.mcpserver.service.dto.ActiveSubscriptionsSummaryResult
import com.clipping.mcpserver.service.dto.EngagementTrendResult
import com.clipping.mcpserver.service.dto.TodayForecastResult
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DashboardAdminControllerTest {

    private val dashboardService = mockk<DashboardService>()
    private val controller = DashboardAdminController(dashboardService)

    @Nested
    inner class `forecast 엔드포인트` {

        @Test
        fun `서비스 반환값을 ForecastDto로 매핑하여 반환한다`() {
            every { dashboardService.todayForecast() } returns TodayForecastResult(
                expectedRunCount = 12,
                expectedDigestCount = 200,
                nextRunAtKst = "2026-04-18T10:05:00+09:00",
            )

            val result = controller.forecast()

            result shouldBe ForecastDto(
                expectedRunCount = 12,
                expectedDigestCount = 200,
                nextRunAtKst = "2026-04-18T10:05:00+09:00",
            )
            verify(exactly = 1) { dashboardService.todayForecast() }
        }
    }

    @Nested
    inner class `userEngagementTrend 엔드포인트` {

        @Test
        fun `서비스 반환값을 UserEngagementTrendDto로 매핑하여 반환한다`() {
            every { dashboardService.engagementTrend() } returns EngagementTrendResult(
                yesterdayClickRate = 12.5,
                sevenDayAvgClickRate = 11.0,
                sevenDayStdDev = 1.5,
                feedbackPositiveYesterday = 40L,
                feedbackNegativeYesterday = 3L,
            )

            val result = controller.userEngagementTrend()

            result shouldBe UserEngagementTrendDto(
                yesterdayClickRate = 12.5,
                sevenDayAvgClickRate = 11.0,
                sevenDayStdDev = 1.5,
                feedbackPositiveYesterday = 40L,
                feedbackNegativeYesterday = 3L,
            )
            verify(exactly = 1) { dashboardService.engagementTrend() }
        }
    }

    @Nested
    inner class `activeSubscriptionsSummary 엔드포인트` {

        @Test
        fun `서비스 반환값을 ActiveSubscriptionsSummaryDto로 매핑하여 반환한다`() {
            every { dashboardService.activeSubscriptionsSummary() } returns ActiveSubscriptionsSummaryResult(
                activeCount = 500L,
                newThisWeek = 12L,
                deactivatedThisWeek = 3L,
                netChange = 9L,
            )

            val result = controller.activeSubscriptionsSummary()

            result shouldBe ActiveSubscriptionsSummaryDto(
                activeCount = 500L,
                newThisWeek = 12L,
                deactivatedThisWeek = 3L,
                netChange = 9L,
            )
            verify(exactly = 1) { dashboardService.activeSubscriptionsSummary() }
        }
    }
}
