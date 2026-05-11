package com.ohmyclipping.store

import com.ohmyclipping.service.dto.pipeline.PipelineRunEntity
import com.ohmyclipping.service.dto.pipeline.PipelineStepTraceEntity
import java.time.Instant

/**
 * 파이프라인 실행 이력과 단계 추적 데이터를 관리하는 저장소 인터페이스.
 * pipeline_runs / pipeline_step_traces 테이블에 대한 CRUD를 제공한다.
 */
interface PipelineRunStore {

    /** 새 파이프라인 실행을 저장한다. id가 비어있으면 UUID를 자동 생성한다. */
    fun save(run: PipelineRunEntity): PipelineRunEntity

    /** 파이프라인 실행 정보를 갱신한다 (상태, 통계, 종료 시각 등). */
    fun update(run: PipelineRunEntity)

    /** ID로 파이프라인 실행을 조회한다. */
    fun findById(id: String): PipelineRunEntity?

    /** 카테고리별 최근 실행 이력을 조회한다. */
    fun findLatestByCategoryId(categoryId: String, limit: Int = 10): List<PipelineRunEntity>

    /**
     * 필터 조건에 따라 실행 이력을 페이지네이션으로 조회한다.
     *
     * @param categoryId 단일 카테고리 필터 (선택)
     * @param status 실행 상태 필터 (선택)
     * @param since null이 아니면 startedAt이 이 시각 이후인 건만 반환한다 (within 파라미터 전달용)
     * @param offset 페이지 오프셋
     * @param limit 페이지 크기
     * @param categoryIds 복수 카테고리 필터 (선택). null이면 적용하지 않는다.
     *                    빈 컬렉션을 전달하면 SQL을 실행하지 않고 즉시 빈 리스트를 반환한다.
     *                    `categoryId`와 함께 지정되면 교집합(=`categoryId`가 반드시 포함돼야 함)으로 동작한다.
     */
    fun findAll(
        categoryId: String? = null,
        status: String? = null,
        since: Instant? = null,
        offset: Int = 0,
        limit: Int = 20,
        categoryIds: Collection<String>? = null
    ): List<PipelineRunEntity>

    /**
     * 필터 조건에 맞는 실행 이력 총 건수를 반환한다.
     *
     * @param since null이 아니면 startedAt이 이 시각 이후인 건만 집계한다
     * @param categoryIds 복수 카테고리 필터 (선택). null이면 적용하지 않는다.
     *                    빈 컬렉션이면 SQL을 실행하지 않고 0을 반환한다.
     */
    fun countAll(
        categoryId: String? = null,
        status: String? = null,
        since: Instant? = null,
        categoryIds: Collection<String>? = null
    ): Int

    /** 파이프라인 단계 추적을 저장한다. */
    fun saveStepTrace(trace: PipelineStepTraceEntity): PipelineStepTraceEntity

    /** 파이프라인 단계 추적 정보를 갱신한다 (상태, 종료 시각, 상세 내용 등). */
    fun updateStepTrace(trace: PipelineStepTraceEntity)

    /** 특정 실행 ID에 속한 단계 추적 목록을 조회한다. */
    fun findStepTracesByRunId(runId: String): List<PipelineStepTraceEntity>

    /** cutoff 이전에 생성된 파이프라인 실행과 관련 단계 추적을 삭제한다. */
    fun deleteOlderThan(cutoff: Instant): Int

    /** 특정 카테고리에서 RUNNING 상태인 파이프라인 실행 수를 반환한다. */
    fun countRunningByCategoryId(categoryId: String): Int

    /**
     * 쿨다운 창(cutoff 이후) 내 특정 카테고리의 가장 최근 FAILED 실행을 반환한다.
     * M2 실패 알림에서 기존 Slack 스레드 재활용 여부를 판단할 때 사용한다.
     */
    fun findLatestFailedByCategory(categoryId: String, cutoff: Instant): PipelineRunEntity?

    /**
     * 특정 카테고리의 최근 N건 실행(종료 시각 역순)을 반환한다.
     * M3 복구 알림에서 이전 연속 실패 streak를 계산할 때 사용한다.
     */
    fun findRecentByCategory(categoryId: String, limit: Int): List<PipelineRunEntity>

    /**
     * 파이프라인 실행의 Slack 스레드 ts와 페이로드 JSON을 갱신한다.
     * M2 실패 알림 발송 후 스레드 추적 정보를 저장하는 데 사용한다.
     */
    fun updateSlackThread(runId: String, threadTs: String, payloadJson: String)

    /**
     * 주어진 시간 범위(lower..upper) 내에 startedAt이 있는 파이프라인 실행이 존재하는지 반환한다.
     * M5 스케줄 미발동 감지에서 실제 실행 여부를 확인할 때 사용한다.
     */
    fun hasRunStartedBetween(lower: java.time.Instant, upper: java.time.Instant): Boolean

    /**
     * since 이후 FAILED 상태인 실행을 카테고리별로 집계하여 minFailures 이상인 항목을 반환한다.
     * DailyOpsForecastScheduler의 위험 소스 목록 조립에 사용한다.
     */
    fun findFailureCountsPerCategorySince(since: Instant, minFailures: Int): List<CategoryFailureSummary>

    /**
     * since 이후 가장 많이 실패한 상위 limit개 카테고리 요약을 반환한다.
     * WeeklyOpsActionReportScheduler의 topFailingSources 조립에 사용한다.
     */
    fun findTopFailingSourcesSince(since: Instant, limit: Int): List<CategoryFailureSummary>

    /**
     * from~to 범위 내 종료된 실행의 durationMs 목록을 반환한다.
     * WeeklyOpsActionReportScheduler의 레이턴시 중앙값 계산에 사용한다.
     */
    fun findDurationsBetween(from: Instant, to: Instant): List<Long>

    /**
     * `pipeline_runs.started_at >= since` 인 run 을 status 별 카운트로 집계한다.
     * 호출자가 since 를 KST 자정으로 맞춰 전달하면 "오늘 KST" 집계가 된다.
     * 해당 상태 row 가 없으면 key 가 포함되지 않는다.
     *
     * @param since 집계 하한 시각(inclusive)
     */
    fun countByStatusSince(since: Instant): Map<String, Long>
}

/**
 * 카테고리별 실패 건수 요약.
 * DailyOpsForecastScheduler / WeeklyOpsActionReportScheduler에서 사용한다.
 */
data class CategoryFailureSummary(
    val categoryId: String,
    val categoryName: String,
    val failureCount: Int,
)
