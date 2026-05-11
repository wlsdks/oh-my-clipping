package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.IncidentWindowState
import com.clipping.mcpserver.service.port.OpsLogNotifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * 5분 슬라이딩 윈도우 내 파이프라인 실패를 집계해 인시던트를 감지한다.
 *
 * 동일 시간 슬롯에서 임계값(opsIncidentThresholdCategories) 이상의
 * distinct category 실패가 발생하면 M4 인시던트 알림을 발송한다.
 * 스위퍼(@Scheduled)가 만료된 창을 정리하고 열린 인시던트를 최종 업데이트한다.
 */
@Component
class IncidentWindowTracker(
    private val runtime: RuntimeSettingService,
    private val notifier: OpsLogNotifier,
    private val clock: Clock,
) {
    private val windows = ConcurrentHashMap<Long, IncidentWindowState>()

    /**
     * 파이프라인 실패 발생 시 호출한다.
     * 해당 시간 슬롯에 실패를 등록하고, 임계값 도달 시 M4 인시던트 알림을 발송한다.
     *
     * @param categoryId 실패한 카테고리 ID (distinct 카운트 기준)
     * @param runId 실패한 파이프라인 실행 ID
     */
    fun recordFailure(categoryId: String, runId: String) {
        val settings = runtime.current()
        val windowMinutes = settings.opsIncidentWindowMinutes.toLong()
        // 현재 시각을 windowMinutes 단위로 버킷화해 윈도우 키를 결정한다
        val windowKey = clock.instant().epochSecond / 60 / windowMinutes

        val state = windows.computeIfAbsent(windowKey) { IncidentWindowState(windowKey = windowKey) }
        synchronized(state) {
            state.categories.add(categoryId)
            state.failedRuns.add(runId)

            val reachedThreshold = state.categories.size >= settings.opsIncidentThresholdCategories
            if (reachedThreshold && state.parentTs == null) {
                // 처음 임계값 도달 시 인시던트 알림을 발동한다
                notifier.postIncident(state)
            }
            // parentTs != null이면 이미 인시던트 모드 — 스위퍼가 창 종료 시 최종 업데이트한다
        }
    }

    /**
     * 1분마다 만료된 창을 스윕해 열린 인시던트를 최종 업데이트하고 상태를 제거한다.
     * 임계값에 도달하지 못한 창(parentTs == null)은 조용히 버린다.
     */
    @Scheduled(fixedDelay = 60_000)
    fun runSweeper() {
        val settings = runtime.current()
        val windowMinutes = settings.opsIncidentWindowMinutes.toLong()
        val currentWindowKey = clock.instant().epochSecond / 60 / windowMinutes

        // 현재 창보다 작은 키는 모두 만료된 창이다
        val toClose = windows.keys.filter { it < currentWindowKey }
        for (key in toClose) {
            val state = windows.remove(key) ?: continue
            if (state.parentTs != null) {
                // parentTs가 설정된 경우(인시던트 모드)에만 최종 chat.update를 발송한다
                notifier.postIncident(state)
            }
        }
    }

    /** 테스트용 창 스냅샷 접근자 */
    internal fun windowsSnapshot(): Map<Long, IncidentWindowState> = windows.toMap()
}
