package com.ohmyclipping.repository

import com.ohmyclipping.entity.ReviewItemAuditEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewItemAuditRepository : JpaRepository<ReviewItemAuditEntity, String> {
    fun findBySummaryIdOrderByCreatedAtDesc(summaryId: String, pageable: Pageable): List<ReviewItemAuditEntity>
}
