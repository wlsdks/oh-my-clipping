package com.ohmyclipping.service.dto

import java.time.Instant

/**
 * 정책 룰이 자동으로 EXCLUDE 처리한 기사 단건.
 *
 * 관리자 감사 화면(`GET /api/admin/review-items/auto-excluded`) 응답에 포함된다.
 * 기계 판독용 [reason] 접두어(`rule:event_type_blacklist` / `rule:zero_signal`) 로
 * 어떤 룰이 발동했는지 UI 에서 필터/그룹핑 할 수 있게 한다.
 *
 * 상세 drawer 에서 기사 본문/원문 링크/언론사 등 context 를 렌더링하기 위해
 * 요약 본문·원문 링크·발행 시각·event_type/sentiment 를 함께 전달한다.
 *
 * @property summaryId batch_summaries.id. 복구 API 의 경로 변수로 사용된다.
 * @property title 사용자에게 노출할 제목. translatedTitle 이 있으면 그 값, 없으면 originalTitle.
 * @property originalTitle batch_summaries.original_title.
 * @property translatedTitle batch_summaries.translated_title (번역이 없으면 null).
 * @property categoryId batch_summaries.category_id — drawer 에서 같은 카테고리 필터링 재진입에 사용.
 * @property categoryName batch_categories.name — UI 카드에 카테고리 라벨로 표시.
 * @property score summary.importance_score (0.0 ~ 1.0). 낮을수록 zero_signal 에 걸리기 쉽다.
 * @property reason 기계 판독 접두어. `rule:{ruleName}` 형태로 직렬화된다.
 * @property excludedAt clipping_review_items.reviewed_at — 자동 제외가 기록된 시각.
 * @property summary batch_summaries.summary — drawer 에 표시할 요약 본문.
 * @property sourceUrl rss_items.link — 원문 링크. rss_items FK orphan 인 경우 null.
 * @property sourceName rss_sources.name — 언론사/피드명. rss_sources 조인 실패 시 null.
 * @property publishedAt rss_items.published_at — 원문 발행 시각.
 * @property eventType batch_summaries.event_type — LLM 분류 결과(OTHER/FUNDING 등).
 * @property sentiment batch_summaries.sentiment — POSITIVE/NEUTRAL/NEGATIVE.
 */
data class AutoExcludedItem(
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

/**
 * 자동 제외 감사 뷰 페이지 응답.
 *
 * [items] 는 요청된 page/size 범위만큼 자른 결과이고, [totalCount] 는 필터를 만족하는 전체 건수.
 * [reasonBreakdown] 은 같은 필터(카테고리/기간) 기준으로 reason 별 발생 수를 집계해 UI 에서
 * "왜 제외됐는지" 카테고리별 요약을 보여줄 수 있게 한다.
 *
 * @property items 페이지 내 개별 항목. size ≤ 100.
 * @property totalCount 필터 조건을 만족하는 전체 자동 제외 건수. 페이지네이션 UI 에서 사용.
 * @property reasonBreakdown reason(rule:event_type_blacklist 등) → count 맵. 기간/카테고리 필터에 따른 집계.
 */
data class AutoExcludedResponse(
    val items: List<AutoExcludedItem>,
    val totalCount: Int,
    val reasonBreakdown: Map<String, Int>,
)

/**
 * 자동 제외된 기사를 REVIEW 상태로 복구한 결과.
 *
 * @property summaryId 복구된 batch_summaries.id.
 * @property newStatus 항상 `"REVIEW"` — 복구 후 최종 상태.
 */
data class RestoreFromAutoExcludeResult(
    val summaryId: String,
    val newStatus: String,
)
