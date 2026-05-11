package com.clipping.mcpserver.service.analytics

import com.clipping.mcpserver.service.analytics.dto.BatchRunResult
import com.clipping.mcpserver.store.analytics.dto.PersonaBatchRun
import com.clipping.mcpserver.store.analytics.dto.TriggerType
import com.clipping.mcpserver.service.analytics.exception.BatchAlreadyRunningException
import com.clipping.mcpserver.service.analytics.steps.WeeklyPersonaSnapshotStep
import com.clipping.mcpserver.store.PersonaAnalyticsStore
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 페르소나 주간 분석 배치 오케스트레이터.
 *
 * 매주 월요일 스케줄러 또는 관리자 수동 API 로 호출된다.
 * Slice 2 범위에서는 Step 1(Snapshot)만 실행하고, Steps 2~4 는 SKIPPED 로 기록한다.
 *
 * - 같은 weekStart 에 이미 RUNNING 상태인 배치가 있으면 [BatchAlreadyRunningException] 을 던진다.
 * - Step 1 에서 DataAccessException 이 발생하면 배치를 FAILED 로 마무리한다.
 * - finally 블록에서 persona-live / persona-trends / persona-signals 캐시를 항상 clear 한다.
 */
@Component
class PersonaAnalyticsMondayBatch(
    private val snapshotStep: WeeklyPersonaSnapshotStep,
    private val analyticsStore: PersonaAnalyticsStore,
    private val cacheManager: CacheManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 주간 배치를 실행한다.
     *
     * @param weekStart   집계 대상 주의 월요일 날짜
     * @param triggerType SCHEDULED / MANUAL / BACKFILL
     * @param triggeredBy MANUAL 실행 시 호출한 관리자 식별자 (SCHEDULED 이면 null)
     * @return 실행 결과 (runId + overallStatus)
     * @throws BatchAlreadyRunningException 같은 weekStart 로 RUNNING 배치가 이미 존재할 때
     */
    fun run(weekStart: LocalDate, triggerType: TriggerType, triggeredBy: String?): BatchRunResult {
        // 같은 주차에 이미 실행 중인 배치가 있는지 확인한다.
        if (analyticsStore.hasRunningBatch(weekStart)) {
            throw BatchAlreadyRunningException(weekStart)
        }

        val runId = UUID.randomUUID().toString()
        val startedAt = Instant.now()

        // RUNNING 상태로 배치 이력을 삽입한다.
        analyticsStore.insertBatchRun(
            PersonaBatchRun(
                id = UUID.randomUUID().toString(),
                runId = runId,
                triggerType = triggerType,
                weekStart = weekStart,
                startedAt = startedAt,
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
        )

        log.info("PersonaAnalyticsMondayBatch 시작: runId={}, weekStart={}, trigger={}", runId, weekStart, triggerType)

        try {
            // Step 1: 주간 페르소나 스냅샷 실행한다.
            snapshotStep.execute(weekStart, runId)
            analyticsStore.updateStepStatus(runId, "SNAPSHOT", "SUCCESS")

            // Steps 2~4: Slice 2 범위 외, SKIPPED 로 기록한다.
            analyticsStore.updateStepStatus(runId, "ANOMALY", "SKIPPED")
            analyticsStore.updateStepStatus(runId, "CLUSTERING", "SKIPPED")
            analyticsStore.updateStepStatus(runId, "REPORT", "SKIPPED")

            // 배치를 SUCCESS 로 마무리한다.
            analyticsStore.finalizeBatchRun(runId, Instant.now(), "SUCCESS")
            log.info("PersonaAnalyticsMondayBatch 완료: runId={}, weekStart={}", runId, weekStart)
            return BatchRunResult(runId = runId, overallStatus = "SUCCESS")
        } catch (e: org.springframework.dao.DataAccessException) {
            // DB 오류 발생 시 배치를 FAILED 로 마무리한다.
            log.error("PersonaAnalyticsMondayBatch 실패: runId={}, error={}", runId, e.message)
            analyticsStore.finalizeBatchRun(runId, Instant.now(), "FAILED", "DB: ${e.message}")
            return BatchRunResult(runId = runId, overallStatus = "FAILED")
        } finally {
            // 배치 성공/실패 여부와 무관하게 캐시를 clear 한다.
            listOf("persona-live", "persona-trends", "persona-signals").forEach { cacheName ->
                cacheManager.getCache(cacheName)?.clear()
            }
        }
    }
}
