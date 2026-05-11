package com.ohmyclipping.service.analytics.dto

import java.time.Instant

/**
 * GET /api/admin/analytics/personas/live 응답.
 *
 * Slice 별 점진 확장:
 *   - Slice 1: totals + presetPortfolio + customSummary (이 파일)
 *   - Slice 2: TotalsCard.weekOverWeekDelta, PresetPortfolioItem.weekOverWeekDelta /
 *              engagementRate 가 채워짐
 *   - Slice 3: activeAnomaliesSummary 필드 추가 예정
 *   - Slice 5: clusterSuggestions 요약 추가 예정
 */
data class LiveSnapshotResponse(
    val totals: TotalsCard,
    val presetPortfolio: List<PresetPortfolioItem>,
    val customSummary: CustomSummary,
    val asOf: Instant
)
