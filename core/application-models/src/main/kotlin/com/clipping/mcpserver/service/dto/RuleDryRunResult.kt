package com.clipping.mcpserver.service.dto

/**
 * 룰 dry-run 시뮬레이션 결과.
 *
 * 저장 전 "이 룰을 적용하면 최근 N 일 동안 몇 건이 자동 제외되는가" 를 미리보기 위해 사용한다.
 * `AdminReviewQueueService.dryRunRule` 가 실제 리뷰 결정/감사 이력을 건드리지 않고 평가만 수행해
 * 반환한다. 관리자 UI 가 룰 편집 화면에서 "적용 효과" 로 바인딩한다.
 *
 * @property analyzedCount 분석 대상으로 스캔한 summary 건수 (최근 N 일, 최대 500).
 * @property wouldAutoExclude 제안된 룰 적용 시 자동 EXCLUDE 되는 건수.
 * @property wouldStayUnchanged 룰이 발동하지 않아 기존 판정 흐름을 그대로 탈 건수.
 *   `analyzedCount - wouldAutoExclude` 항상 성립.
 * @property samples EXCLUDE 로 분류된 상위 N 개 샘플. UI 에서 "어떤 기사가 걸리는지" 확인용.
 */
data class RuleDryRunResult(
    val analyzedCount: Int,
    val wouldAutoExclude: Int,
    val wouldStayUnchanged: Int,
    val samples: List<DryRunSample>,
) {
    /**
     * dry-run 에서 EXCLUDE 로 분류된 개별 summary 의 미리보기 정보.
     *
     * @property summaryId batch_summaries.id — UI 에서 상세 조회 링크로 사용 가능.
     * @property title 사용자에게 노출할 기사 제목. translatedTitle 이 있으면 그것, 없으면 originalTitle.
     * @property eventType summary 의 AI 분류 event type (예: `OPINION`, `OTHER`). 룰 reason 과 대조용.
     * @property score importance score (0.0~1.0).
     * @property reason 기계 판독용 룰 식별자 — `event_type_blacklist` 또는 `zero_signal`.
     *   `ReviewPolicyRuleEvaluator` 의 reason 상수와 동일.
     */
    data class DryRunSample(
        val summaryId: String,
        val title: String,
        val eventType: String?,
        val score: Float,
        val reason: String,
    )
}
