package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.TrendVisualCard
import com.clipping.mcpserver.model.TrendVisualReviewStatus

interface TrendVisualCardStore {
    fun findById(id: String): TrendVisualCard?
    fun listBySnapshotId(snapshotId: String, limit: Int = 100): List<TrendVisualCard>
    fun list(reviewStatus: TrendVisualReviewStatus? = null, limit: Int = 200): List<TrendVisualCard>
    fun save(card: TrendVisualCard): TrendVisualCard
}
