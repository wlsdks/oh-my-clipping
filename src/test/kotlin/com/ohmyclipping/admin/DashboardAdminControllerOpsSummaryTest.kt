package com.ohmyclipping.admin

import com.ohmyclipping.service.DashboardService
import com.ohmyclipping.service.dto.analytics.DeliveryOpsSummary
import com.ohmyclipping.service.dto.analytics.OpsSummary
import com.ohmyclipping.service.dto.analytics.PipelineOpsSummary
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DashboardAdminControllerOpsSummaryTest {

    private val dashboardService = mockk<DashboardService>()
    private val controller = DashboardAdminController(dashboardService)

    @Nested
    inner class `getOpsSummary 엔드포인트` {

        @Test
        fun `delivery + pipeline 카운트 포함 OpsSummary를 반환한다`() {
            every { dashboardService.getOpsSummary() } returns OpsSummary(
                delivery = DeliveryOpsSummary(total = 46L, sent = 42L, failed = 3L),
                pipeline = PipelineOpsSummary(total = 13L, success = 10L, failed = 2L),
            )

            val result = controller.getOpsSummary()

            result shouldBe OpsSummary(
                delivery = DeliveryOpsSummary(total = 46L, sent = 42L, failed = 3L),
                pipeline = PipelineOpsSummary(total = 13L, success = 10L, failed = 2L),
            )
            verify(exactly = 1) { dashboardService.getOpsSummary() }
        }

        @Test
        fun `서비스가 0 카운트를 반환해도 그대로 전달한다`() {
            every { dashboardService.getOpsSummary() } returns OpsSummary(
                delivery = DeliveryOpsSummary(total = 0L, sent = 0L, failed = 0L),
                pipeline = PipelineOpsSummary(total = 0L, success = 0L, failed = 0L),
            )

            val result = controller.getOpsSummary()

            result.delivery.total shouldBe (0L)
            result.pipeline.total shouldBe (0L)
            verify(exactly = 1) { dashboardService.getOpsSummary() }
        }
    }
}
