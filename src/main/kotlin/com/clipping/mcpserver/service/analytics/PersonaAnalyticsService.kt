package com.clipping.mcpserver.service.analytics

import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.service.analytics.dto.LiveSnapshotResponse
import com.clipping.mcpserver.service.analytics.dto.PersonaBatchRunResponse
import com.clipping.mcpserver.store.analytics.dto.PersonaBatchRun
import com.clipping.mcpserver.service.analytics.dto.SignalsResponse
import com.clipping.mcpserver.service.analytics.dto.WeeklyTrendsResponse
import com.clipping.mcpserver.store.PersonaAnalyticsStore
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 페르소나 분석 데이터 조회 진입점.
 *
 * 조회 전용 — 쓰기 연산은 Slice 2 이후 추가될 배치/훅/워크플로우 서비스가 담당한다.
 *
 * 캐시 전략:
 *   - persona-live: 5 분 Caffeine TTL (CacheConfig 참고). 주간 배치 (Slice 2 이후)
 *     종료 시 명시적으로 clear 한다. 5 분 TTL 은 안전망.
 *
 * Slice 별 점진 확장:
 *   - Slice 1 (이 파일): getLiveSnapshot()
 *   - Slice 2: getWeeklyTrends, getRecentBatchRuns, runBackfill 추가
 *   - Slice 3: getActiveAnomalies, resolveAnomaly, triggerManualBatch 추가
 *   - Slice 4: getCustomKeywords 추가
 *   - Slice 5: getCustomClusters, getPresetCandidates 추가
 */
@Service
class PersonaAnalyticsService(
    private val readModel: PersonaAnalyticsReadModel,
    private val analyticsStore: PersonaAnalyticsStore
) {

    /**
     * 실시간 페르소나 현황 스냅샷.
     *
     * Caffeine 기반 5 분 캐시 (key = 'snapshot' singleton).
     * Slice 2 이후 배치가 데이터를 갱신할 때 명시적으로 evict 한다.
     */
    @Cacheable(cacheNames = [CACHE_LIVE], key = "'snapshot'")
    fun getLiveSnapshot(): LiveSnapshotResponse {
        val totals = readModel.computeLiveTotals()
        val portfolio = readModel.loadPresetPortfolio()
        val customSummary = readModel.loadCustomSummary()

        return LiveSnapshotResponse(
            totals = totals,
            presetPortfolio = portfolio,
            customSummary = customSummary,
            asOf = Instant.now()
        )
    }

    /**
     * N주 기간의 페르소나별 주간 트렌드 시리즈.
     *
     * Caffeine 기반 30분 캐시 (key = weeks). 트렌드 데이터는 주 배치 후 변경되므로
     * 30분 TTL 은 충분한 안전망이다.
     *
     * @param weeks 조회할 주 수 (1~52). 범위 외 입력 시 IllegalArgumentException.
     */
    @Cacheable(cacheNames = [CACHE_TRENDS], key = "#weeks")
    fun getWeeklyTrends(weeks: Int = 12): WeeklyTrendsResponse {
        // 조회 범위 유효성을 검증한다.
        ensureValid(weeks in 1..52) { "weeks 는 1 이상 52 이하여야 합니다. 입력값: $weeks" }
        return readModel.buildWeeklyTrends(weeks)
    }

    /**
     * 위험·성장 신호를 통합 조회한다.
     *
     * 5분 캐시 (`CACHE_SIGNALS`). 주간 배치 종료 시 명시적으로 evict 한다.
     *
     * @param lookbackWeeks 유휴/지속 주차 카운팅 범위 (1~12). 기본 4.
     */
    @Cacheable(cacheNames = [CACHE_SIGNALS], key = "#lookbackWeeks")
    fun getSignals(lookbackWeeks: Int = 4): SignalsResponse {
        ensureValid(lookbackWeeks in 1..12) {
            "lookbackWeeks 는 1 이상 12 이하여야 합니다. 입력값: $lookbackWeeks"
        }
        return readModel.loadSignals(lookbackWeeks)
    }

    /**
     * 최근 배치 실행 이력을 조회한다.
     *
     * @param limit 반환할 최대 건수
     */
    fun getRecentBatchRuns(limit: Int = 10): List<PersonaBatchRunResponse> =
        analyticsStore.findRecentBatchRuns(limit).map { it.toResponse() }

    private fun PersonaBatchRun.toResponse(): PersonaBatchRunResponse =
        PersonaBatchRunResponse(
            id = id,
            runId = runId,
            triggerType = triggerType.name,
            weekStart = weekStart,
            startedAt = startedAt,
            finishedAt = finishedAt,
            overallStatus = overallStatus,
            snapshotStatus = snapshotStatus,
            anomalyStatus = anomalyStatus,
            clusteringStatus = clusteringStatus,
            reportStatus = reportStatus,
            personasScanned = personasScanned,
            anomaliesCreated = anomaliesCreated,
            anomaliesResolved = anomaliesResolved,
            embeddingCalls = embeddingCalls,
            llmCalls = llmCalls,
            llmTokensUsed = llmTokensUsed,
            errorMessage = errorMessage,
            errorStep = errorStep,
            triggeredBy = triggeredBy,
        )

    companion object {
        const val CACHE_LIVE = "persona-live"
        const val CACHE_TRENDS = "persona-trends"
        const val CACHE_SIGNALS = "persona-signals"
    }
}
