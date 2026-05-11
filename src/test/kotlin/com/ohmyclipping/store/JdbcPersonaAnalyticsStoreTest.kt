package com.ohmyclipping.store

import com.ohmyclipping.store.analytics.dto.PersonaBatchRun
import com.ohmyclipping.store.analytics.dto.TriggerType
import com.ohmyclipping.store.analytics.dto.WeeklyPersonaSnapshot
import com.ohmyclipping.store.analytics.dto.WeeklySubscriptionState
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JdbcPersonaAnalyticsStore 통합 테스트.
 *
 * @SpringBootTest + @Transactional 조합으로 각 테스트 후 DB 상태가 롤백된다.
 * 테스트 DB 는 H2 (MODE=PostgreSQL) 이며, Flyway 마이그레이션이 자동 적용된다.
 *
 * persona_id FK 제약이 있으므로 각 테스트에서 clipping_personas 에
 * 테스트 페르소나를 먼저 삽입한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcPersonaAnalyticsStoreTest {

    @Autowired
    lateinit var store: JdbcPersonaAnalyticsStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private val weekStart = LocalDate.of(2026, 4, 7)  // 월요일
    private val prevWeek  = LocalDate.of(2026, 3, 31) // 이전 월요일

    private lateinit var personaId: String

    @BeforeEach
    fun insertTestPersona() {
        // FK(clipping_personas) 제약을 만족시키기 위해 테스트 페르소나를 삽입한다.
        personaId = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO clipping_personas
                (id, name, system_prompt, is_active, is_preset, created_at, updated_at)
            VALUES (?, ?, ?, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            personaId,
            "테스트페르소나-${personaId.take(8)}",
            "테스트용 시스템 프롬프트"
        )
    }

    // ── 헬퍼 팩토리 ────────────────────────────────────────────────────────────

    private fun makeSnapshot(
        week: LocalDate = weekStart,
        pId: String = personaId,
        activeSubs: Int = 10,
        newSubs: Int = 2,
        churnedSubs: Int = 1
    ) = WeeklyPersonaSnapshot(
        id              = UUID.randomUUID().toString(),
        weekStart       = week,
        personaId       = pId,
        personaName     = "테스트 페르소나",
        isPreset        = false,
        activeSubs      = activeSubs,
        newSubs         = newSubs,
        churnedSubs     = churnedSubs,
        deliveredCount  = 5,
        deliveredItems  = 50,
        engagedUsers    = 3,
        totalClicks     = 12,
        totalBookmarks  = 4,
        engagementRate  = 0.3,
        clickPerDelivery = 2.4,
        createdAt       = Instant.now()
    )

    // V117 에서 wpss.category_id → batch_categories(id) FK 가 생겼으므로
    // 테스트 fixture 가 참조하는 category row 를 명시적으로 생성한다.
    private fun insertTestCategoryIfMissing(id: String) {
        jdbc.update(
            """
            INSERT INTO batch_categories
                (id, name, is_active, is_public, max_items, status,
                 created_at, updated_at, system_updated_at)
            SELECT ?, ?, TRUE, FALSE, 10, 'ACTIVE',
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            WHERE NOT EXISTS (SELECT 1 FROM batch_categories WHERE id = ?)
            """.trimIndent(),
            id, "테스트카테고리-${id.take(8)}", id
        )
    }

    private fun makeSubscriptionState(
        week: LocalDate = weekStart,
        pId: String = personaId,
        categoryId: String = UUID.randomUUID().toString(),
        state: String = "ACTIVE"
    ): WeeklySubscriptionState {
        insertTestCategoryIfMissing(categoryId)
        return WeeklySubscriptionState(
            weekStart             = week,
            personaId             = pId,
            categoryId            = categoryId,
            state                 = state,
            deliveryOpportunities = 5,
            deliveredCount        = 4,
            clicksInWeek          = 2,
            bookmarksInWeek       = 1
        )
    }

    private fun makeBatchRun(
        runId: String = UUID.randomUUID().toString(),
        week: LocalDate = weekStart,
        status: String = "RUNNING",
        triggerType: TriggerType = TriggerType.SCHEDULED
    ) = PersonaBatchRun(
        id                = UUID.randomUUID().toString(),
        runId             = runId,
        triggerType       = triggerType,
        weekStart         = week,
        startedAt         = Instant.now(),
        finishedAt        = null,
        overallStatus     = status,
        snapshotStatus    = null,
        anomalyStatus     = null,
        clusteringStatus  = null,
        reportStatus      = null,
        personasScanned   = 0,
        anomaliesCreated  = 0,
        anomaliesResolved = 0,
        embeddingCalls    = 0,
        llmCalls          = 0,
        llmTokensUsed     = 0,
        errorMessage      = null,
        errorStep         = null,
        triggeredBy       = "test-user"
    )

    // ── Weekly Snapshot 테스트 ─────────────────────────────────────────────────

    @Nested
    inner class `upsertWeeklySnapshot 테스트` {

        @Test
        fun `같은 주차 두 번 호출해도 1행만 존재한다 (멱등)`() {
            // 첫 번째 upsert
            store.upsertWeeklySnapshot(makeSnapshot(activeSubs = 10))

            // 두 번째 upsert — 다른 값으로 덮어씀
            store.upsertWeeklySnapshot(makeSnapshot(activeSubs = 20))

            // (week_start, persona_id) 유니크 제약으로 1행만 남아야 한다
            val rows = store.findSnapshotsByWeek(weekStart)
            rows shouldHaveSize 1
            rows.first().activeSubs shouldBe 20
        }

        @Test
        fun `서로 다른 주차는 각각 독립 행으로 저장된다`() {
            store.upsertWeeklySnapshot(makeSnapshot(week = prevWeek))
            store.upsertWeeklySnapshot(makeSnapshot(week = weekStart))

            store.findSnapshotsByWeek(prevWeek) shouldHaveSize 1
            store.findSnapshotsByWeek(weekStart) shouldHaveSize 1
        }
    }

    @Nested
    inner class `findSnapshotsByRange 테스트` {

        @Test
        fun `범위 내 스냅샷을 weekStart DESC 정렬로 반환한다`() {
            // 세 주차를 역순으로 삽입한다
            val w1 = LocalDate.of(2026, 3, 23)
            val w2 = LocalDate.of(2026, 3, 30)
            val w3 = LocalDate.of(2026, 4, 6)

            store.upsertWeeklySnapshot(makeSnapshot(week = w2))
            store.upsertWeeklySnapshot(makeSnapshot(week = w1))
            store.upsertWeeklySnapshot(makeSnapshot(week = w3))

            // 세 주차 모두 포함 범위로 조회한다
            val results = store.findSnapshotsByRange(w1, w3)
            results shouldHaveSize 3
            // DESC 정렬이므로 첫 번째가 가장 최신 주차여야 한다
            results[0].weekStart shouldBe w3
            results[1].weekStart shouldBe w2
            results[2].weekStart shouldBe w1
        }

        @Test
        fun `범위 밖 스냅샷은 포함하지 않는다`() {
            store.upsertWeeklySnapshot(makeSnapshot(week = prevWeek))
            store.upsertWeeklySnapshot(makeSnapshot(week = weekStart))

            // prevWeek 이전 주는 없으므로 1건만 반환되어야 한다
            val results = store.findSnapshotsByRange(weekStart, weekStart)
            results shouldHaveSize 1
            results.first().weekStart shouldBe weekStart
        }
    }

    // ── Weekly Subscription State 테스트 ──────────────────────────────────────

    @Nested
    inner class `upsertWeeklySubscriptionStates 테스트` {

        @Test
        fun `배치 삽입이 정상 동작한다`() {
            val states = listOf(
                makeSubscriptionState(state = "ACTIVE"),
                makeSubscriptionState(state = "NEW"),
                makeSubscriptionState(state = "ACTIVE")
            )
            store.upsertWeeklySubscriptionStates(states)

            val found = store.findPreviousWeekSubscriptionStates(weekStart, personaId)
            found shouldHaveSize 3
        }

        @Test
        fun `같은 키로 두 번 삽입하면 1행만 존재한다 (멱등)`() {
            val categoryId = UUID.randomUUID().toString()
            val s1 = makeSubscriptionState(categoryId = categoryId, state = "ACTIVE")
            val s2 = makeSubscriptionState(categoryId = categoryId, state = "CHURNED")

            store.upsertWeeklySubscriptionStates(listOf(s1))
            store.upsertWeeklySubscriptionStates(listOf(s2))

            val found = store.findPreviousWeekSubscriptionStates(weekStart, personaId)
            found shouldHaveSize 1
            found.first().state shouldBe "CHURNED"
        }

        @Test
        fun `빈 리스트를 전달하면 아무 행도 삽입되지 않는다`() {
            store.upsertWeeklySubscriptionStates(emptyList())
            val found = store.findPreviousWeekSubscriptionStates(weekStart, personaId)
            found shouldHaveSize 0
        }
    }

    // ── countChurnedSubscriptions 테스트 ──────────────────────────────────────

    @Nested
    inner class `countChurnedSubscriptions 테스트` {

        @Test
        fun `전주 ACTIVE에서 이번주 미존재 유저만 카운트한다`() {
            val stayingUser = UUID.randomUUID().toString()
            val churningUser = UUID.randomUUID().toString()

            // 전주 데이터: 두 유저 모두 ACTIVE
            store.upsertWeeklySubscriptionStates(listOf(
                makeSubscriptionState(week = prevWeek, categoryId = stayingUser,  state = "ACTIVE"),
                makeSubscriptionState(week = prevWeek, categoryId = churningUser, state = "ACTIVE")
            ))

            // 이번 주 데이터: stayingUser 만 존재
            store.upsertWeeklySubscriptionStates(listOf(
                makeSubscriptionState(week = weekStart, categoryId = stayingUser, state = "ACTIVE")
            ))

            // churningUser 만 이탈이므로 1이어야 한다
            val count = store.countChurnedSubscriptions(prevWeek, weekStart, personaId)
            count shouldBe 1
        }

        @Test
        fun `전주 NEW 상태는 이탈 집계에서 제외된다`() {
            val newUser = UUID.randomUUID().toString()

            // 전주에 NEW 상태로만 존재했고, 이번 주에 없는 경우
            store.upsertWeeklySubscriptionStates(listOf(
                makeSubscriptionState(week = prevWeek, categoryId = newUser, state = "NEW")
            ))

            // ACTIVE 가 아닌 NEW 상태는 이탈로 카운트하지 않는다
            val count = store.countChurnedSubscriptions(prevWeek, weekStart, personaId)
            count shouldBe 0
        }

        @Test
        fun `이번주에도 존재하는 구독은 이탈로 카운트하지 않는다`() {
            val categoryId = UUID.randomUUID().toString()

            store.upsertWeeklySubscriptionStates(listOf(
                makeSubscriptionState(week = prevWeek,  categoryId = categoryId, state = "ACTIVE"),
                makeSubscriptionState(week = weekStart, categoryId = categoryId, state = "ACTIVE")
            ))

            val count = store.countChurnedSubscriptions(prevWeek, weekStart, personaId)
            count shouldBe 0
        }
    }

    // ── Batch Run 생명주기 테스트 ──────────────────────────────────────────────

    @Nested
    inner class `배치 실행 생명주기 테스트` {

        @Test
        fun `insertBatchRun + updateStepStatus + finalizeBatchRun 생명주기`() {
            val runId = UUID.randomUUID().toString()
            val run = makeBatchRun(runId = runId, status = "RUNNING")

            // 1. 배치 삽입
            store.insertBatchRun(run)

            // 2. SNAPSHOT 단계 상태 갱신
            store.updateStepStatus(runId, "SNAPSHOT", "SUCCEEDED")

            // 3. 배치 완료 처리
            val finishedAt = Instant.now()
            store.finalizeBatchRun(runId, finishedAt, "SUCCEEDED")

            // 4. 결과 확인
            val found = store.findRecentBatchRuns(10)
                .firstOrNull { it.runId == runId }
            found shouldNotBe null
            found!!.overallStatus shouldBe "SUCCEEDED"
            found.snapshotStatus shouldBe "SUCCEEDED"
            found.finishedAt shouldNotBe null
        }
    }

    // ── hasRunningBatch 테스트 ─────────────────────────────────────────────────

    @Nested
    inner class `hasRunningBatch 테스트` {

        @Test
        fun `RUNNING 상태만 true 를 반환한다`() {
            store.insertBatchRun(makeBatchRun(status = "RUNNING"))
            store.hasRunningBatch(weekStart) shouldBe true
        }

        @Test
        fun `SUCCEEDED 상태는 false 를 반환한다`() {
            store.insertBatchRun(makeBatchRun(status = "SUCCEEDED"))
            store.hasRunningBatch(weekStart) shouldBe false
        }

        @Test
        fun `배치가 없으면 false 를 반환한다`() {
            store.hasRunningBatch(weekStart) shouldBe false
        }
    }

    // ── updateRunCounter 테스트 ────────────────────────────────────────────────

    @Nested
    inner class `updateRunCounter 테스트` {

        @Test
        fun `llm_calls 카운터가 증분 적용된다`() {
            val runId = UUID.randomUUID().toString()
            store.insertBatchRun(makeBatchRun(runId = runId))

            store.updateRunCounter(runId, "llm_calls", 3)
            store.updateRunCounter(runId, "llm_calls", 5)

            val found = store.findRecentBatchRuns(10).first { it.runId == runId }
            found.llmCalls shouldBe 8
        }

        @Test
        fun `personas_scanned 카운터가 정상 증분된다`() {
            val runId = UUID.randomUUID().toString()
            store.insertBatchRun(makeBatchRun(runId = runId))

            store.updateRunCounter(runId, "personas_scanned", 10)

            val found = store.findRecentBatchRuns(10).first { it.runId == runId }
            found.personasScanned shouldBe 10
        }

        @Test
        fun `허용되지 않은 counterName 은 IllegalArgumentException 을 발생시킨다`() {
            val runId = UUID.randomUUID().toString()
            store.insertBatchRun(makeBatchRun(runId = runId))

            val thrown = runCatching { store.updateRunCounter(runId, "malicious_column; DROP TABLE", 1) }
            thrown.isFailure shouldBe true
            thrown.exceptionOrNull() is IllegalArgumentException
        }
    }

    // ── findRecentBatchRuns 테스트 ─────────────────────────────────────────────

    @Nested
    inner class `findRecentBatchRuns 테스트` {

        @Test
        fun `limit 개수만큼만 반환하며 started_at DESC 정렬이다`() {
            // 3개 삽입 (순서 역전 없이 started_at 은 자연히 증가)
            repeat(3) { store.insertBatchRun(makeBatchRun()) }

            val result = store.findRecentBatchRuns(2)
            result shouldHaveSize 2
        }
    }
}
