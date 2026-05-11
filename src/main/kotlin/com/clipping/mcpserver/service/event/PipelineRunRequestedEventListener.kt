package com.clipping.mcpserver.service.event

import com.clipping.mcpserver.service.pipeline.PipelineAsyncWorker
import com.clipping.mcpserver.service.dto.pipeline.PipelineRunStatus
import com.clipping.mcpserver.store.PipelineRunStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.Instant
import java.util.concurrent.RejectedExecutionException

private val log = KotlinLogging.logger {}

/**
 * 파이프라인 실행 요청 이벤트를 커밋 이후 비동기 워커 호출로 변환한다.
 */
@Component
class PipelineRunRequestedEventListener(
    private val pipelineAsyncWorker: PipelineAsyncWorker,
    private val pipelineRunStore: PipelineRunStore
) {

    /**
     * run 레코드가 커밋된 뒤에만 워커를 호출해 비동기 실행이 미커밋 데이터를 읽지 않게 한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PipelineRunRequestedEvent) {
        // 저장된 run 레코드가 보이는 시점에만 실제 파이프라인 실행을 시작한다.
        try {
            pipelineAsyncWorker.execute(
                runId = event.runId,
                categoryId = event.categoryId,
                hoursBack = event.hoursBack,
                maxItems = event.maxItems,
                unsentOnly = event.unsentOnly,
                sendToSlack = event.sendToSlack,
                slackChannelId = event.slackChannelId,
                ralphLoopEnabled = event.ralphLoopEnabled,
                ralphLoopMaxIterations = event.ralphLoopMaxIterations,
                ralphLoopStopPhrase = event.ralphLoopStopPhrase
            )
        } catch (error: TaskRejectedException) {
            markRunAsRejected(event.runId, error)
        } catch (error: RejectedExecutionException) {
            markRunAsRejected(event.runId, error)
        }
    }

    private fun markRunAsRejected(runId: String, error: RuntimeException) {
        val run = pipelineRunStore.findById(runId)
        if (run == null) {
            log.error(error) { "Pipeline async execution rejected but run was not found: runId=$runId" }
            return
        }
        if (run.status != PipelineRunStatus.RUNNING) {
            log.warn(error) {
                "Pipeline async execution rejected but run is already terminal or non-running: " +
                    "runId=$runId, status=${run.status}"
            }
            return
        }
        val now = Instant.now()
        pipelineRunStore.update(
            run.copy(
                status = PipelineRunStatus.FAILED,
                endedAt = now,
                durationMs = Duration.between(run.startedAt, now).toMillis(),
                errorMessage = "Pipeline async execution rejected: ${error.message}".take(500),
            )
        )
        log.error(error) { "Pipeline async execution rejected: runId=$runId" }
    }
}
