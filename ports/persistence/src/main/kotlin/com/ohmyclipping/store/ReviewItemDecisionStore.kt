package com.ohmyclipping.store

import com.ohmyclipping.model.ReviewItemDecision
import java.time.Instant

interface ReviewItemDecisionStore {
    fun findBySummaryId(summaryId: String): ReviewItemDecision?
    fun findBySummaryIds(summaryIds: List<String>): List<ReviewItemDecision>
    fun findReviewedBetween(from: Instant, to: Instant, categoryId: String? = null): List<ReviewItemDecision>
    fun upsert(decision: ReviewItemDecision): ReviewItemDecision

    /** 날짜 범위 내 카테고리별 상태(REVIEW/EXCLUDE)별 건수를 집계한다. */
    fun countByStatusGroupedByCategory(from: Instant, to: Instant): List<CategoryStatusCount>

    /** 특정 카테고리의 제외(EXCLUDE) 항목을 최신순으로 조회한다. batch_summaries 조인으로 제목을 포함한다. */
    fun findExcludedItems(categoryId: String, limit: Int = 5): List<ExcludedItemRow>

    /** 미발송 batch_summaries 기준으로 카테고리별 상태 집계를 반환한다. */
    fun countByCategory(): List<ReviewCategoryCounts>

    /**
     * 지정 기간 동안 사람이 검토한 항목의 AI 제안 vs 실제 결과 집계를 반환한다.
     * suggested_status가 NULL이거나 reviewed_by = 'policy-auto'인 항목은 제외한다.
     */
    fun getAccuracyStats(from: Instant, to: Instant): List<AccuracyRow>

    /**
     * 정책 룰이 자동 제외한 항목(`status = 'EXCLUDE'` AND `reviewed_by = 'policy-auto'`) 을
     * 지정 기간 내에서 최신순으로 페이지 단위로 조회한다. batch_summaries / batch_categories 와 조인하여
     * 제목/카테고리명/점수를 함께 반환한다.
     *
     * @param since 이 시각(`reviewed_at >= since`) 이후의 자동 제외만 포함한다.
     * @param categoryId 비어있지 않으면 해당 카테고리로 필터. null/blank 는 전체.
     * @param reasonPrefix 비어있지 않으면 `reason LIKE 'prefix%'` 로 필터. `rule:` / `rule:event_type_blacklist` 등.
     * @param limit 페이지 크기 (1..100). 서비스 레이어에서 clamp 해서 전달한다.
     * @param offset 페이지 오프셋 (0 이상).
     */
    fun findAutoExcluded(
        since: Instant,
        categoryId: String?,
        reasonPrefix: String?,
        limit: Int,
        offset: Int,
    ): List<AutoExcludedRow>

    /**
     * [findAutoExcluded] 와 동일 조건을 만족하는 전체 건수를 반환한다. 페이지네이션용.
     */
    fun countAutoExcluded(
        since: Instant,
        categoryId: String?,
        reasonPrefix: String?,
    ): Int

    /**
     * [findAutoExcluded] 와 동일 조건에서 `reason` 별 집계를 반환한다.
     * 키는 저장된 reason 문자열 그대로(`rule:event_type_blacklist` 등), 값은 건수.
     */
    fun breakdownAutoExcludedByReason(
        since: Instant,
        categoryId: String?,
        reasonPrefix: String?,
    ): Map<String, Int>
}

/** 카테고리별 상태 집계 결과. */
data class CategoryStatusCount(
    val categoryId: String,
    val status: String,
    val count: Int
)

/** 제외 항목 조회 결과 (batch_summaries 조인). */
data class ExcludedItemRow(
    val title: String,
    val reason: String?,
    val score: Float,
    val excludedAt: Instant
)

/** AI 제안 vs 실제 결과 집계 행. */
data class AccuracyRow(
    val categoryId: String,
    val categoryName: String,
    val suggestedStatus: String,
    val actualStatus: String,
    val count: Int
)

/** 카테고리별 검토함 상태 집계 결과 (미발송 대상 기준). */
data class ReviewCategoryCounts(
    val categoryId: String,
    val categoryName: String,
    val totalCount: Int,
    val reviewCount: Int,
    val includeCount: Int,
    val excludeCount: Int,
    val suggestedIncludeCount: Int
)

/**
 * 자동 제외 감사 조회 결과 행.
 *
 * `clipping_review_items` ⨝ `batch_summaries` ⨝ `batch_categories`
 * ⟕ `rss_items` ⟕ `rss_sources` (rss_* 는 LEFT JOIN — FK 가 있지만
 * CASCADE 미설정인 이론적 orphan 케이스 방어).
 *
 * @property summaryId batch_summaries.id.
 * @property title 사용자 노출용 제목 — 번역 제목이 있으면 그 값, 없으면 원문 제목.
 * @property originalTitle batch_summaries.original_title.
 * @property translatedTitle batch_summaries.translated_title (nullable).
 * @property categoryId batch_summaries.category_id — 상세 drawer 에서 카테고리별 필터 재설정에 사용.
 * @property categoryName batch_categories.name.
 * @property score importance_score.
 * @property reason clipping_review_items.reason — `rule:{name}` 문자열.
 * @property excludedAt reviewed_at — 정책이 제외 결정을 기록한 시각.
 * @property summary batch_summaries.summary — 상세 drawer 에 노출할 요약 본문.
 * @property sourceUrl rss_items.link — 원문 링크. orphan 방어로 nullable.
 * @property sourceName rss_sources.name — 언론사/피드명. orphan 방어로 nullable.
 * @property publishedAt rss_items.published_at — 원문 발행 시각. 누락된 피드가 있어 nullable.
 * @property eventType batch_summaries.event_type — `OTHER`/`FUNDING`/... 과 같은 LLM 분류 결과.
 * @property sentiment batch_summaries.sentiment — `POSITIVE`/`NEUTRAL`/`NEGATIVE`.
 */
data class AutoExcludedRow(
    val summaryId: String,
    val title: String,
    val originalTitle: String,
    val translatedTitle: String?,
    val categoryId: String,
    val categoryName: String,
    val score: Float,
    val reason: String,
    val excludedAt: Instant,
    val summary: String,
    val sourceUrl: String?,
    val sourceName: String?,
    val publishedAt: Instant?,
    val eventType: String?,
    val sentiment: String?,
)
