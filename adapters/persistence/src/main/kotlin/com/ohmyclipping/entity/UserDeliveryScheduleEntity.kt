package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 사용자 글로벌 발송 스케줄 설정 엔티티.
 * user_delivery_schedules 테이블에 매핑된다.
 * PK는 user_id 단독이다.
 */
@Entity
@Table(name = "user_delivery_schedules")
class UserDeliveryScheduleEntity(
    @Id
    @Column(name = "user_id", length = 36)
    val userId: String = "",

    @Column(name = "delivery_days", length = 50, nullable = false)
    var deliveryDays: String = "MON,TUE,WED,THU,FRI",

    @Column(name = "delivery_hour", nullable = false)
    var deliveryHour: Int = 8,

    @Column(length = 20, nullable = false)
    var preset: String = "WEEKDAYS",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
