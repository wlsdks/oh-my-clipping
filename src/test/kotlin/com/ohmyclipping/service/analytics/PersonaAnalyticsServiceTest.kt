package com.ohmyclipping.service.analytics

import com.ohmyclipping.service.analytics.dto.CustomSummary
import com.ohmyclipping.service.analytics.dto.PortfolioStatus
import com.ohmyclipping.service.analytics.dto.PresetPortfolioItem
import com.ohmyclipping.service.analytics.dto.TotalsCard
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PersonaAnalyticsServiceTest {

    private lateinit var readModel: PersonaAnalyticsReadModel
    private lateinit var service: PersonaAnalyticsService

    @BeforeEach
    fun setUp() {
        readModel = mockk()
        service = PersonaAnalyticsService(readModel, mockk(relaxed = true))
    }

    @Test
    fun `getLiveSnapshot 은 ReadModel 의 3 메서드를 조합해 응답 구성`() {
        val totals = TotalsCard(
            totalStyles = 18,
            presetCount = 12,
            customCount = 6,
            activeSubscriptions = 247,
            presetUsageRate = 0.78,
            customStyleRatio = 0.33,
            weekOverWeekDelta = null
        )
        val portfolio = listOf(
            PresetPortfolioItem(
                personaId = "p1",
                personaName = "테크 에디터",
                activeSubs = 42,
                weekOverWeekDelta = null,
                engagementRate = null,
                status = PortfolioStatus.HEALTHY,
                lastDeliveredAt = null
            )
        )
        val custom = CustomSummary(
            totalCustomPersonas = 6,
            activeCustomSubscriptions = 12,
            newThisWeek = 0,
            recentPersonas = emptyList()
        )
        every { readModel.computeLiveTotals() } returns totals
        every { readModel.loadPresetPortfolio() } returns portfolio
        every { readModel.loadCustomSummary() } returns custom

        val response = service.getLiveSnapshot()

        assertThat(response.totals).isEqualTo(totals)
        assertThat(response.presetPortfolio).isEqualTo(portfolio)
        assertThat(response.customSummary).isEqualTo(custom)
        assertThat(response.asOf).isNotNull
        verify(exactly = 1) { readModel.computeLiveTotals() }
        verify(exactly = 1) { readModel.loadPresetPortfolio() }
        verify(exactly = 1) { readModel.loadCustomSummary() }
    }

    @Test
    fun `getLiveSnapshot 은 빈 데이터에도 정상 응답`() {
        val emptyTotals = TotalsCard(0, 0, 0, 0, 0.0, 0.0, null)
        every { readModel.computeLiveTotals() } returns emptyTotals
        every { readModel.loadPresetPortfolio() } returns emptyList()
        every { readModel.loadCustomSummary() } returns CustomSummary(0, 0, 0, emptyList())

        val response = service.getLiveSnapshot()

        assertThat(response.totals.totalStyles).isEqualTo(0)
        assertThat(response.presetPortfolio).isEmpty()
        assertThat(response.customSummary.recentPersonas).isEmpty()
    }

    @Test
    fun `cache name 상수는 persona-live 로 정의`() {
        assertThat(PersonaAnalyticsService.CACHE_LIVE).isEqualTo("persona-live")
    }
}
