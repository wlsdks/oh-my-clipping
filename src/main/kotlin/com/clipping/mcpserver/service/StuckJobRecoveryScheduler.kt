package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.OpsNotificationEvent

import com.clipping.mcpserver.service.notification.OperationsNotificationService
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.store.AsyncJobStore
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * RUNNING 상태에서 멈춘 작업을 자동 복구하는 스케줄러.
 * `clipping.scheduler.stuck-job.timeout-minutes` 이상 갱신 없는 RUNNING 작업을
 * 죽은 것으로 간주한다. 기본값 30분 — Collect/Summarize 가 수 분 걸릴 수
 * 있어 기존 5분은 과도 회수 위험이 있었다.
 * 최대 시도 초과로 최종 실패한 작업은 운영 알림 서비스를 통해 관리자에게 알린다.
 */
@Component
class StuckJobRecoveryScheduler(
    private val jobStore: AsyncJobStore,
    private val notificationService: OperationsNotificationService,
    private val metrics: ClippingMetrics,
    @Value("\${clipping.scheduler.stuck-job.timeout-minutes:30}")
    private val stuckTimeoutMinutes: Long = 30,
    @Value("\${clipping.scheduler.stuck-job.max-attempts:3}")
    private val maxAttempts: Int = 3
) {
    @Scheduled(fixedDelay = 60_000)
    fun recover() = metrics.recordSchedulerRun("stuck_job_recovery") {
        log.debug { "StuckJobRecoveryScheduler started (timeout=${stuckTimeoutMinutes}min)" }
        val start = System.nanoTime()
        val (recovered, permanentlyFailed) = jobStore.recoverStuck(
            stuckMinutes = stuckTimeoutMinutes,
            maxAttempts = maxAttempts
        )
        if (recovered > 0) {
            log.warn { "Recovered $recovered stuck jobs (heartbeat timeout > ${stuckTimeoutMinutes}min)" }
        }
        if (permanentlyFailed > 0) {
            log.error { "$permanentlyFailed jobs permanently failed after max attempts — manual intervention needed" }
            notificationService.sendOps(
                OpsNotificationEvent.JOB_PERMANENTLY_FAILED,
                "$permanentlyFailed 건의 비동기 작업이 최대 재시도 횟수를 초과하여 최종 실패했습니다. 수동 확인이 필요합니다."
            )
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        if (recovered > 0 || permanentlyFailed > 0) {
            log.info { "StuckJobRecoveryScheduler completed in ${elapsed}ms, recovered=$recovered, failed=$permanentlyFailed" }
        } else {
            log.debug { "StuckJobRecoveryScheduler completed in ${elapsed}ms, no stuck jobs" }
        }
    }

    @PreDestroy
    fun onShutdown() {
        val count = jobStore.resetStalledRunningToPending(staleSeconds = 30)
        if (count > 0) {
            log.info { "Shutdown: reset $count stalled RUNNING jobs to PENDING" }
        }
    }
}
