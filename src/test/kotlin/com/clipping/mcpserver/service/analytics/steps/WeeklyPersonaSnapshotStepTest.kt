package com.clipping.mcpserver.service.analytics.steps

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.Persona
import com.clipping.mcpserver.service.analytics.ActivityJudgment
import com.clipping.mcpserver.service.analytics.PersonaSubscriptionActivityJudge
import com.clipping.mcpserver.store.analytics.dto.WeeklyPersonaSnapshot
import com.clipping.mcpserver.store.analytics.dto.WeeklySubscriptionState
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.PersonaAnalyticsStore
import com.clipping.mcpserver.store.PersonaStore
import com.clipping.mcpserver.store.UserEventStore
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class WeeklyPersonaSnapshotStepTest {

    private val personaStore = mockk<PersonaStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val categoryRuleStore = mockk<CategoryRuleStore>()
    private val deliveryLogStore = mockk<DeliveryLogStore>()
    private val userEventStore = mockk<UserEventStore>()
    private val analyticsStore = mockk<PersonaAnalyticsStore>(relaxed = true)
    private val activityJudge = PersonaSubscriptionActivityJudge()

    private lateinit var step: WeeklyPersonaSnapshotStep

    private val weekStart = LocalDate.of(2026, 3, 30) // 월요일
    private val runId = "test-run-001"

    @BeforeEach
    fun setUp() {
        step = WeeklyPersonaSnapshotStep(
            personaStore, categoryStore, categoryRuleStore,
            deliveryLogStore, userEventStore, analyticsStore, activityJudge
        )
        // 글로벌 참여 지표 기본 mock
        every { userEventStore.countByEventType(any(), any(), any()) } returns 10L
        every { userEventStore.countDistinctUsers(any(), any()) } returns 3L
        every { categoryStore.countOperational() } returns 0L
    }

    private fun persona(id: String, name: String, isPreset: Boolean = true) = Persona(
        id = id, name = name, isPreset = isPreset, isActive = true,
        systemPrompt = "test", createdAt = Instant.parse("2025-01-01T00:00:00Z")
    )

    private fun category(id: String, personaId: String, createdAt: Instant = Instant.parse("2025-01-01T00:00:00Z")) =
        Category(id = id, name = "cat-$id", personaId = personaId, isActive = true, createdAt = createdAt)

    private fun weekdayRule(categoryId: String) = CategoryRule(
        categoryId = categoryId,
        deliveryDays = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
        deliveryHour = 9
    )

    @Nested
    inner class `정상 집계` {

        @Test
        fun `3개 페르소나를 순회하면 3개 스냅샷이 upsert 된다`() {
            // given
            val personas = listOf(
                persona("p1", "뉴스봇A"),
                persona("p2", "뉴스봇B"),
                persona("p3", "뉴스봇C", isPreset = false)
            )
            every { personaStore.listActive() } returns personas

            for (p in personas) {
                val cats = listOf(category("cat-${p.id}", p.id))
                every { categoryStore.findActiveByPersonaId(p.id) } returns cats
                every { categoryRuleStore.findByCategoryId("cat-${p.id}") } returns weekdayRule("cat-${p.id}")
                every { deliveryLogStore.existsSent("cat-${p.id}", any(), any()) } returns true
                every { deliveryLogStore.countAll("cat-${p.id}", "SENT", any(), any()) } returns 3
                every { analyticsStore.countChurnedSubscriptions(any(), any(), p.id) } returns 0
            }
            // 글로벌 카테고리 목록 (활성 3개)
            every { categoryStore.countOperational() } returns 3L

            // when
            val count = step.execute(weekStart, runId)

            // then
            count shouldBe 3
            verify(exactly = 3) { analyticsStore.upsertWeeklySnapshot(any()) }
            verify(exactly = 3) { analyticsStore.upsertWeeklySubscriptionStates(any()) }
        }

        @Test
        fun `멱등성 — 같은 weekStart 로 두 번 실행해도 동일하게 upsert 된다`() {
            // given
            val personas = listOf(persona("p1", "뉴스봇A"))
            every { personaStore.listActive() } returns personas
            every { categoryStore.findActiveByPersonaId("p1") } returns listOf(category("c1", "p1"))
            every { categoryRuleStore.findByCategoryId("c1") } returns weekdayRule("c1")
            every { deliveryLogStore.existsSent("c1", any(), any()) } returns true
            every { deliveryLogStore.countAll("c1", "SENT", any(), any()) } returns 2
            every { analyticsStore.countChurnedSubscriptions(any(), any(), "p1") } returns 0
            every { categoryStore.countOperational() } returns 1L

            // when — 두 번 실행
            step.execute(weekStart, runId)
            step.execute(weekStart, runId)

            // then — upsert 이므로 2번 호출되지만 결과는 동일
            verify(exactly = 2) { analyticsStore.upsertWeeklySnapshot(any()) }
        }
    }

    @Nested
    inner class `이탈 구독 계산` {

        @Test
        fun `전주에 ACTIVE 였지만 이번 주에 없으면 churned 로 집계된다`() {
            // given
            every { personaStore.listActive() } returns listOf(persona("p1", "뉴스봇"))
            every { categoryStore.findActiveByPersonaId("p1") } returns listOf(category("c1", "p1"))
            every { categoryRuleStore.findByCategoryId("c1") } returns weekdayRule("c1")
            every { deliveryLogStore.existsSent("c1", any(), any()) } returns true
            every { deliveryLogStore.countAll("c1", "SENT", any(), any()) } returns 1
            every { categoryStore.countOperational() } returns 1L
            // 전주 대비 2명 이탈
            every { analyticsStore.countChurnedSubscriptions(any(), any(), "p1") } returns 2

            // when
            step.execute(weekStart, runId)

            // then
            val snapshotSlot = slot<WeeklyPersonaSnapshot>()
            verify { analyticsStore.upsertWeeklySnapshot(capture(snapshotSlot)) }
            snapshotSlot.captured.churnedSubs shouldBe 2
        }

        @Test
        fun `이전 스냅샷이 없으면 churned = 0`() {
            // given
            every { personaStore.listActive() } returns listOf(persona("p1", "뉴스봇"))
            every { categoryStore.findActiveByPersonaId("p1") } returns listOf(category("c1", "p1"))
            every { categoryRuleStore.findByCategoryId("c1") } returns weekdayRule("c1")
            every { deliveryLogStore.existsSent("c1", any(), any()) } returns true
            every { deliveryLogStore.countAll("c1", "SENT", any(), any()) } returns 1
            every { categoryStore.countOperational() } returns 1L
            every { analyticsStore.countChurnedSubscriptions(any(), any(), "p1") } returns 0

            // when
            step.execute(weekStart, runId)

            // then
            val snapshotSlot = slot<WeeklyPersonaSnapshot>()
            verify { analyticsStore.upsertWeeklySnapshot(capture(snapshotSlot)) }
            snapshotSlot.captured.churnedSubs shouldBe 0
            // 이전 없으므로 활성 구독 = new 가 될 수 있음 (가입 14일 미만 판정에 따라)
        }
    }

    @Nested
    inner class `참여율 계산` {

        @Test
        fun `참여 유저가 있으면 engagementRate 가 0보다 크다`() {
            // given
            every { personaStore.listActive() } returns listOf(persona("p1", "뉴스봇"))
            val cats = listOf(category("c1", "p1"))
            every { categoryStore.findActiveByPersonaId("p1") } returns cats
            every { categoryRuleStore.findByCategoryId("c1") } returns weekdayRule("c1")
            every { deliveryLogStore.existsSent("c1", any(), any()) } returns true
            every { deliveryLogStore.countAll("c1", "SENT", any(), any()) } returns 5
            every { analyticsStore.countChurnedSubscriptions(any(), any(), "p1") } returns 0
            every { categoryStore.countOperational() } returns cats.size.toLong()
            every { userEventStore.countDistinctUsers(any(), any()) } returns 5L
            every { userEventStore.countByEventType("ARTICLE_CLICK", any(), any()) } returns 20L
            every { userEventStore.countByEventType("BOOKMARK", any(), any()) } returns 5L

            // when
            step.execute(weekStart, runId)

            // then
            val snapshotSlot = slot<WeeklyPersonaSnapshot>()
            verify { analyticsStore.upsertWeeklySnapshot(capture(snapshotSlot)) }
            snapshotSlot.captured.engagementRate shouldBeGreaterThan 0.0
            snapshotSlot.captured.engagementRate shouldBeLessThanOrEqual 1.0
        }

        @Test
        fun `활성 구독이 0이면 engagementRate = 0`() {
            // given — 구독 없는 페르소나
            every { personaStore.listActive() } returns listOf(persona("p1", "뉴스봇"))
            every { categoryStore.findActiveByPersonaId("p1") } returns emptyList()
            every { analyticsStore.countChurnedSubscriptions(any(), any(), "p1") } returns 0
            every { categoryStore.countOperational() } returns 0L

            // when
            step.execute(weekStart, runId)

            // then
            val snapshotSlot = slot<WeeklyPersonaSnapshot>()
            verify { analyticsStore.upsertWeeklySnapshot(capture(snapshotSlot)) }
            snapshotSlot.captured.engagementRate shouldBe 0.0
            snapshotSlot.captured.activeSubs shouldBe 0
        }
    }

    @Nested
    inner class `클릭_발송 비율` {

        @Test
        fun `deliveredCount 가 0이면 clickPerDelivery = 0`() {
            // given
            every { personaStore.listActive() } returns listOf(persona("p1", "뉴스봇"))
            every { categoryStore.findActiveByPersonaId("p1") } returns listOf(category("c1", "p1"))
            every { categoryRuleStore.findByCategoryId("c1") } returns weekdayRule("c1")
            every { deliveryLogStore.existsSent("c1", any(), any()) } returns false
            every { deliveryLogStore.countAll("c1", "SENT", any(), any()) } returns 0
            every { analyticsStore.countChurnedSubscriptions(any(), any(), "p1") } returns 0
            every { categoryStore.countOperational() } returns 1L

            // when
            step.execute(weekStart, runId)

            // then
            val snapshotSlot = slot<WeeklyPersonaSnapshot>()
            verify { analyticsStore.upsertWeeklySnapshot(capture(snapshotSlot)) }
            snapshotSlot.captured.clickPerDelivery shouldBe 0.0
        }

        @Test
        fun `발송이 있으면 clickPerDelivery = totalClicks 나누기 deliveredCount`() {
            // given
            every { personaStore.listActive() } returns listOf(persona("p1", "뉴스봇"))
            val cats = listOf(category("c1", "p1"))
            every { categoryStore.findActiveByPersonaId("p1") } returns cats
            every { categoryRuleStore.findByCategoryId("c1") } returns weekdayRule("c1")
            every { deliveryLogStore.existsSent("c1", any(), any()) } returns true
            every { deliveryLogStore.countAll("c1", "SENT", any(), any()) } returns 4
            every { analyticsStore.countChurnedSubscriptions(any(), any(), "p1") } returns 0
            every { categoryStore.countOperational() } returns cats.size.toLong()
            every { userEventStore.countByEventType("ARTICLE_CLICK", any(), any()) } returns 20L
            every { userEventStore.countByEventType("BOOKMARK", any(), any()) } returns 0L

            // when
            step.execute(weekStart, runId)

            // then
            val snapshotSlot = slot<WeeklyPersonaSnapshot>()
            verify { analyticsStore.upsertWeeklySnapshot(capture(snapshotSlot)) }
            val snap = snapshotSlot.captured
            // 클릭은 글로벌 비례 배분이므로 totalClicks = 20 * 1/1 = 20, delivered = 4
            snap.clickPerDelivery shouldBe (snap.totalClicks.toDouble() / snap.deliveredCount.toDouble())
        }
    }

    @Nested
    inner class `배치 카운터` {

        @Test
        fun `personas_scanned 카운터가 페르소나 수만큼 증분된다`() {
            // given
            val personas = listOf(persona("p1", "A"), persona("p2", "B"))
            every { personaStore.listActive() } returns personas
            for (p in personas) {
                every { categoryStore.findActiveByPersonaId(p.id) } returns emptyList()
                every { analyticsStore.countChurnedSubscriptions(any(), any(), p.id) } returns 0
            }

            // when
            step.execute(weekStart, runId)

            // then
            verify(exactly = 1) { analyticsStore.updateRunCounter(runId, "personas_scanned", 2) }
        }
    }

    @Nested
    inner class `신규 구독 판정` {

        @Test
        fun `가입 14일 미만 카테고리는 NEW 상태로 집계된다`() {
            // given — 최근 생성된 카테고리
            val recentCreatedAt = weekStart.plusDays(1).atStartOfDay(
                com.clipping.mcpserver.service.analytics.time.AnalyticsTime.KST
            ).toInstant()
            every { personaStore.listActive() } returns listOf(persona("p1", "뉴스봇"))
            every { categoryStore.findActiveByPersonaId("p1") } returns
                listOf(category("c1", "p1", createdAt = recentCreatedAt))
            every { categoryRuleStore.findByCategoryId("c1") } returns weekdayRule("c1")
            every { deliveryLogStore.existsSent("c1", any(), any()) } returns false
            every { deliveryLogStore.countAll("c1", "SENT", any(), any()) } returns 0
            every { analyticsStore.countChurnedSubscriptions(any(), any(), "p1") } returns 0
            every { categoryStore.countOperational() } returns 1L

            // when
            step.execute(weekStart, runId)

            // then
            val statesSlot = slot<List<WeeklySubscriptionState>>()
            verify { analyticsStore.upsertWeeklySubscriptionStates(capture(statesSlot)) }
            statesSlot.captured.first().state shouldBe "NEW"

            val snapshotSlot = slot<WeeklyPersonaSnapshot>()
            verify { analyticsStore.upsertWeeklySnapshot(capture(snapshotSlot)) }
            snapshotSlot.captured.newSubs shouldBe 1
            snapshotSlot.captured.activeSubs shouldBe 1  // NEW 는 활성으로 카운트
        }
    }

    @Nested
    inner class `스케줄 없는 구독` {

        @Test
        fun `deliveryDays 가 없으면 CHURNED 상태로 판정된다`() {
            // given
            every { personaStore.listActive() } returns listOf(persona("p1", "뉴스봇"))
            every { categoryStore.findActiveByPersonaId("p1") } returns listOf(category("c1", "p1"))
            // 스케줄 규칙 없음
            every { categoryRuleStore.findByCategoryId("c1") } returns null
            every { deliveryLogStore.countAll("c1", "SENT", any(), any()) } returns 0
            every { analyticsStore.countChurnedSubscriptions(any(), any(), "p1") } returns 0
            every { categoryStore.countOperational() } returns 1L

            // when
            step.execute(weekStart, runId)

            // then
            val statesSlot = slot<List<WeeklySubscriptionState>>()
            verify { analyticsStore.upsertWeeklySubscriptionStates(capture(statesSlot)) }
            statesSlot.captured.first().state shouldBe "CHURNED"
        }
    }
}
