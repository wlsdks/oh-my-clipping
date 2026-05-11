package com.ohmyclipping.service

import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.store.CategoryStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * 30일 이상 일시정지 상태인 카테고리를 자동으로 활성 상태로 복원한다.
 * 매일 03:00에 실행되며, pausedAt 기준으로 만료 여부를 판단한다.
 */
@Component
class AutoUnpauseScheduler(
    private val categoryStore: CategoryStore,
    private val metrics: ClippingMetrics
) {

    companion object {
        /** 일시정지 허용 최대 기간 */
        val MAX_PAUSE_DURATION: Duration = Duration.ofDays(30)
    }

    /**
     * 매일 03:00 실행: pausedAt이 30일을 초과한 PAUSED 카테고리를 자동 해제한다.
     * 개별 카테고리 처리 중 예외가 발생해도 나머지 카테고리는 계속 처리한다.
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun resumeExpiredPauses() = metrics.recordSchedulerRun("auto_unpause") {
        // Store에서 pausedAt이 30일 초과인 PAUSED 카테고리를 조회한다.
        val expired = categoryStore.findExpiredPaused(MAX_PAUSE_DURATION)
        if (expired.isEmpty()) return@recordSchedulerRun

        log.info { "[auto-unpause] ${expired.size}개 카테고리 자동 해제 대상" }
        for (category in expired) {
            runCatching {
                categoryStore.resume(category.id)
                val pausedDays = Duration.between(category.pausedAt, Instant.now()).toDays()
                log.info { "[auto-unpause] 자동 해제: ${category.name} (${category.id}), 일시정지 기간: ${pausedDays}일" }
            }.onFailure { e ->
                log.error(e) { "[auto-unpause] 자동 해제 실패: ${category.name} (${category.id})" }
            }
        }
    }
}
