package com.ohmyclipping.service.dto.pipeline

import com.ohmyclipping.service.dto.clipping.PipelineStepStatus

import java.time.Instant

/**
 * 파이프라인 실행 이력을 나타내는 앱 계약 DTO.
 * JPA entity가 아니라 pipeline_runs 테이블 값을 store/service/admin 경계로 전달하는 record다.
 */
data class PipelineRunEntity(
    val id: String = "",
    val categoryId: String,
    val categoryName: String,
    val triggeredBy: String,
    val status: PipelineRunStatus,
    val orchestrationMode: String,
    val totalCollected: Int = 0,
    val totalSummarized: Int = 0,
    val totalDigestSelected: Int = 0,
    val postedToSlack: Boolean = false,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val durationMs: Long? = null,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    /** Slack 스레드 타임스탬프. M2 쿨다운 창 내 기존 스레드 재활용에 사용된다. */
    val slackThreadTs: String? = null,
)

/**
 * 파이프라인 개별 단계(step) 추적 앱 계약 DTO.
 * JPA entity가 아니라 pipeline_step_traces 테이블 값을 전달하는 record다.
 */
data class PipelineStepTraceEntity(
    val id: String = "",
    val runId: String,
    val step: String,
    val status: PipelineStepStatus,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val durationMs: Long? = null,
    val detail: String? = null,
    val createdAt: Instant = Instant.now()
)

/**
 * 파이프라인 실행 전체 상태를 나타내는 열거형.
 */
enum class PipelineRunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    PARTIAL
}
