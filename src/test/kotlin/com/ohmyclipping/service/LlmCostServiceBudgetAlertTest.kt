package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.model.BudgetSetting
import com.ohmyclipping.store.BudgetSettingStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.CostAlertNotification
import com.ohmyclipping.store.CostAlertNotificationStore
import com.ohmyclipping.store.LlmRunStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

/**
 * LlmCostService.currentBudgetAlert() 단위 테스트.
 */
class LlmCostServiceBudgetAlertTest {

    private val jdbc = mockk<JdbcTemplate>()
    private val properties = ClippingMcpServerProperties(
        llmInputCostPerMillionUsd = 0.30,
        llmOutputCostPerMillionUsd = 2.50,
    )
    private val llmRunStore = mockk<LlmRunStore>()
    private val budgetSettingStore = mockk<BudgetSettingStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val costAlertNotificationStore = mockk<CostAlertNotificationStore>()

    private val service = LlmCostService(
        jdbc = jdbc,
        properties = properties,
        llmRunStore = llmRunStore,
        budgetSettingStore = budgetSettingStore,
        categoryStore = categoryStore,
        costAlertNotificationStore = costAlertNotificationStore,
    )

    init {
        every { llmRunStore.sumBillableTokensBetween(any(), any(), any()) } returns (0L to 0L)
    }

    private fun budgetOf(usd: Double) = BudgetSetting(
        monthlyBudgetUsd = usd,
        alertThresholdPercent = 90,
        slackAlertEnabled = false,
    )

    private fun alertOf(level: String) = CostAlertNotification(
        monthId = "2026-04",
        thresholdLevel = level,
        notifiedAt = Instant.now(),
    )

    @Nested
    inner class `임계값 레벨 우선순위` {

        @Test
        fun `CRITICAL 레코드가 없으면 currentLevel이 null이다`() {
            every { costAlertNotificationStore.findActiveCriticalsByMonth(any()) } returns emptyList()
            every { budgetSettingStore.get() } returns budgetOf(100.0)
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            val result = service.currentBudgetAlert()

            result.currentLevel shouldBe null
        }

        @Test
        fun `CRITICAL_90만 있으면 currentLevel이 CRITICAL_90이다`() {
            every { costAlertNotificationStore.findActiveCriticalsByMonth(any()) } returns listOf(alertOf("CRITICAL_90"))
            every { budgetSettingStore.get() } returns budgetOf(100.0)
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            val result = service.currentBudgetAlert()

            result.currentLevel shouldBe "CRITICAL_90"
        }

        @Test
        fun `CRITICAL_90과 CRITICAL_100이 모두 있으면 CRITICAL_100이 우선한다`() {
            every { costAlertNotificationStore.findActiveCriticalsByMonth(any()) } returns listOf(
                alertOf("CRITICAL_90"),
                alertOf("CRITICAL_100"),
            )
            every { budgetSettingStore.get() } returns budgetOf(100.0)
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            val result = service.currentBudgetAlert()

            result.currentLevel shouldBe "CRITICAL_100"
        }
    }

    @Nested
    inner class `사용률 및 남은 일수 계산` {

        @Test
        fun `예산이 0이면 usagePct가 0이다`() {
            every { costAlertNotificationStore.findActiveCriticalsByMonth(any()) } returns emptyList()
            every { budgetSettingStore.get() } returns budgetOf(0.0)
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            val result = service.currentBudgetAlert()

            result.usagePct shouldBe 0
        }

        @Test
        fun `remainingDays는 월말까지 남은 일수이다`() {
            every { costAlertNotificationStore.findActiveCriticalsByMonth(any()) } returns emptyList()
            every { budgetSettingStore.get() } returns budgetOf(100.0)
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            val result = service.currentBudgetAlert()

            // remainingDays는 0~30 범위 내에 있어야 한다
            assert(result.remainingDays in 0..31) { "remainingDays=${result.remainingDays} is out of range" }
        }

        @Test
        fun `monthId는 YYYY-MM 형식이다`() {
            every { costAlertNotificationStore.findActiveCriticalsByMonth(any()) } returns emptyList()
            every { budgetSettingStore.get() } returns budgetOf(100.0)
            every { llmRunStore.findByCreatedAtBetween(any(), any(), null) } returns emptyList()

            val result = service.currentBudgetAlert()

            // YYYY-MM 형식 검증
            assert(result.monthId.matches(Regex("""\d{4}-\d{2}"""))) {
                "monthId=${result.monthId} does not match YYYY-MM pattern"
            }
        }
    }
}
