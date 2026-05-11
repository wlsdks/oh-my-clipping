package com.ohmyclipping.admin.dto

import com.ohmyclipping.service.dto.pipeline.PipelineRunEntity
import com.ohmyclipping.service.dto.pipeline.PipelineStepTraceEntity

/**
 * 파이프라인 실행 요청 DTO.
 * 프론트엔드에서 파이프라인을 수동 실행할 때 사용한다.
 */
data class PipelineExecuteRequest(
    val categoryId: String,
    val hoursBack: Int? = null,
    val maxItems: Int? = null,
    val unsentOnly: Boolean? = true,
    val sendToSlack: Boolean? = false,
    val slackChannelId: String? = null,
    val ralphLoopEnabled: Boolean? = null,
    val ralphLoopMaxIterations: Int? = null,
    val ralphLoopStopPhrase: String? = null
)

/**
 * 파이프라인 실행 응답 DTO.
 * 비동기 실행이 시작되면 runId를 반환한다.
 */
data class PipelineExecuteResponse(
    val runId: String
)

/**
 * 파이프라인 실행 이력 응답 DTO.
 * PipelineRunEntity를 API 응답 형태로 변환한다.
 */
data class PipelineRunResponse(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val triggeredBy: String,
    val status: String,
    val orchestrationMode: String,
    val totalCollected: Int,
    val totalSummarized: Int,
    val totalDigestSelected: Int,
    val postedToSlack: Boolean,
    val startedAt: String,
    val endedAt: String?,
    val durationMs: Long?,
    val errorMessage: String?,
    val stepTraces: List<PipelineStepTraceResponse>
) {
    companion object {
        /** 엔티티와 단계 추적 목록을 응답 DTO로 변환한다. */
        fun from(
            entity: PipelineRunEntity,
            traces: List<PipelineStepTraceEntity> = emptyList()
        ): PipelineRunResponse = PipelineRunResponse(
            id = entity.id,
            categoryId = entity.categoryId,
            categoryName = entity.categoryName,
            triggeredBy = entity.triggeredBy,
            status = entity.status.name,
            orchestrationMode = entity.orchestrationMode,
            totalCollected = entity.totalCollected,
            totalSummarized = entity.totalSummarized,
            totalDigestSelected = entity.totalDigestSelected,
            postedToSlack = entity.postedToSlack,
            startedAt = entity.startedAt.toString(),
            endedAt = entity.endedAt?.toString(),
            durationMs = entity.durationMs,
            errorMessage = entity.errorMessage,
            stepTraces = traces.map { PipelineStepTraceResponse.from(it) }
        )
    }
}

/**
 * 파이프라인 단계 추적 응답 DTO.
 * PipelineStepTraceEntity를 API 응답 형태로 변환한다.
 */
data class PipelineStepTraceResponse(
    val id: String,
    val step: String,
    val status: String,
    val startedAt: String,
    val endedAt: String?,
    val durationMs: Long?,
    val detail: String?
) {
    companion object {
        /** 단계 추적 엔티티를 응답 DTO로 변환한다. */
        fun from(entity: PipelineStepTraceEntity): PipelineStepTraceResponse =
            PipelineStepTraceResponse(
                id = entity.id,
                step = entity.step,
                status = entity.status.name,
                startedAt = entity.startedAt.toString(),
                endedAt = entity.endedAt?.toString(),
                durationMs = entity.durationMs,
                detail = entity.detail
            )
    }
}

/**
 * 파이프라인 실행 이력 페이지네이션 응답 DTO.
 */
data class PipelineRunsPageResponse(
    val content: List<PipelineRunResponse>,
    val totalCount: Long,
    val page: Int,
    val size: Int
)
