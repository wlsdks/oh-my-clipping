package com.clipping.mcpserver.store

import java.time.Instant

/**
 * 비용 알림 발송 이력 저장소 포트.
 * (monthId, thresholdLevel) 조합의 최초 등록 여부를 원자적으로 판단한다.
 */
interface CostAlertNotificationStore {

    /**
     * (monthId, thresholdLevel) 조합을 최초로 등록하면 true를 반환한다.
     * 이미 등록된 경우 false를 반환하여 중복 알림을 방지한다.
     *
     * @param monthId 알림 대상 월 (YYYY-MM)
     * @param thresholdLevel 임계값 레벨 식별자 (예: "CRITICAL_100", "CRITICAL_90")
     * @return 최초 등록이면 true, 이미 존재하면 false
     */
    fun tryRegister(monthId: String, thresholdLevel: String): Boolean

    /**
     * 특정 월의 CRITICAL_ 접두어 알림 이력을 반환한다.
     * 홈 대시보드 예산 경보 티어 조회에 사용한다.
     *
     * @param monthId 조회 대상 월 (YYYY-MM)
     * @return thresholdLevel이 "CRITICAL_"로 시작하는 알림 엔티티 목록
     */
    fun findActiveCriticalsByMonth(monthId: String): List<CostAlertNotification>
}

data class CostAlertNotification(
    val monthId: String,
    val thresholdLevel: String,
    val notifiedAt: Instant,
)
