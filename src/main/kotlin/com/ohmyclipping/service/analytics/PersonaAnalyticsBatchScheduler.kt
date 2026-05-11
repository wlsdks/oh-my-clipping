package com.ohmyclipping.service.analytics

import com.ohmyclipping.store.analytics.dto.TriggerType
import com.ohmyclipping.service.analytics.exception.BatchAlreadyRunningException
import com.ohmyclipping.service.analytics.time.AnalyticsTime
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 페르소나 주간 배치 스케줄러.
 *
 * 매주 월요일 05:00 KST 에 직전 주의 스냅샷 배치를 자동으로 실행한다.
 * 이미 실행 중인 배치가 있으면 중복 실행을 방지하고 경고 로그만 남긴다.
 *
 * 실제 배치 로직은 [PersonaAnalyticsMondayBatch] 에 위임한다.
 */
@Component
class PersonaAnalyticsBatchScheduler(
    private val batch: PersonaAnalyticsMondayBatch
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 매주 월요일 05:00 KST 에 자동 트리거한다.
     * BatchAlreadyRunningException 만 catch 해 중복 실행을 방지하고,
     * 그 외 예외는 상위로 전파해 원인을 노출한다.
     */
    @Scheduled(cron = "0 0 5 * * MON", zone = "Asia/Seoul")
    fun trigger() {
        val weekStart = AnalyticsTime.previousWeekStart()
        log.info("PersonaAnalyticsBatchScheduler 트리거: weekStart={}", weekStart)

        try {
            batch.run(weekStart, TriggerType.SCHEDULED, null)
        } catch (e: BatchAlreadyRunningException) {
            // 같은 주차에 이미 실행 중인 배치가 있으면 경고 로그만 남기고 종료한다.
            log.warn("배치 중복 실행 방지: {}", e.message)
        }
    }
}
