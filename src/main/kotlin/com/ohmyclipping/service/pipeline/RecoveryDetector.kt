package com.ohmyclipping.service.pipeline

import com.ohmyclipping.service.RuntimeSettingService
import com.ohmyclipping.service.dto.pipeline.PipelineRunStatus
import com.ohmyclipping.service.port.PipelineRunOpsEvent
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.PipelineRunStore
import org.springframework.stereotype.Component

/**
 * 파이프라인 실행 복구를 감지해 M3 알림을 발송한다.
 *
 * 동일 카테고리에서 N회 연속 FAILED 이후 SUCCEEDED 실행이 발생하면
 * OpsLogNotifier.postPipelineRecovery를 호출한다.
 * N은 opsRecoveryStreakThreshold 런타임 설정값으로 제어된다.
 */
@Component
class RecoveryDetector(
    private val runStore: PipelineRunStore,
    private val runtime: RuntimeSettingService,
    private val notifier: OpsLogNotifier,
) {

    /**
     * 파이프라인 실행이 SUCCEEDED 상태로 전환될 때 호출한다.
     * 직전 N개 실행이 모두 FAILED였던 경우 복구 알림을 발송한다.
     *
     * @param run 방금 SUCCEEDED로 완료된 파이프라인 실행 운영 알림 DTO
     */
    fun onRunCompleted(run: PipelineRunOpsEvent) {
        // SUCCEEDED가 아니면 복구 감지 대상이 아니다
        if (run.status != "SUCCEEDED") return

        val streak = runtime.current().opsRecoveryStreakThreshold
        // 현재 실행 포함 streak+1건을 조회한다 (index 0 = 현재, 1..streak = 이전 N건)
        val recent = runStore.findRecentByCategory(run.categoryId, streak + 1)

        // 이전 실행이 streak 건 미만이면(부팅 직후 등) 판단하지 않는다
        if (recent.size < streak + 1) return

        val priorN = recent.drop(1).take(streak)
        // 직전 N개가 모두 FAILED인 경우에만 복구로 간주한다
        if (priorN.all { it.status == PipelineRunStatus.FAILED }) {
            notifier.postPipelineRecovery(run.categoryId, run, streak)
        }
    }
}
