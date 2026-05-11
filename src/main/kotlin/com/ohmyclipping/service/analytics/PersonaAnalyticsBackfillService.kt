package com.ohmyclipping.service.analytics

import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.service.analytics.dto.BackfillResult
import com.ohmyclipping.store.analytics.dto.PersonaBatchRun
import com.ohmyclipping.store.analytics.dto.TriggerType
import com.ohmyclipping.service.AuditActorResolver
import com.ohmyclipping.service.analytics.steps.WeeklyPersonaSnapshotStep
import com.ohmyclipping.service.analytics.time.AnalyticsTime
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.PersonaAnalyticsStore
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 페르소나 분석 백필 서비스.
 *
 * 지정한 주 수만큼 과거 주간 스냅샷을 재생성한다.
 * 관리자가 데이터 누락이나 마이그레이션 후 히스토리를 채울 때 사용한다.
 *
 * 트리거 타입은 BACKFILL 으로 기록되며, 실행 이력은 persona_batch_run 에 남는다.
 * 감사 로그에는 실행자 ID 와 처리된 주·행 수가 함께 기록된다.
 */
@Service
class PersonaAnalyticsBackfillService(
    private val snapshotStep: WeeklyPersonaSnapshotStep,
    private val analyticsStore: PersonaAnalyticsStore,
    private val auditLogStore: AuditLogStore,
    private val cacheManager: CacheManager,
    private val auditActorResolver: AuditActorResolver
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 과거 N주 치 주간 스냅샷을 백필한다.
     *
     * @param weeks      처리할 주 수. 1 이상 52 이하여야 한다.
     * @param principal  Spring `authentication.name` (username). 감사 로그 기록용.
     *                   null 이면 시스템 호출로 간주되고 actor_name="system" 으로 기록.
     * @return 처리 결과 DTO
     * @throws IllegalArgumentException weeks 가 유효 범위(1~52) 를 벗어나는 경우
     */
    fun backfill(weeks: Int, principal: String? = null): BackfillResult {
        // 주 수 유효성을 검증한다.
        ensureValid(weeks in 1..52) { "weeks 는 1 이상 52 이하여야 합니다. 입력값: $weeks" }

        // principal 을 한 번만 resolver 로 풀어두고 이후 로깅·감사·trigger 메타에 재사용한다.
        val actor = auditActorResolver.resolve(principal)

        val startedAt = Instant.now()
        val runId = UUID.randomUUID().toString()

        // 배치 실행 레코드를 RUNNING 상태로 삽입한다.
        val run = buildInitialBatchRun(runId, actor.name)
        analyticsStore.insertBatchRun(run)

        log.info("PersonaAnalyticsBackfillService 시작: weeks={}, principal={}, runId={}", weeks, actor.name, runId)

        var totalRows = 0
        var lastPersonaCount = 0

        try {
            // 과거 N주를 오래된 순(i=weeks)부터 최근(i=1)으로 순회한다.
            for (i in weeks downTo 1) {
                val targetWeek = AnalyticsTime.previousWeekStart().minusWeeks((i - 1).toLong())
                // 해당 주의 스냅샷 스텝을 실행한다.
                lastPersonaCount = snapshotStep.execute(targetWeek, runId)
                totalRows += lastPersonaCount
            }

            val durationMs = Duration.between(startedAt, Instant.now()).toMillis()

            // 배치 실행을 SUCCESS 로 완료 처리한다.
            analyticsStore.finalizeBatchRun(runId, Instant.now(), "SUCCESS")

            // 감사 로그에 실행자와 처리 규모를 기록한다.
            recordAuditLog(actor, runId, weeks, totalRows)

            log.info("PersonaAnalyticsBackfillService 완료: weeks={}, rows={}, durationMs={}", weeks, totalRows, durationMs)

            return BackfillResult(
                weeksProcessed = weeks,
                personasAggregated = lastPersonaCount,
                snapshotRowsCreated = totalRows,
                durationMs = durationMs
            )
        } catch (e: DataAccessException) {
            // DB 오류 발생 시 배치를 FAILED 로 마무리해 RUNNING 상태로 영구 잔류하는 것을 방지한다.
            log.error("PersonaAnalyticsBackfillService 실패: runId={}, error={}", runId, e.message)
            analyticsStore.finalizeBatchRun(runId, Instant.now(), "FAILED", "DB: ${e.message}")
            throw e
        } finally {
            // 백필이 방금 갱신한 스냅샷을 사용자에게 즉시 반영하기 위해 관련 캐시를 전부 비운다.
            listOf("persona-live", "persona-trends", "persona-signals").forEach { cacheName ->
                cacheManager.getCache(cacheName)?.clear()
            }
        }
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    /**
     * 초기 RUNNING 상태의 PersonaBatchRun DTO 를 생성한다.
     * `triggeredBy` 에는 resolver 가 돌려준 actorName 을 그대로 쓰며, 이는 실제 username
     * 또는 "system" (principal=null) 중 하나다.
     */
    private fun buildInitialBatchRun(runId: String, triggeredBy: String): PersonaBatchRun =
        PersonaBatchRun(
            id = UUID.randomUUID().toString(),
            runId = runId,
            triggerType = TriggerType.BACKFILL,
            weekStart = AnalyticsTime.previousWeekStart(),
            startedAt = Instant.now(),
            finishedAt = null,
            overallStatus = "RUNNING",
            snapshotStatus = null,
            anomalyStatus = null,
            clusteringStatus = null,
            reportStatus = null,
            personasScanned = 0,
            anomaliesCreated = 0,
            anomaliesResolved = 0,
            embeddingCalls = 0,
            llmCalls = 0,
            llmTokensUsed = 0,
            errorMessage = null,
            errorStep = null,
            triggeredBy = triggeredBy
        )

    /**
     * 백필 실행 결과를 감사 로그에 기록한다.
     */
    private fun recordAuditLog(
        actor: com.ohmyclipping.service.ResolvedActor,
        runId: String,
        weeks: Int,
        rowsCreated: Int
    ) {
        auditLogStore.log(
            actorId = actor.id,
            actorName = actor.name,
            action = "PERSONA_ANALYTICS_BACKFILL",
            targetType = "PERSONA_BATCH_RUN",
            targetId = runId,
            detail = "weeks=$weeks rowsCreated=$rowsCreated"
        )
    }
}
