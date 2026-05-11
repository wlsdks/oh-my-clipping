package com.ohmyclipping.repository

import com.ohmyclipping.entity.CostAlertNotificationEntity
import com.ohmyclipping.entity.CostAlertNotificationId
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 비용 알림 발송 이력 JPA 레포지토리.
 * (monthId, thresholdLevel) 복합 PK 기반의 dedup 조회를 제공한다.
 */
interface CostAlertNotificationRepository : JpaRepository<CostAlertNotificationEntity, CostAlertNotificationId> {

    /** 해당 월/임계값 조합의 발송 이력이 존재하는지 확인한다. */
    fun existsByMonthIdAndThresholdLevel(monthId: String, thresholdLevel: String): Boolean

    /**
     * 특정 월에서 thresholdLevel이 주어진 prefix로 시작하는 알림 이력을 반환한다.
     * findActiveCriticalsByMonth()에서 prefix="CRITICAL_"로 호출한다.
     */
    fun findByMonthIdAndThresholdLevelStartingWith(
        monthId: String,
        prefix: String
    ): List<CostAlertNotificationEntity>
}
