package com.ohmyclipping.store

import com.ohmyclipping.entity.CostAlertNotificationEntity
import com.ohmyclipping.repository.CostAlertNotificationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

private val log = KotlinLogging.logger {}

/**
 * JPA 기반 비용 알림 발송 이력 저장소 구현체.
 * existsByMonthIdAndThresholdLevel 조회 후 save로 dedup을 처리하며,
 * 동시 삽입 경합 시 DataIntegrityViolationException을 잡아 false를 반환한다.
 */
@Repository
class JpaCostAlertNotificationStore(
    private val repo: CostAlertNotificationRepository,
) : CostAlertNotificationStore {

    /**
     * (monthId, thresholdLevel) 조합의 최초 등록을 시도한다.
     * 이미 존재하거나 동시 삽입 경합이 발생하면 false를 반환한다.
     */
    override fun tryRegister(monthId: String, thresholdLevel: String): Boolean {
        // 중복 여부를 먼저 확인해 불필요한 삽입을 방지한다
        if (repo.existsByMonthIdAndThresholdLevel(monthId, thresholdLevel)) return false
        return try {
            repo.save(CostAlertNotificationEntity(monthId = monthId, thresholdLevel = thresholdLevel))
            true
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청으로 인한 경합 — 다른 스레드가 먼저 등록한 것으로 간주한다
            log.debug { "Cost alert dedup: concurrent registration detected for $monthId/$thresholdLevel" }
            false
        }
    }

    /**
     * 특정 월의 CRITICAL_ 접두어 알림 이력을 반환한다.
     * thresholdLevel이 "CRITICAL_"로 시작하는 건만 조회하여 일반 임계값(DAILY 등)과 구분한다.
     */
    override fun findActiveCriticalsByMonth(monthId: String): List<CostAlertNotification> =
        repo.findByMonthIdAndThresholdLevelStartingWith(monthId, "CRITICAL_")
            .map { it.toModel() }

    private fun CostAlertNotificationEntity.toModel(): CostAlertNotification =
        CostAlertNotification(
            monthId = monthId,
            thresholdLevel = thresholdLevel,
            notifiedAt = notifiedAt,
        )
}
