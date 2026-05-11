package com.ohmyclipping.repository

import com.ohmyclipping.entity.UserEventEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface UserEventRepository : JpaRepository<UserEventEntity, Long> {
    fun countByEventTypeAndCreatedAtBetween(eventType: String, from: Instant, to: Instant): Long

    @Query("SELECT COUNT(DISTINCT e.userId) FROM UserEventEntity e WHERE e.createdAt BETWEEN :from AND :to")
    fun countDistinctUsersByCreatedAtBetween(from: Instant, to: Instant): Long

    fun findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        userId: String,
        from: Instant,
        to: Instant,
        pageable: Pageable
    ): List<UserEventEntity>

    fun findByEventTypeAndCreatedAtBetween(eventType: String, from: Instant, to: Instant): List<UserEventEntity>

    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
