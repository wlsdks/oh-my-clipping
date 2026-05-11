package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.TrendPeriodType
import com.clipping.mcpserver.model.TrendRegionType
import com.clipping.mcpserver.model.TrendSnapshot
import com.clipping.mcpserver.model.TrendSnapshotStatus

interface TrendSnapshotStore {
    fun findById(id: String): TrendSnapshot?
    fun list(
        periodType: TrendPeriodType? = null,
        categoryId: String? = null,
        regionType: TrendRegionType? = null,
        status: TrendSnapshotStatus? = null,
        limit: Int = 100
    ): List<TrendSnapshot>

    fun save(snapshot: TrendSnapshot): TrendSnapshot
}
