package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.TrendVisualCardEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface TrendVisualCardRepository : JpaRepository<TrendVisualCardEntity, String> {
    fun findBySnapshotIdOrderByCreatedAtDesc(snapshotId: String, pageable: Pageable): List<TrendVisualCardEntity>
    fun findByReviewStatusOrderByCreatedAtDesc(reviewStatus: String, pageable: Pageable): List<TrendVisualCardEntity>
}
