package com.ohmyclipping.service.pipeline

import com.ohmyclipping.error.BatchJobExecutionException
import com.ohmyclipping.service.AdminClippingService
import com.ohmyclipping.service.dto.pipeline.PipelineRunStatus
import com.ohmyclipping.service.dto.pipeline.PipelineStepTraceEntity
import com.ohmyclipping.store.PipelineRunStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * 파이프라인을 비동기로 실행하는 워커.
 * Spring @Async 프록시가 동작하려면 호출자와 별도 클래스여야 한다.
 * PipelineExecutionService가 run 레코드를 생성한 뒤 이 클래스의 execute()를 호출한다.
 */
@Service
class PipelineAsyncWorker(
    private val pipelineRunStore: PipelineRunStore,
    private val adminClippingService: AdminClippingService,
    private val pipelineLogService: PipelineLogService
) {

    /**
     * 파이프라인을 백그라운드에서 실행하고 결과를 DB에 기록한다.
     *
     * @param runId 이미 RUNNING 상태로 저장된 파이프라인 실행 ID
     * @param categoryId 대상 카테고리 ID
     * @param hoursBack 수집 시간 범위 (null이면 런타임 기본값 사용)
     * @param maxItems 최대 아이템 수 (null이면 런타임 기본값 사용)
     * @param unsentOnly 미발송 항목만 대상으로 할지 여부
     * @param sendToSlack Slack 발송 여부
     * @param slackChannelId Slack 채널 ID (null이면 카테고리 기본 채널)
     * @param ralphLoopEnabled Ralph 루프 활성화 오버라이드
     * @param ralphLoopMaxIterations Ralph 루프 최대 반복 오버라이드
     * @param ralphLoopStopPhrase Ralph 루프 중단 문구 오버라이드
     */
    @Async
    fun execute(
        runId: String,
        categoryId: String,
        hoursBack: Int?,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
        ralphLoopEnabled: Boolean?,
        ralphLoopMaxIterations: Int?,
        ralphLoopStopPhrase: String?
    ) {
        log.info { "파이프라인 비동기 실행 시작: runId=$runId, categoryId=$categoryId" }
        val run = pipelineRunStore.findById(runId)
        val categoryName = run?.categoryName ?: "(알 수 없음)"
        pipelineLogService.sendPipelineStartLog(categoryName)
        try {
            val result = runPipeline(
                runId = runId,
                categoryId = categoryId,
                hoursBack = hoursBack,
                maxItems = maxItems,
                unsentOnly = unsentOnly,
                sendToSlack = sendToSlack,
                slackChannelId = slackChannelId,
                ralphLoopEnabled = ralphLoopEnabled,
                ralphLoopMaxIterations = ralphLoopMaxIterations,
                ralphLoopStopPhrase = ralphLoopStopPhrase
            )

            // 단계별 추적 기록 저장
            saveStepTraces(runId, result.stepTraces)

            // 실행 완료 상태로 업데이트
            updateRunAsSucceeded(runId, result)
            log.info { "파이프라인 비동기 실행 완료: runId=$runId" }

            // 운영 로그 채널에 요약 전송
            val run = pipelineRunStore.findById(runId)
            val durationMs = run?.durationMs ?: 0L
            val categoryName = run?.categoryName ?: "(알 수 없음)"
            pipelineLogService.sendPipelineLog(result, categoryName, durationMs)
        } catch (e: BatchJobExecutionException) {
            log.error(e) { "파이프라인 비동기 실행 실패: runId=$runId" }
            updateRunAsFailed(runId, e.operationalMessage)

            // 실패 시에도 운영 로그 채널에 실패 요약 전송
            val run = pipelineRunStore.findById(runId)
            val durationMs = run?.durationMs ?: 0L
            val categoryName = run?.categoryName ?: "(알 수 없음)"
            pipelineLogService.sendPipelineFailureLog(categoryName, e.operationalMessage, durationMs)
        }
    }

    private fun runPipeline(
        runId: String,
        categoryId: String,
        hoursBack: Int?,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
        ralphLoopEnabled: Boolean?,
        ralphLoopMaxIterations: Int?,
        ralphLoopStopPhrase: String?
    ): com.ohmyclipping.service.dto.clipping.PipelineRunResult =
        runCatching {
            adminClippingService.runPipeline(
                categoryId = categoryId,
                hoursBack = hoursBack,
                maxItems = maxItems,
                unsentOnly = unsentOnly,
                sendToSlack = sendToSlack,
                slackChannelId = slackChannelId,
                ralphLoopEnabledOverride = ralphLoopEnabled,
                ralphLoopMaxIterationsOverride = ralphLoopMaxIterations,
                ralphLoopStopPhraseOverride = ralphLoopStopPhrase
            )
        }.getOrElse { cause ->
            throw BatchJobExecutionException(
                jobId = runId,
                jobType = "PIPELINE",
                operationalMessage = cause.message ?: "Unknown error",
                cause = cause
            )
        }

    /**
     * 파이프라인 결과의 단계 추적을 DB에 저장한다.
     */
    private fun saveStepTraces(
        runId: String,
        stepTraces: List<com.ohmyclipping.service.dto.clipping.PipelineStepTrace>
    ) {
        stepTraces.forEach { trace ->
            pipelineRunStore.saveStepTrace(
                PipelineStepTraceEntity(
                    runId = runId,
                    step = trace.step,
                    status = trace.status,
                    startedAt = Instant.parse(trace.startedAt),
                    endedAt = Instant.parse(trace.endedAt),
                    durationMs = computeDurationMs(trace.startedAt, trace.endedAt),
                    detail = trace.detail
                )
            )
        }
    }

    /**
     * 실행 성공 시 run 레코드를 갱신한다.
     */
    private fun updateRunAsSucceeded(
        runId: String,
        result: com.ohmyclipping.service.dto.clipping.PipelineRunResult
    ) {
        val run = pipelineRunStore.findById(runId) ?: return
        val now = Instant.now()
        pipelineRunStore.update(
            run.copy(
                status = PipelineRunStatus.SUCCEEDED,
                orchestrationMode = result.orchestrationMode.name,
                totalCollected = result.collect.totalCollected,
                totalSummarized = result.summarize.totalSummarized,
                totalDigestSelected = result.digest.selectedCount,
                postedToSlack = result.digest.postedToSlack,
                endedAt = now,
                durationMs = Duration.between(run.startedAt, now).toMillis()
            )
        )
    }

    /**
     * 실행 실패 시 run 레코드를 갱신한다.
     */
    private fun updateRunAsFailed(runId: String, errorMessage: String?) {
        val run = pipelineRunStore.findById(runId) ?: return
        val now = Instant.now()
        pipelineRunStore.update(
            run.copy(
                status = PipelineRunStatus.FAILED,
                endedAt = now,
                durationMs = Duration.between(run.startedAt, now).toMillis(),
                errorMessage = errorMessage?.take(500)
            )
        )
    }

    /** ISO-8601 시각 문자열 쌍에서 밀리초 단위 소요 시간을 계산한다. */
    private fun computeDurationMs(startedAt: String, endedAt: String): Long =
        Duration.between(Instant.parse(startedAt), Instant.parse(endedAt)).toMillis()
}
