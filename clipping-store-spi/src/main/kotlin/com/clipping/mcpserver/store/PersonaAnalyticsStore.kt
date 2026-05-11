package com.clipping.mcpserver.store

import com.clipping.mcpserver.store.analytics.dto.PersonaBatchRun
import com.clipping.mcpserver.store.analytics.dto.WeeklyPersonaSnapshot
import com.clipping.mcpserver.store.analytics.dto.WeeklySubscriptionState
import java.time.Instant
import java.time.LocalDate

/**
 * 페르소나 분석 집계 데이터 저장소 포트.
 *
 * Slice 2 범위:
 *   - weekly_persona_snapshot: 페르소나별 주간 집계 스냅샷 upsert/조회
 *   - weekly_persona_subscription_state: 카테고리 구독 단위 주간 상태 배치 upsert/조회
 *   - persona_batch_run: 배치 실행 이력 삽입/상태 갱신/카운터 증분
 *
 * Slice 3: persona_anomaly 관련 메서드 추가 예정
 * Slice 5: persona_embedding, persona_cluster, persona_cluster_member 관련 메서드 추가 예정
 *
 * 모든 upsert 메서드는 멱등(idempotent) 설계를 따르므로 동일 입력을 여러 번 호출해도
 * DB 상태가 동일해야 한다.
 */
interface PersonaAnalyticsStore {

    // ── Weekly Snapshot ──────────────────────────────────────────────────────

    /**
     * 주간 스냅샷을 upsert 한다.
     *
     * (week_start, persona_id) 복합 유니크 제약 기반으로 충돌 시 기존 행을 덮어쓴다.
     * 동일 주차에 대해 재계산이 발생해도 중복 행이 생기지 않는다.
     */
    fun upsertWeeklySnapshot(snapshot: WeeklyPersonaSnapshot)

    /**
     * 카테고리 구독 단위 주간 상태 목록을 배치 upsert 한다.
     *
     * (week_start, persona_id, category_id) 복합 PK 기반으로 충돌 시 기존 행을 덮어쓴다.
     * 빈 리스트 입력 시 아무 것도 실행하지 않는다.
     */
    fun upsertWeeklySubscriptionStates(states: List<WeeklySubscriptionState>)

    /**
     * 특정 week_start 에 해당하는 모든 스냅샷을 조회한다.
     *
     * @param weekStart 조회 기준 주 시작일 (월요일 기준)
     * @return 해당 주차의 스냅샷 목록 (없으면 빈 리스트)
     */
    fun findSnapshotsByWeek(weekStart: LocalDate): List<WeeklyPersonaSnapshot>

    /**
     * [fromWeek, toWeek] 범위에 해당하는 스냅샷 목록을 week_start DESC 정렬로 반환한다.
     *
     * @param fromWeek 시작 주차 (포함)
     * @param toWeek   종료 주차 (포함)
     */
    fun findSnapshotsByRange(fromWeek: LocalDate, toWeek: LocalDate): List<WeeklyPersonaSnapshot>

    /**
     * 전주에 해당하는 특정 페르소나의 카테고리별 구독 상태 목록을 반환한다.
     *
     * @param prevWeek  전주 week_start
     * @param personaId 대상 페르소나 ID
     */
    fun findPreviousWeekSubscriptionStates(prevWeek: LocalDate, personaId: String): List<WeeklySubscriptionState>

    /**
     * 이탈 구독자 수를 계산한다.
     *
     * prevWeek 에 ACTIVE 상태였지만 thisWeek 에 아무 행도 없는 카테고리 구독 수를 반환한다.
     *
     * @param prevWeek  전주 week_start
     * @param thisWeek  이번 주 week_start
     * @param personaId 대상 페르소나 ID
     */
    fun countChurnedSubscriptions(prevWeek: LocalDate, thisWeek: LocalDate, personaId: String): Int

    // ── Batch Run ─────────────────────────────────────────────────────────────

    /**
     * 새 배치 실행 레코드를 삽입한다.
     *
     * run_id 유니크 제약이 있으므로 동일 run_id 로 중복 삽입 시 예외가 발생한다.
     */
    fun insertBatchRun(run: PersonaBatchRun)

    /**
     * 특정 배치 실행의 단계 상태를 갱신한다.
     *
     * @param runId    대상 run_id
     * @param stepName 단계명: SNAPSHOT | ANOMALY | CLUSTERING | REPORT
     * @param status   갱신할 상태값 (RUNNING | SUCCEEDED | FAILED 등)
     */
    fun updateStepStatus(runId: String, stepName: String, status: String)

    /**
     * 배치 실행을 완료(또는 실패) 처리한다.
     *
     * finished_at, overall_status, error_message 를 한 번에 갱신한다.
     *
     * @param runId         대상 run_id
     * @param finishedAt    완료 시각
     * @param overallStatus 최종 상태 (SUCCEEDED | FAILED)
     * @param errorMessage  실패 시 오류 메시지 (성공 시 null)
     */
    fun finalizeBatchRun(runId: String, finishedAt: Instant, overallStatus: String, errorMessage: String? = null)

    /**
     * 해당 week_start 에 RUNNING 상태인 배치가 있는지 확인한다.
     *
     * @return RUNNING 상태 행이 1개 이상이면 true
     */
    fun hasRunningBatch(weekStart: LocalDate): Boolean

    /**
     * 최근 배치 실행 목록을 started_at DESC 정렬로 반환한다.
     *
     * @param limit 반환할 최대 건수
     */
    fun findRecentBatchRuns(limit: Int): List<PersonaBatchRun>

    /**
     * 배치 실행의 숫자형 카운터를 증분한다.
     *
     * 허용 카운터: personas_scanned, anomalies_created, anomalies_resolved,
     *              embedding_calls, llm_calls, llm_tokens_used
     *
     * @param runId       대상 run_id
     * @param counterName 증분할 컬럼명
     * @param increment   증분값 (양수)
     */
    fun updateRunCounter(runId: String, counterName: String, increment: Int)
}
