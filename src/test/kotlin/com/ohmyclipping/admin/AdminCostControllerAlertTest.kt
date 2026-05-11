package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.CostAlertCurrentDto
import com.ohmyclipping.service.LlmCostService
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminCostControllerAlertTest {

    private val llmCostService = mockk<LlmCostService>()
    private val controller = AdminCostController(llmCostService)

    private fun alertResult(level: String?, usagePct: Int = 45, remainingDays: Int = 12) =
        LlmCostService.CurrentBudgetAlertResult(
            monthId = "2026-04",
            currentLevel = level,
            usagePct = usagePct,
            remainingDays = remainingDays,
        )

    @Nested
    inner class `currentBudgetAlert 엔드포인트` {

        @Test
        fun `활성 임계값이 없으면 currentLevel이 null인 DTO를 반환한다`() {
            every { llmCostService.currentBudgetAlert() } returns alertResult(null)

            val result = controller.currentBudgetAlert()

            result shouldBe CostAlertCurrentDto(
                monthId = "2026-04",
                currentLevel = null,
                usagePct = 45,
                remainingDays = 12,
            )
            verify(exactly = 1) { llmCostService.currentBudgetAlert() }
        }

        @Test
        fun `CRITICAL_90 레벨이면 DTO에 그대로 반영된다`() {
            every { llmCostService.currentBudgetAlert() } returns alertResult("CRITICAL_90", usagePct = 92)

            val result = controller.currentBudgetAlert()

            result.currentLevel shouldBe "CRITICAL_90"
            result.usagePct shouldBe 92
        }

        @Test
        fun `CRITICAL_100 레벨이면 DTO에 그대로 반영된다`() {
            every { llmCostService.currentBudgetAlert() } returns alertResult("CRITICAL_100", usagePct = 105)

            val result = controller.currentBudgetAlert()

            result.currentLevel shouldBe "CRITICAL_100"
            result.usagePct shouldBe 105
        }

        @Test
        fun `monthId와 remainingDays가 올바르게 매핑된다`() {
            every { llmCostService.currentBudgetAlert() } returns alertResult(null, usagePct = 30, remainingDays = 8)

            val result = controller.currentBudgetAlert()

            result.monthId shouldBe "2026-04"
            result.remainingDays shouldBe 8
        }
    }

    @Nested
    inner class `detail 엔드포인트` {

        @Test
        fun `카테고리 필터를 비용 집계 서비스에 전달한다`() {
            every { llmCostService.summarizeByChannel(any(), any(), "cat-1") } returns LlmCostService.CostSummary(
                from = Instant.parse("2026-05-01T00:00:00Z"),
                to = Instant.parse("2026-06-01T00:00:00Z"),
                inputCostPerMillionUsd = 0.10,
                outputCostPerMillionUsd = 0.40,
                totalRequestCount = 1,
                totalTokensIn = 100,
                totalTokensOut = 50,
                totalEstimatedUsd = 0.00003,
                rows = listOf(
                    LlmCostService.CostRow(
                        channelId = "C0123456789",
                        categoryId = "cat-1",
                        categoryName = "기술",
                        requestCount = 1,
                        tokensIn = 100,
                        tokensOut = 50,
                        estimatedUsd = 0.00003
                    )
                )
            )

            val result = controller.detail("2026-05-01", "2026-05-31", "cat-1")

            result.rows.size shouldBe 1
            result.rows[0].categoryId shouldBe "cat-1"
            verify(exactly = 1) { llmCostService.summarizeByChannel(any(), any(), "cat-1") }
        }
    }
}
