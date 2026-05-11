package com.clipping.mcpserver.service.dto

/**
 * 경쟁사 스냅샷 조회 응답 DTO.
 */
data class CompetitorSnapshotResponse(
    val items: List<CompetitorSnapshotItem>
)

data class CompetitorSnapshotItem(
    val summaryId: String,
    val competitorName: String,
    val title: String,
    val sourceLink: String,
    val importanceScore: Float,
    val createdAt: String
)
