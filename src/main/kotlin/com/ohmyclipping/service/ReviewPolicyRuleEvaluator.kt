package com.ohmyclipping.service

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.CategoryRule
import com.ohmyclipping.service.dto.RuleEvaluationResult
import com.ohmyclipping.store.RuntimeSettingStore
import org.springframework.stereotype.Service

/**
 * 리뷰 정책 룰 엔진.
 *
 * `AdminReviewQueueService.ensurePolicyReviewDecisions` 가 importance threshold 분기
 * (`suggestStatus` + `resolvePolicyAutoStatus`) **앞** 에서 호출한다. 룰이 발동하면
 * 자동 EXCLUDE 로 귀결시켜 이후 판정 흐름을 건너뛴다.
 *
 * 지원 룰 (적용 순서):
 *  1. `event_type_blacklist` — 카테고리별 `CategoryRule.excludeEventTypes` blacklist 에 걸리면
 *     즉시 EXCLUDE. V132 에서 추가된 카테고리 scope 룰.
 *  2. `zero_signal` — OTHER + NEUTRAL + include_keywords 가 비어있지 않은데 제목/본문 어디에도
 *     매칭되지 않으면 EXCLUDE. runtime setting 으로 전역 on/off 제어. 기본값 on.
 *
 * Over-trigger 방지 invariant:
 *  - zero_signal 은 `includeKeywords` 가 비어있으면 절대 발동하지 않는다. 아직 키워드를
 *    설정하지 않은 카테고리까지 대량 EXCLUDE 하는 사고를 막기 위함.
 *
 * 반환:
 *  - [RuleEvaluationResult.Exclude] — 룰 발동. `reason` 은 기계 판독용 식별자.
 *  - [RuleEvaluationResult.PassThrough] — 룰 비해당. 호출부는 기존 판정 흐름을 이어간다.
 */
@Service
class ReviewPolicyRuleEvaluator(
    private val settingStore: RuntimeSettingStore,
) {
    /**
     * 주어진 summary 에 대해 룰 엔진을 실행한다.
     *
     * @param summary 평가 대상 요약.
     * @param category 요약이 속한 카테고리 (현재 룰에서는 미사용이나 카테고리별 정책 확장 여지).
     * @param rule 카테고리별 운영 규칙 (`excludeEventTypes`, `includeKeywords` 사용).
     */
    fun evaluate(
        summary: BatchSummary,
        category: Category,
        rule: CategoryRule,
    ): RuleEvaluationResult {
        // 1. event_type blacklist 우선 적용 (short-circuit)
        val eventType = summary.eventType
        if (eventType != null && rule.excludeEventTypes.isNotEmpty() && eventType in rule.excludeEventTypes) {
            return RuleEvaluationResult.Exclude(REASON_EVENT_TYPE_BLACKLIST)
        }

        // 2. zero_signal — runtime setting 이 on 이고 모든 조건 충족 시
        if (isZeroSignalEnabled() && isZeroSignalMatch(summary, rule)) {
            return RuleEvaluationResult.Exclude(REASON_ZERO_SIGNAL)
        }

        return RuleEvaluationResult.PassThrough
    }

    /**
     * runtime setting `policy.rule.zero_signal_exclude.enabled` 가 `"true"` 인지 확인.
     * 값이 없거나 파싱 불가하면 비활성(false) 으로 해석한다.
     */
    private fun isZeroSignalEnabled(): Boolean =
        settingStore.findByKey(ZERO_SIGNAL_KEY)?.value?.trim()?.equals("true", ignoreCase = true) == true

    /**
     * zero_signal 룰 적용 조건 판정.
     *
     * 모두 충족할 때만 `true`:
     *  - `includeKeywords` 가 비어있지 않다 (over-trigger 방지 invariant).
     *  - `eventType == "OTHER"`.
     *  - `sentiment == "NEUTRAL"`.
     *  - 제목/요약 어디에도 `includeKeywords` 중 어느 것도 매칭되지 않는다.
     */
    private fun isZeroSignalMatch(summary: BatchSummary, rule: CategoryRule): Boolean {
        // include_keywords 비어있으면 발동 금지 (필수 invariant)
        if (rule.includeKeywords.isEmpty()) return false
        if (summary.eventType != "OTHER") return false
        if (summary.sentiment != "NEUTRAL") return false

        // 제목/본문 대소문자 무시로 매칭 여부 판정
        val titleLower = summary.originalTitle.lowercase()
        val summaryLower = summary.summary.lowercase()
        val anyMatch = rule.includeKeywords.any { kw ->
            val lower = kw.lowercase()
            titleLower.contains(lower) || summaryLower.contains(lower)
        }
        // 어떤 키워드도 매칭되지 않으면 zero-signal 로 판단
        return !anyMatch
    }

    companion object {
        /** zero-signal 룰 on/off 플래그 키. [RuleEngineSettingsBootstrap.ZERO_SIGNAL_KEY] 와 동일. */
        const val ZERO_SIGNAL_KEY = "policy.rule.zero_signal_exclude.enabled"

        /** event_type blacklist 룰 발동 시 reason. audit 에는 `rule:{이 값}` 으로 기록된다. */
        const val REASON_EVENT_TYPE_BLACKLIST = "event_type_blacklist"

        /** zero_signal 룰 발동 시 reason. audit 에는 `rule:{이 값}` 으로 기록된다. */
        const val REASON_ZERO_SIGNAL = "zero_signal"
    }
}
