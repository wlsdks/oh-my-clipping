package com.ohmyclipping.store

import com.ohmyclipping.model.TrendPeriodType
import com.ohmyclipping.model.TrendRegionType
import com.ohmyclipping.model.TrendSnapshot
import com.ohmyclipping.model.TrendSnapshotStatus

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
