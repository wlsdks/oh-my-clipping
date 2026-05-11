package com.clipping.mcpserver.service.analytics.dto

/** 백필 실행 결과 DTO. */
data class BackfillResult(
    val weeksProcessed: Int,
    val personasAggregated: Int,
    val snapshotRowsCreated: Int,
    val durationMs: Long
)
