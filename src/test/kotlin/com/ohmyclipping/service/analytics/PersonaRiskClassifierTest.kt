package com.ohmyclipping.service.analytics

import com.ohmyclipping.model.Persona
import com.ohmyclipping.service.analytics.dto.ChurnExcessDetails
import com.ohmyclipping.service.analytics.dto.EngagementDropDetails
import com.ohmyclipping.service.analytics.dto.EngagementRiseDetails
import com.ohmyclipping.service.analytics.dto.ExcludedReason
import com.ohmyclipping.service.analytics.dto.FirstSubscriptionDetails
import com.ohmyclipping.service.analytics.dto.GrowthSignalType
import com.ohmyclipping.service.analytics.dto.IdleDetails
import com.ohmyclipping.service.analytics.dto.RiskSignalType
import com.ohmyclipping.service.analytics.dto.SubsSurgeDetails
import com.ohmyclipping.store.analytics.dto.WeeklyPersonaSnapshot
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * `PersonaRiskClassifier` 단위 테스트. 스펙 §2, §2.5 모든 판정 케이스.
 *
 * 고정 임계치 (기본값 그대로) 를 주입해 판정 경계값을 명확하게 검증한다.
 */
class PersonaRiskClassifierTest {

    private val risk = AnalyticsRiskProperties(
        churnMinCount = 2,
        churnBaselineMin = 8,
        engagementDropPp = 10,
        engagementMinDeliveries = 30,
        idleWeeks = 4
    )
    private val growth = AnalyticsGrowthProperties(
        subsSurgePct = 20,
        subsSurgeMin = 3,
        engagementRisePp = 10
    )

    private val classifier = PersonaRiskClassifier(risk, growth)

    private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    private val weekStart = LocalDate.of(2026, 4, 13) // 월요일

    private fun preset(id: String = "p1", createdDaysAgo: Long = 200) = Persona(
        id = id,
        name = id,
        systemPrompt = "x",
        isPreset = true,
        createdAt = weekStart.minusDays(createdDaysAgo).atStartOfDay(KST).toInstant()
    )

    private fun custom(id: String = "c1", createdDaysAgo: Long = 200) = Persona(
        id = id,
        name = id,
        systemPrompt = "x",
        isPreset = false,
        createdAt = weekStart.minusDays(createdDaysAgo).atStartOfDay(KST).toInstant()
    )

    private fun snap(
        personaId: String,
        week: LocalDate,
        activeSubs: Int = 10,
        newSubs: Int = 0,
        churnedSubs: Int = 0,
        deliveredCount: Int = 50,
        totalClicks: Int = 20,
        engagedUsers: Int = 5,
        engagementRate: Double = 0.5,
        isPreset: Boolean = true
    ) = WeeklyPersonaSnapshot(
        id = "$personaId-$week",
        weekStart = week,
        personaId = personaId,
        personaName = personaId,
        isPreset = isPreset,
        activeSubs = activeSubs,
        newSubs = newSubs,
        churnedSubs = churnedSubs,
        deliveredCount = deliveredCount,
        deliveredItems = deliveredCount,
        engagedUsers = engagedUsers,
        totalClicks = totalClicks,
        totalBookmarks = 0,
        engagementRate = engagementRate,
        clickPerDelivery = 0.0,
        createdAt = Instant.now()
    )

    @Nested
    inner class `CHURN_EXCESS 판정` {

        @Test
        fun `이탈이 신규보다 많고 baseline 충족되면 위험 신호`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 10, newSubs = 1, churnedSubs = 0),
                snap(p.id, weekStart, activeSubs = 8, newSubs = 1, churnedSubs = 3)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks shouldHaveSize 1
            val item = result.risks.single()
            item.riskType shouldBe RiskSignalType.CHURN_EXCESS
            val d = item.details.shouldBeInstanceOf<ChurnExcessDetails>()
            d.churnedSubs shouldBe 3
            d.newSubs shouldBe 1
        }

        @Test
        fun `전주 baseline 미달이면 위험 신호 아니고 excluded 로 분류`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 5, newSubs = 0, churnedSubs = 0),
                snap(p.id, weekStart, activeSubs = 3, newSubs = 0, churnedSubs = 2)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks.shouldBeEmpty()
            result.excluded shouldHaveSize 1
            result.excluded.single().reason shouldBe ExcludedReason.CHURN_BASELINE_BELOW_MIN
        }

        @Test
        fun `churnedSubs가 newSubs 이하면 위험 아님`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 10),
                snap(p.id, weekStart, activeSubs = 10, newSubs = 3, churnedSubs = 3)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks.shouldBeEmpty()
        }
    }

    @Nested
    inner class `IDLE 판정` {

        @Test
        fun `프리셋이 연속 4주 0 발송이면 유휴`() {
            val p = preset()
            val series = (0 until 4).map { i ->
                snap(
                    p.id,
                    weekStart.minusWeeks((3 - i).toLong()),
                    activeSubs = 0,
                    deliveredCount = 0
                )
            }
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks shouldHaveSize 1
            val item = result.risks.single()
            item.riskType shouldBe RiskSignalType.IDLE
            item.persistentWeeks shouldBe 4
            val d = item.details.shouldBeInstanceOf<IdleDetails>()
            d.consecutiveWeeks shouldBe 4
        }

        @Test
        fun `커스텀은 유휴 판정 제외 (excluded 로 분류)`() {
            val c = custom()
            val series = (0 until 4).map { i ->
                snap(c.id, weekStart.minusWeeks((3 - i).toLong()), activeSubs = 0, deliveredCount = 0, isPreset = false)
            }
            val result = classifier.classify(weekStart, listOf(c), mapOf(c.id to series))
            result.risks.shouldBeEmpty()
            result.excluded.single().reason shouldBe ExcludedReason.IDLE_NOT_PRESET
        }

        @Test
        fun `중간 한 주라도 발송 있으면 유휴 아님`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(3), activeSubs = 0, deliveredCount = 0),
                snap(p.id, weekStart.minusWeeks(2), activeSubs = 0, deliveredCount = 5),
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 0, deliveredCount = 0),
                snap(p.id, weekStart, activeSubs = 0, deliveredCount = 0)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks.shouldBeEmpty()
        }
    }

    @Nested
    inner class `ENGAGEMENT_DROP 판정` {

        @Test
        fun `전주 대비 10pp 이상 하락 + delivered 양쪽 충족이면 위험`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), deliveredCount = 40, engagementRate = 0.53),
                snap(p.id, weekStart, deliveredCount = 40, engagementRate = 0.41)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks shouldHaveSize 1
            val d = result.risks.single().details.shouldBeInstanceOf<EngagementDropDetails>()
            d.deltaPp shouldBe -12
        }

        @Test
        fun `delivered 최소치 미달이면 excluded`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), deliveredCount = 10, engagementRate = 0.6),
                snap(p.id, weekStart, deliveredCount = 10, engagementRate = 0.4)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks.shouldBeEmpty()
            result.excluded.single().reason shouldBe ExcludedReason.ENGAGEMENT_DELIVERIES_BELOW_MIN
        }

        @Test
        fun `하락폭이 9pp 면 위험 아님 (경계값)`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), deliveredCount = 40, engagementRate = 0.50),
                snap(p.id, weekStart, deliveredCount = 40, engagementRate = 0.41)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks.shouldBeEmpty()
        }
    }

    @Nested
    inner class `SUBS_SURGE 성장 판정` {

        @Test
        fun `전주 대비 +20퍼 이상, +3명 이상 증가면 성장`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 7),
                snap(p.id, weekStart, activeSubs = 12)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.growth shouldHaveSize 1
            val g = result.growth.single()
            g.signalType shouldBe GrowthSignalType.SUBS_SURGE
            val d = g.details.shouldBeInstanceOf<SubsSurgeDetails>()
            d.deltaAbs shouldBe 5
            d.deltaPct shouldBe 71
        }

        @Test
        fun `증가 절댓값이 min 미만이면 성장 아님 (2명 증가)`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 5),
                snap(p.id, weekStart, activeSubs = 7)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.growth.shouldBeEmpty()
        }
    }

    @Nested
    inner class `FIRST_SUBSCRIPTION 판정` {

        @Test
        fun `신규 생성 4주 이내 첫 구독 진입이면 성장`() {
            val p = custom(createdDaysAgo = 7)
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 0, isPreset = false),
                snap(p.id, weekStart, activeSubs = 1, isPreset = false)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.growth shouldHaveSize 1
            val g = result.growth.single()
            g.signalType shouldBe GrowthSignalType.FIRST_SUBSCRIPTION
            val d = g.details.shouldBeInstanceOf<FirstSubscriptionDetails>()
            d.daysSinceCreation shouldBe 7
        }

        @Test
        fun `생성 30일 지난 페르소나는 FIRST_SUBSCRIPTION 아님`() {
            val p = custom(createdDaysAgo = 30)
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 0, isPreset = false),
                snap(p.id, weekStart, activeSubs = 1, isPreset = false)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.growth.shouldBeEmpty()
        }
    }

    @Nested
    inner class `지속 주차 카운팅` {

        @Test
        fun `연속 2주 CHURN_EXCESS 면 persistentWeeks 2`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(2), activeSubs = 10, churnedSubs = 0),
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 10, newSubs = 1, churnedSubs = 3),
                snap(p.id, weekStart, activeSubs = 8, newSubs = 1, churnedSubs = 3)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks.single().persistentWeeks shouldBe 2
        }

        @Test
        fun `중간 OFF 주가 있으면 리셋되어 NEW 1`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(3), activeSubs = 10),
                snap(p.id, weekStart.minusWeeks(2), activeSubs = 10, newSubs = 1, churnedSubs = 3), // ON
                snap(p.id, weekStart.minusWeeks(1), activeSubs = 10, newSubs = 0, churnedSubs = 0), // OFF
                snap(p.id, weekStart, activeSubs = 8, newSubs = 1, churnedSubs = 3) // ON
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.risks.single().persistentWeeks shouldBe 1
        }
    }

    @Nested
    inner class `ENGAGEMENT_RISE 성장 판정` {

        @Test
        fun `전주 대비 10pp 이상 상승 + delivered 충족이면 성장`() {
            val p = preset()
            val series = listOf(
                snap(p.id, weekStart.minusWeeks(1), deliveredCount = 40, engagementRate = 0.40),
                snap(p.id, weekStart, deliveredCount = 40, engagementRate = 0.55)
            )
            val result = classifier.classify(weekStart, listOf(p), mapOf(p.id to series))
            result.growth shouldHaveSize 1
            val d = result.growth.single().details.shouldBeInstanceOf<EngagementRiseDetails>()
            d.deltaPp shouldBe 15
        }
    }
}
