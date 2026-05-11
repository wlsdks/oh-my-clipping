package com.ohmyclipping.store

import com.ohmyclipping.store.analytics.dto.PersonaBatchRun
import com.ohmyclipping.store.analytics.dto.TriggerType
import com.ohmyclipping.store.analytics.dto.WeeklyPersonaSnapshot
import com.ohmyclipping.store.analytics.dto.WeeklySubscriptionState
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JDBC 기반 PersonaAnalyticsStore 어댑터.
 *
 * Slice 2: weekly_persona_snapshot, weekly_persona_subscription_state,
 *          persona_batch_run 관련 메서드를 구현한다.
 *
 * 모든 upsert 는 PostgreSQL 호환 `INSERT ... ON CONFLICT ... DO UPDATE SET ... = EXCLUDED.*`
 * 패턴을 사용한다. H2 테스트 환경에서는 MODE=PostgreSQL 설정으로 동작이 보장된다.
 *
 * 허용 카운터 목록은 [ALLOWED_COUNTERS] 에 정의되어 있으며,
 * 목록에 없는 counterName 이 들어오면 [IllegalArgumentException] 을 발생시킨다.
 */
@Repository
class JdbcPersonaAnalyticsStore(
    private val jdbc: JdbcTemplate
) : PersonaAnalyticsStore {

    companion object {
        /** updateRunCounter 에서 허용하는 컬럼명 목록. SQL 인젝션 방지용 화이트리스트. */
        private val ALLOWED_COUNTERS = setOf(
            "personas_scanned",
            "anomalies_created",
            "anomalies_resolved",
            "embedding_calls",
            "llm_calls",
            "llm_tokens_used"
        )

        /** stepName → DB 컬럼명 매핑. */
        private val STEP_COLUMN_MAP = mapOf(
            "SNAPSHOT"   to "snapshot_status",
            "ANOMALY"    to "anomaly_status",
            "CLUSTERING" to "clustering_status",
            "REPORT"     to "report_status"
        )
    }

    // ── Row Mappers ───────────────────────────────────────────────────────────

    private val snapshotRowMapper = RowMapper<WeeklyPersonaSnapshot> { rs, _ ->
        WeeklyPersonaSnapshot(
            id              = rs.getString("id"),
            weekStart       = rs.getDate("week_start").toLocalDate(),
            personaId       = rs.getString("persona_id"),
            personaName     = rs.getString("persona_name"),
            isPreset        = rs.getBoolean("is_preset"),
            activeSubs      = rs.getInt("active_subs"),
            newSubs         = rs.getInt("new_subs"),
            churnedSubs     = rs.getInt("churned_subs"),
            deliveredCount  = rs.getInt("delivered_count"),
            deliveredItems  = rs.getInt("delivered_items"),
            engagedUsers    = rs.getInt("engaged_users"),
            totalClicks     = rs.getInt("total_clicks"),
            totalBookmarks  = rs.getInt("total_bookmarks"),
            engagementRate  = rs.getDouble("engagement_rate"),
            clickPerDelivery = rs.getDouble("click_per_delivery"),
            createdAt       = rs.getTimestamp("created_at").toInstant()
        )
    }

    private val subscriptionStateRowMapper = RowMapper<WeeklySubscriptionState> { rs, _ ->
        WeeklySubscriptionState(
            weekStart             = rs.getDate("week_start").toLocalDate(),
            personaId             = rs.getString("persona_id"),
            categoryId            = rs.getString("category_id"),
            state                 = rs.getString("state"),
            deliveryOpportunities = rs.getInt("delivery_opportunities"),
            deliveredCount        = rs.getInt("delivered_count"),
            clicksInWeek          = rs.getInt("clicks_in_week"),
            bookmarksInWeek       = rs.getInt("bookmarks_in_week")
        )
    }

    // ── Weekly Snapshot ───────────────────────────────────────────────────────

    override fun upsertWeeklySnapshot(snapshot: WeeklyPersonaSnapshot) {
        // UPDATE 먼저 시도한다. 영향 행이 없으면 INSERT 를 시도한다.
        val updated = jdbc.update(
            """
            UPDATE weekly_persona_snapshot SET
                persona_name       = ?,
                is_preset          = ?,
                active_subs        = ?,
                new_subs           = ?,
                churned_subs       = ?,
                delivered_count    = ?,
                delivered_items    = ?,
                engaged_users      = ?,
                total_clicks       = ?,
                total_bookmarks    = ?,
                engagement_rate    = ?,
                click_per_delivery = ?
            WHERE week_start = ? AND persona_id = ?
            """.trimIndent(),
            snapshot.personaName,
            snapshot.isPreset,
            snapshot.activeSubs,
            snapshot.newSubs,
            snapshot.churnedSubs,
            snapshot.deliveredCount,
            snapshot.deliveredItems,
            snapshot.engagedUsers,
            snapshot.totalClicks,
            snapshot.totalBookmarks,
            snapshot.engagementRate,
            snapshot.clickPerDelivery,
            Date.valueOf(snapshot.weekStart),
            snapshot.personaId
        )
        // 기존 행이 없는 경우에만 INSERT 를 시도한다.
        if (updated == 0) {
            val id = snapshot.id.ifBlank { UUID.randomUUID().toString() }
            try {
                jdbc.update(
                    """
                    INSERT INTO weekly_persona_snapshot (
                        id, week_start, persona_id, persona_name, is_preset,
                        active_subs, new_subs, churned_subs,
                        delivered_count, delivered_items,
                        engaged_users, total_clicks, total_bookmarks,
                        engagement_rate, click_per_delivery, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    id,
                    Date.valueOf(snapshot.weekStart),
                    snapshot.personaId,
                    snapshot.personaName,
                    snapshot.isPreset,
                    snapshot.activeSubs,
                    snapshot.newSubs,
                    snapshot.churnedSubs,
                    snapshot.deliveredCount,
                    snapshot.deliveredItems,
                    snapshot.engagedUsers,
                    snapshot.totalClicks,
                    snapshot.totalBookmarks,
                    snapshot.engagementRate,
                    snapshot.clickPerDelivery,
                    Timestamp.from(snapshot.createdAt)
                )
            } catch (e: DuplicateKeyException) {
                // 동시 실행으로 이미 행이 존재 — UPDATE 재시도
                jdbc.update(
                    """
                    UPDATE weekly_persona_snapshot SET
                        persona_name       = ?,
                        is_preset          = ?,
                        active_subs        = ?,
                        new_subs           = ?,
                        churned_subs       = ?,
                        delivered_count    = ?,
                        delivered_items    = ?,
                        engaged_users      = ?,
                        total_clicks       = ?,
                        total_bookmarks    = ?,
                        engagement_rate    = ?,
                        click_per_delivery = ?
                    WHERE week_start = ? AND persona_id = ?
                    """.trimIndent(),
                    snapshot.personaName,
                    snapshot.isPreset,
                    snapshot.activeSubs,
                    snapshot.newSubs,
                    snapshot.churnedSubs,
                    snapshot.deliveredCount,
                    snapshot.deliveredItems,
                    snapshot.engagedUsers,
                    snapshot.totalClicks,
                    snapshot.totalBookmarks,
                    snapshot.engagementRate,
                    snapshot.clickPerDelivery,
                    Date.valueOf(snapshot.weekStart),
                    snapshot.personaId
                )
            }
        }
    }

    override fun upsertWeeklySubscriptionStates(states: List<WeeklySubscriptionState>) {
        // 빈 리스트면 불필요한 DB 호출을 생략한다.
        if (states.isEmpty()) return

        // 각 상태에 대해 UPDATE → 0행이면 INSERT 패턴을 적용한다.
        for (state in states) {
            upsertSingleSubscriptionState(state)
        }
    }

    /** 단일 구독 상태 row 를 upsert 한다. UPDATE 후 영향 행이 없으면 INSERT 시도, 동시 경합 시 UPDATE 재시도. */
    private fun upsertSingleSubscriptionState(state: WeeklySubscriptionState) {
        val updated = jdbc.update(
            """
            UPDATE weekly_persona_subscription_state SET
                state                  = ?,
                delivery_opportunities = ?,
                delivered_count        = ?,
                clicks_in_week         = ?,
                bookmarks_in_week      = ?
            WHERE week_start = ? AND persona_id = ? AND category_id = ?
            """.trimIndent(),
            state.state,
            state.deliveryOpportunities,
            state.deliveredCount,
            state.clicksInWeek,
            state.bookmarksInWeek,
            Date.valueOf(state.weekStart),
            state.personaId,
            state.categoryId
        )
        // 기존 행이 없는 경우에만 INSERT 를 시도한다.
        if (updated == 0) {
            try {
                jdbc.update(
                    """
                    INSERT INTO weekly_persona_subscription_state (
                        week_start, persona_id, category_id, state,
                        delivery_opportunities, delivered_count, clicks_in_week, bookmarks_in_week
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    Date.valueOf(state.weekStart),
                    state.personaId,
                    state.categoryId,
                    state.state,
                    state.deliveryOpportunities,
                    state.deliveredCount,
                    state.clicksInWeek,
                    state.bookmarksInWeek
                )
            } catch (e: DuplicateKeyException) {
                // 동시 실행으로 이미 행이 존재 — UPDATE 재시도
                jdbc.update(
                    """
                    UPDATE weekly_persona_subscription_state SET
                        state                  = ?,
                        delivery_opportunities = ?,
                        delivered_count        = ?,
                        clicks_in_week         = ?,
                        bookmarks_in_week      = ?
                    WHERE week_start = ? AND persona_id = ? AND category_id = ?
                    """.trimIndent(),
                    state.state,
                    state.deliveryOpportunities,
                    state.deliveredCount,
                    state.clicksInWeek,
                    state.bookmarksInWeek,
                    Date.valueOf(state.weekStart),
                    state.personaId,
                    state.categoryId
                )
            }
        }
    }

    override fun findSnapshotsByWeek(weekStart: LocalDate): List<WeeklyPersonaSnapshot> =
        jdbc.query(
            "SELECT * FROM weekly_persona_snapshot WHERE week_start = ?",
            snapshotRowMapper,
            Date.valueOf(weekStart)
        )

    override fun findSnapshotsByRange(fromWeek: LocalDate, toWeek: LocalDate): List<WeeklyPersonaSnapshot> =
        // week_start DESC 정렬로 최신 주차를 먼저 반환한다.
        jdbc.query(
            """
            SELECT * FROM weekly_persona_snapshot
            WHERE week_start >= ? AND week_start <= ?
            ORDER BY week_start DESC
            """.trimIndent(),
            snapshotRowMapper,
            Date.valueOf(fromWeek),
            Date.valueOf(toWeek)
        )

    override fun findPreviousWeekSubscriptionStates(
        prevWeek: LocalDate,
        personaId: String
    ): List<WeeklySubscriptionState> =
        jdbc.query(
            """
            SELECT * FROM weekly_persona_subscription_state
            WHERE week_start = ? AND persona_id = ?
            """.trimIndent(),
            subscriptionStateRowMapper,
            Date.valueOf(prevWeek),
            personaId
        )

    override fun countChurnedSubscriptions(prevWeek: LocalDate, thisWeek: LocalDate, personaId: String): Int {
        // 전주 ACTIVE 상태였지만 이번 주에 어떤 상태로도 존재하지 않는 구독(카테고리) 수를 집계한다.
        return jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM weekly_persona_subscription_state prev
            WHERE prev.week_start = ?
              AND prev.persona_id = ?
              AND prev.state = 'ACTIVE'
              AND NOT EXISTS (
                  SELECT 1
                  FROM weekly_persona_subscription_state curr
                  WHERE curr.week_start = ?
                    AND curr.persona_id = prev.persona_id
                    AND curr.category_id = prev.category_id
              )
            """.trimIndent(),
            Int::class.java,
            Date.valueOf(prevWeek),
            personaId,
            Date.valueOf(thisWeek)
        ) ?: 0
    }

    // ── Batch Run ─────────────────────────────────────────────────────────────

    override fun insertBatchRun(run: PersonaBatchRun) {
        // run_id 유니크 제약으로 중복 배치 삽입을 DB 레벨에서 차단한다.
        val id = run.id.ifBlank { UUID.randomUUID().toString() }
        jdbc.update(
            """
            INSERT INTO persona_batch_run (
                id, run_id, trigger_type, week_start, started_at,
                finished_at, overall_status,
                snapshot_status, anomaly_status, clustering_status, report_status,
                personas_scanned, anomalies_created, anomalies_resolved,
                embedding_calls, llm_calls, llm_tokens_used,
                error_message, error_step, triggered_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            run.runId,
            run.triggerType.name,
            Date.valueOf(run.weekStart),
            Timestamp.from(run.startedAt),
            run.finishedAt?.let { Timestamp.from(it) },
            run.overallStatus,
            run.snapshotStatus,
            run.anomalyStatus,
            run.clusteringStatus,
            run.reportStatus,
            run.personasScanned,
            run.anomaliesCreated,
            run.anomaliesResolved,
            run.embeddingCalls,
            run.llmCalls,
            run.llmTokensUsed,
            run.errorMessage,
            run.errorStep,
            run.triggeredBy
        )
    }

    override fun updateStepStatus(runId: String, stepName: String, status: String) {
        // stepName 을 DB 컬럼명으로 변환한다. 미등록 단계는 예외를 발생시킨다.
        val column = STEP_COLUMN_MAP[stepName.uppercase()]
            ?: throw IllegalStateException("알 수 없는 단계명: $stepName. 허용값: ${STEP_COLUMN_MAP.keys}")
        jdbc.update(
            "UPDATE persona_batch_run SET $column = ? WHERE run_id = ?",
            status,
            runId
        )
    }

    override fun finalizeBatchRun(
        runId: String,
        finishedAt: Instant,
        overallStatus: String,
        errorMessage: String?
    ) {
        // 완료 처리: finished_at, overall_status, error_message 를 한 번에 갱신한다.
        jdbc.update(
            """
            UPDATE persona_batch_run
            SET finished_at    = ?,
                overall_status = ?,
                error_message  = ?
            WHERE run_id = ?
            """.trimIndent(),
            Timestamp.from(finishedAt),
            overallStatus,
            errorMessage,
            runId
        )
    }

    override fun hasRunningBatch(weekStart: LocalDate): Boolean {
        // RUNNING 상태 행이 하나라도 있으면 true 를 반환한다.
        val count = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM persona_batch_run
            WHERE week_start = ? AND overall_status = 'RUNNING'
            """.trimIndent(),
            Int::class.java,
            Date.valueOf(weekStart)
        ) ?: 0
        return count > 0
    }

    override fun findRecentBatchRuns(limit: Int): List<PersonaBatchRun> =
        jdbc.query(
            "SELECT * FROM persona_batch_run ORDER BY started_at DESC LIMIT ?",
            { rs, _ -> mapBatchRunOrNull(rs) },
            limit
        ).mapNotNull { it }

    override fun updateRunCounter(runId: String, counterName: String, increment: Int) {
        // SQL 인젝션 방지: 화이트리스트에 없는 counterName 을 거부한다.
        require(counterName in ALLOWED_COUNTERS) {
            "허용되지 않은 카운터: $counterName. 허용값: $ALLOWED_COUNTERS"
        }
        jdbc.update(
            "UPDATE persona_batch_run SET $counterName = $counterName + ? WHERE run_id = ?",
            increment,
            runId
        )
    }

    private fun mapBatchRunOrNull(rs: ResultSet): PersonaBatchRun? {
        val id = rs.getString("id") ?: return null
        val runId = rs.getString("run_id") ?: return null
        val triggerType = parseTriggerType(rs.getString("trigger_type")) ?: return null
        val weekStart = rs.getDate("week_start")?.toLocalDate() ?: return null
        val startedAt = rs.getTimestamp("started_at")?.toInstant() ?: return null

        return PersonaBatchRun(
            id                = id,
            runId             = runId,
            triggerType       = triggerType,
            weekStart         = weekStart,
            startedAt         = startedAt,
            finishedAt        = rs.getTimestamp("finished_at")?.toInstant(),
            overallStatus     = rs.getString("overall_status") ?: "UNKNOWN",
            snapshotStatus    = rs.getString("snapshot_status"),
            anomalyStatus     = rs.getString("anomaly_status"),
            clusteringStatus  = rs.getString("clustering_status"),
            reportStatus      = rs.getString("report_status"),
            personasScanned   = rs.getInt("personas_scanned"),
            anomaliesCreated  = rs.getInt("anomalies_created"),
            anomaliesResolved = rs.getInt("anomalies_resolved"),
            embeddingCalls    = rs.getInt("embedding_calls"),
            llmCalls          = rs.getInt("llm_calls"),
            llmTokensUsed     = rs.getInt("llm_tokens_used"),
            errorMessage      = rs.getString("error_message"),
            errorStep         = rs.getString("error_step"),
            triggeredBy       = rs.getString("triggered_by")
        )
    }

    private fun parseTriggerType(value: String?): TriggerType? =
        if (value.isNullOrBlank()) {
            null
        } else {
            runCatching { TriggerType.valueOf(value) }.getOrNull()
        }
}
