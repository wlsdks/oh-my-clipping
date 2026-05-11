package com.ohmyclipping.service.dto

/**
 * 리뷰 정책 룰 평가 결과.
 *
 * `AdminReviewQueueService.ensurePolicyReviewDecisions` 가 기존 importance threshold 분기
 * (`suggestStatus` + `resolvePolicyAutoStatus`) **앞** 단계에서 호출한다.
 *
 * - [PassThrough]: 룰이 비해당이므로 기존 threshold 기반 판정 흐름을 그대로 이어간다.
 * - [Exclude]: 룰이 발동해 즉시 자동 EXCLUDE. [Exclude.reason] 은 audit/reason prefix
 *   (`rule:{name}`) 로 쓰이는 식별자이며 한국어 설명은 호출부에서 합성한다.
 */
sealed class RuleEvaluationResult {
    /** 적용된 룰이 없음. 호출부가 기존 판정 흐름을 계속 실행한다. */
    data object PassThrough : RuleEvaluationResult()

    /**
     * 룰 발동으로 자동 EXCLUDE.
     *
     * @property reason 기계 판독 가능한 룰 이름 (예: `event_type_blacklist`, `zero_signal`).
     *   audit/reason prefix 는 `rule:{reason}` 으로 기록된다.
     */
    data class Exclude(val reason: String) : RuleEvaluationResult()
}
