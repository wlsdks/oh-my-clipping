package com.clipping.mcpserver.service.analytics

import com.clipping.mcpserver.service.analytics.dto.PortfolioStatus
import com.clipping.mcpserver.store.PersonaAnalyticsStore
import com.clipping.mcpserver.store.PersonaStore
import com.clipping.mcpserver.store.PresetUsageRow
import com.clipping.mcpserver.store.RecentCustomPersonaRow
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class PersonaAnalyticsReadModelTest {

    private lateinit var personaStore: PersonaStore
    private lateinit var analyticsStore: PersonaAnalyticsStore
    private lateinit var readModel: PersonaAnalyticsReadModel

    @BeforeEach
    fun setUp() {
        personaStore = mockk(relaxed = true)
        analyticsStore = mockk(relaxed = true)
        // loadSignals 경로만 riskClassifier 에 의존하므로 나머지 테스트는 relaxed mock 로 주입한다.
        val riskClassifier = mockk<PersonaRiskClassifier>(relaxed = true)
        readModel = PersonaAnalyticsReadModel(personaStore, analyticsStore, riskClassifier)
    }

    @Nested
    inner class `computeLiveTotals` {

        @Test
        fun `프리셋 + 커스텀 카운트가 totalStyles 에 합산된다`() {
            every { personaStore.listPresets() } returns List(10) { mockk(relaxed = true) }
            every { personaStore.countCustomPersonas() } returns 5L
            every { personaStore.countTotalActiveSubscriptions() } returns 0L
            every { personaStore.findPresetUsage() } returns emptyList()

            val totals = readModel.computeLiveTotals()

            assertThat(totals.totalStyles).isEqualTo(15)
            assertThat(totals.presetCount).isEqualTo(10)
            assertThat(totals.customCount).isEqualTo(5)
        }

        @Test
        fun `weekOverWeekDelta 는 Slice 1 단계에서 항상 null`() {
            every { personaStore.listPresets() } returns emptyList()
            every { personaStore.countCustomPersonas() } returns 0L
            every { personaStore.countTotalActiveSubscriptions() } returns 0L
            every { personaStore.findPresetUsage() } returns emptyList()

            val totals = readModel.computeLiveTotals()

            assertThat(totals.weekOverWeekDelta).isNull()
        }

        @Test
        fun `customStyleRatio 와 presetUsageRate 는 전체가 0 일 때 0 반환`() {
            every { personaStore.listPresets() } returns emptyList()
            every { personaStore.countCustomPersonas() } returns 0L
            every { personaStore.countTotalActiveSubscriptions() } returns 0L
            every { personaStore.findPresetUsage() } returns emptyList()

            val totals = readModel.computeLiveTotals()

            assertThat(totals.customStyleRatio).isEqualTo(0.0)
            assertThat(totals.presetUsageRate).isEqualTo(0.0)
        }

        @Test
        fun `presetUsageRate = presetSubs sum div totalActiveSubs`() {
            every { personaStore.listPresets() } returns emptyList()
            every { personaStore.countCustomPersonas() } returns 0L
            every { personaStore.countTotalActiveSubscriptions() } returns 100L
            every { personaStore.findPresetUsage() } returns listOf(
                PresetUsageRow("p1", "p1", 50L),
                PresetUsageRow("p2", "p2", 28L)
            )

            val totals = readModel.computeLiveTotals()

            assertThat(totals.presetUsageRate).isEqualTo(0.78)
        }
    }

    @Nested
    inner class `loadPresetPortfolio` {

        @Test
        fun `findPresetUsage 결과가 PresetPortfolioItem 으로 매핑된다`() {
            every { personaStore.findPresetUsage() } returns listOf(
                PresetUsageRow("p1", "테크 에디터", 12L),
                PresetUsageRow("p2", "투자 브리핑", 0L),
                PresetUsageRow("p3", "마케팅 요약", 3L)
            )

            val portfolio = readModel.loadPresetPortfolio()

            assertThat(portfolio).hasSize(3)
            assertThat(portfolio[0].personaId).isEqualTo("p1")
            assertThat(portfolio[0].personaName).isEqualTo("테크 에디터")
            assertThat(portfolio[0].activeSubs).isEqualTo(12)
        }

        @Test
        fun `5+ 활성 구독 → HEALTHY`() {
            every { personaStore.findPresetUsage() } returns listOf(
                PresetUsageRow("p1", "p1", 5L),
                PresetUsageRow("p2", "p2", 50L)
            )

            val portfolio = readModel.loadPresetPortfolio()

            assertThat(portfolio.map { it.status })
                .containsExactly(PortfolioStatus.HEALTHY, PortfolioStatus.HEALTHY)
        }

        @Test
        fun `0 활성 구독 → UNUSED, 그 외 → WATCHING`() {
            every { personaStore.findPresetUsage() } returns listOf(
                PresetUsageRow("p1", "p1", 0L),
                PresetUsageRow("p2", "p2", 1L),
                PresetUsageRow("p3", "p3", 4L)
            )

            val portfolio = readModel.loadPresetPortfolio()

            assertThat(portfolio.map { it.status })
                .containsExactly(
                    PortfolioStatus.UNUSED,
                    PortfolioStatus.WATCHING,
                    PortfolioStatus.WATCHING
                )
        }

        @Test
        fun `Slice 1 portfolio item 의 시계열 필드는 모두 null`() {
            every { personaStore.findPresetUsage() } returns listOf(
                PresetUsageRow("p1", "p1", 10L)
            )

            val item = readModel.loadPresetPortfolio().first()

            assertThat(item.weekOverWeekDelta).isNull()
            assertThat(item.engagementRate).isNull()
            assertThat(item.lastDeliveredAt).isNull()
        }
    }

    @Nested
    inner class `loadCustomSummary` {

        @Test
        fun `최근 커스텀 페르소나는 systemPrompt 를 120 자로 절단`() {
            val longPrompt = "x".repeat(500)
            every { personaStore.countCustomPersonas() } returns 1L
            every { personaStore.countActiveCustomSubscriptions() } returns 1L
            every { personaStore.findRecentCustomPersonas(20) } returns listOf(
                RecentCustomPersonaRow(
                    id = "1",
                    userName = "홍길동",
                    personaName = "VC",
                    systemPrompt = longPrompt,
                    tone = null,
                    lengthPref = null,
                    createdAt = Instant.now()
                )
            )

            val summary = readModel.loadCustomSummary()

            assertThat(summary.recentPersonas).hasSize(1)
            assertThat(summary.recentPersonas[0].systemPromptPreview.length).isEqualTo(120)
        }

        @Test
        fun `newThisWeek 는 Slice 1 에서 항상 0`() {
            every { personaStore.countCustomPersonas() } returns 5L
            every { personaStore.countActiveCustomSubscriptions() } returns 3L
            every { personaStore.findRecentCustomPersonas(20) } returns emptyList()

            val summary = readModel.loadCustomSummary()

            assertThat(summary.newThisWeek).isEqualTo(0)
            assertThat(summary.totalCustomPersonas).isEqualTo(5)
            assertThat(summary.activeCustomSubscriptions).isEqualTo(3)
        }
    }
}
