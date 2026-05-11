package com.ohmyclipping.service.dto

/**
 * DAU(일별 활성 사용자 수) 응답.
 */
data class DauResponse(val data: List<DauPoint>)

/**
 * DAU 개별 데이터 포인트.
 */
data class DauPoint(val date: String, val count: Long)

/**
 * 위자드 퍼널 분석 응답.
 */
data class WizardFunnelResponse(val data: List<WizardFunnelStep>)

/**
 * 위자드 퍼널 개별 단계.
 */
data class WizardFunnelStep(
    val step: String,
    val enters: Long,
    val completes: Long,
    val dropRate: Double
)

/**
 * 카테고리별 통계 응답.
 */
data class CategoryStatsResponse(val data: List<CategoryStatItem>)

/**
 * 카테고리별 통계 개별 항목.
 * CTR은 카테고리 내 클릭/노출 비율(%), sharePercent는 전체 클릭 대비 카테고리 비중(%).
 */
data class CategoryStatItem(
    val categoryId: String,
    val categoryName: String,
    val clicks: Long,
    val impressions: Long,
    val ctr: Double,
    val sharePercent: Double
)

/**
 * 기사 랭킹 응답.
 */
data class ArticleRankingResponse(val data: List<ArticleRankItem>)

/**
 * 기사 랭킹 개별 항목.
 */
data class ArticleRankItem(
    val rank: Int,
    val summaryId: String,
    val title: String?,
    val categoryName: String?,
    val sourceName: String?,
    val publishedAt: String?,
    val clicks: Long,
    val impressions: Long,
    val ctr: Double,
    val bookmarks: Long
)

/**
 * 기사 클릭률 요약 응답.
 * 기간 내 총 클릭 수, 총 발송 수, 클릭률(%)을 제공한다.
 */
data class ClickRateSummaryResponse(
    val totalClicks: Long,
    val totalDeliveries: Long,
    val clickRate: Double
)
