package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.observability.ClippingMetrics
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 비동기 수집/요약 작업 큐를 폴링하는 워커.
 * 고정 지연 간격으로 실행되며, 점검 모드 또는 비활성 설정 시 건너뛴다.
 */
@Component
class AsyncClipJobWorker(
    private val asyncClipJobService: AsyncClipJobService,
    private val properties: ClippingMcpServerProperties,
    private val runtimeSettingService: RuntimeSettingService,
    private val metrics: ClippingMetrics
) {

    @Scheduled(fixedDelayString = "\${clipping-mcp-server.job-poll-delay-ms:3000}")
    fun poll() = metrics.recordSchedulerRun("async_clip_job") {
        if (!properties.jobEnabled) return@recordSchedulerRun
        // 점검 모드에서는 수집/요약 작업을 중지한다
        if (runtimeSettingService.current().maintenanceMode) return@recordSchedulerRun
        asyncClipJobService.processDueJobs()
    }
}
