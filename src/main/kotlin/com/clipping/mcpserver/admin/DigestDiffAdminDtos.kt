package com.clipping.mcpserver.admin

import com.clipping.mcpserver.model.DigestDiffLog

/** GET /api/admin/digest-diff 페이지 응답. */
data class DigestDiffPageResponse(
    val content: List<DigestDiffEntryResponse>,
    val totalElements: Int,
    val page: Int,
    val size: Int,
)

/** 단일 diff row 응답. */
data class DigestDiffEntryResponse(
    val id: String,
    val categoryId: String,
    val digestDate: String,
    val legacySummary: String?,
    val newSummary: String?,
    val newMode: String?,
    val sectionsCount: Int,
    val articlesCount: Int,
    val crossMatchCount: Int,
    val createdAt: String,
)

internal fun DigestDiffLog.toResponse(): DigestDiffEntryResponse =
    DigestDiffEntryResponse(
        id = id,
        categoryId = categoryId,
        digestDate = digestDate.toString(),
        legacySummary = legacySummary,
        newSummary = newSummary,
        newMode = newMode,
        sectionsCount = sectionsCount,
        articlesCount = articlesCount,
        crossMatchCount = crossMatchCount,
        createdAt = createdAt.toString(),
    )
