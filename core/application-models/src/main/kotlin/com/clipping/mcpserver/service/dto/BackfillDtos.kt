package com.clipping.mcpserver.service.dto

import jakarta.validation.constraints.Size

// ─── Preview ────────────────────────────────────────────────────────────────

/**
 * 단일 RSS 소스에 대한 기업 매칭 후보 정보.
 *
 * [confidence] 는 DART precision 점수로 분류된다:
 * - `high`   : precision >= 0.9
 * - `medium` : precision >= 0.5
 * - `low`    : precision < 0.5
 */
data class BackfillCandidate(
    val sourceId: String,
    val sourceUrl: String,
    val sourceName: String,
    val categoryId: String,
    val categoryName: String,
    val matchedCompanyName: String,
    val stockCode: String?,
    val confidence: String,
    val precision: Double,
)

/**
 * [GET /api/admin/organizations/backfill/preview] 응답.
 */
data class BackfillPreviewResponse(
    val candidates: List<BackfillCandidate>,
    val total: Int,
    val byConfidence: Map<String, Int>,
)

// ─── Apply ───────────────────────────────────────────────────────────────────

/**
 * [POST /api/admin/organizations/backfill/apply] 요청.
 *
 * 한 번에 최대 100개 후보를 적용한다. 초과하면 400 응답.
 */
data class BackfillApplyRequest(
    @field:Size(max = 100, message = "한 번에 최대 100개 적용")
    val candidateIds: List<String> = emptyList(),
)

/**
 * 단일 후보 적용 실패 정보.
 */
data class BackfillApplyError(
    val candidateId: String,
    val reason: String,
)

/**
 * [POST /api/admin/organizations/backfill/apply] 응답.
 */
data class BackfillApplyResponse(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val errors: List<BackfillApplyError>,
    val affectedCategoryIds: List<String>,
)
