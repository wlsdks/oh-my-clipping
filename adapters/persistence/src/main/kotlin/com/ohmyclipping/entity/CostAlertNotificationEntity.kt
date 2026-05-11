package com.ohmyclipping.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * 월별·임계값별 비용 알림 발송 이력 엔티티.
 * (monthId, thresholdLevel) 복합 PK로 중복 알림을 방지한다.
 * cost_alert_notifications 테이블에 매핑된다.
 */
@Entity
@Table(name = "cost_alert_notifications")
@IdClass(CostAlertNotificationId::class)
class CostAlertNotificationEntity(

    /** 알림 대상 월 (YYYY-MM 형식). VARCHAR(7) — V107 originally CHAR(7), migrated to VARCHAR(7) in V109 to match Hibernate's default type mapping. */
    @Id
    @Column(name = "month_id", length = 7, nullable = false)
    val monthId: String,

    /** 임계값 레벨 식별자 (예: "CRITICAL_100", "CRITICAL_90") */
    @Id
    @Column(name = "threshold_level", length = 32, nullable = false)
    val thresholdLevel: String,

    /** 최초 알림 발송 시각 */
    @Column(name = "notified_at", nullable = false)
    val notifiedAt: Instant = Instant.now(),
)

/**
 * CostAlertNotificationEntity 복합 PK 클래스.
 */
data class CostAlertNotificationId(
    val monthId: String = "",
    val thresholdLevel: String = "",
) : Serializable
