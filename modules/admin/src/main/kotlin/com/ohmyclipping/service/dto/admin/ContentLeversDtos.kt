package com.ohmyclipping.service.dto.admin

import java.time.Instant

/** RSS 소스 품질 집계 (레버 3). */
data class SourceQualityRow(
    val sourceId: String?,      // null = 수동 URL 버킷
    val sourceName: String,     // 수동 URL 은 "(수동 URL)"
    val delivered: Long,
    val uniqueUserClicks: Long,
    val clickRatePct: Double?,
    val likes: Long,
    val dislikes: Long,
    val likeRatePct: Double?,
    val statusLabel: String,    // "normal" | "review" | "default"
    // 비활성 소스 기본 숨김 필터 및 activate/deactivate mutation 의 optimistic concurrency(expectedUpdatedAt) 를 위해 carry 한다.
    // 수동 URL (rss_source_id NULL) 은 `true` / `Instant.EPOCH` 로 fallback.
    val isActive: Boolean,
    val updatedAt: Instant,
)

/** Content lever summary 응답 (소스 품질 단일 집계). */
data class ContentLeversSummary(
    val sourceQuality: List<SourceQualityRow>,
)
