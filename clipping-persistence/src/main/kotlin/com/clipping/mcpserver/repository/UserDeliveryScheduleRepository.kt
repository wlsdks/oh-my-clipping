package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.UserDeliveryScheduleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface UserDeliveryScheduleRepository : JpaRepository<UserDeliveryScheduleEntity, String> {
    fun findByUserId(userId: String): UserDeliveryScheduleEntity?

    @Query(
        """SELECT e FROM UserDeliveryScheduleEntity e
           WHERE e.deliveryDays LIKE CONCAT('%', :dayOfWeek, '%')
           AND e.deliveryHour = :hour"""
    )
    fun findByDeliveryDaysContainingAndDeliveryHour(dayOfWeek: String, hour: Int): List<UserDeliveryScheduleEntity>

    /** 오늘 이전에 수정된 발송 대상 스케줄을 DB 수준에서 필터하여 조회한다. */
    @Query(
        """SELECT e FROM UserDeliveryScheduleEntity e
           WHERE e.deliveryDays LIKE CONCAT('%', :dayOfWeek, '%')
           AND e.deliveryHour = :hour
           AND e.updatedAt < :cutoff"""
    )
    fun findDueSchedules(dayOfWeek: String, hour: Int, cutoff: Instant): List<UserDeliveryScheduleEntity>

    @Query("SELECT e.userId FROM UserDeliveryScheduleEntity e")
    fun findAllUserIds(): Set<String>

    /** 오늘 이전에 수정된 스케줄의 사용자 ID만 DB 수준에서 필터하여 조회한다. */
    @Query("SELECT e.userId FROM UserDeliveryScheduleEntity e WHERE e.updatedAt < :cutoff")
    fun findUserIdsUpdatedBefore(cutoff: Instant): Set<String>
}
