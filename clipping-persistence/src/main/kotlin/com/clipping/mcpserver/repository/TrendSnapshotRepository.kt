package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.TrendSnapshotEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface TrendSnapshotRepository : JpaRepository<TrendSnapshotEntity, String> {
    fun findByPeriodTypeAndCategoryIdAndRegionTypeAndStatusOrderByCreatedAtDesc(
        periodType: String,
        categoryId: String?,
        regionType: String,
        status: String,
        pageable: Pageable
    ): List<TrendSnapshotEntity>

    fun findByPeriodTypeOrderByCreatedAtDesc(periodType: String, pageable: Pageable): List<TrendSnapshotEntity>
    fun findByCategoryIdOrderByCreatedAtDesc(categoryId: String, pageable: Pageable): List<TrendSnapshotEntity>
    fun findByStatusOrderByCreatedAtDesc(status: String, pageable: Pageable): List<TrendSnapshotEntity>
}
