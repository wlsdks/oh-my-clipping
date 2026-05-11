package com.ohmyclipping.service.analytics.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate

/**
 * `GET /api/admin/analytics/personas/signals` 응답.
 *
 * 특정 주차 스냅샷 기준으로 위험/성장 페르소나 목록과, noise floor 미달로
 * 분석에서 제외된 페르소나 메타를 함께 돌려준다. 스펙은
 * `docs/superpowers/specs/2026-04-17-persona-insights-redesign-design.md` §4.1.
 */
data class SignalsResponse(
    /** ISO 주차 (`2026-W16` 형식). */
    val asOfWeekIso: String,
    /** 사용된 스냅샷의 `week_start` (KST 월요일). */
    val asOfSnapshotDate: LocalDate,
    /** 해당 주가 완결됐는지 (오늘 ≥ week_start + 7일). */
    val isWeekComplete: Boolean,
    val risks: List<RiskSignalItem>,
    val growth: List<GrowthSignalItem>,
    val excluded: List<ExcludedPersonaItem>
)

/** 위험 카드 1건. */
data class RiskSignalItem(
    val personaId: String,
    val personaName: String,
    val isPreset: Boolean,
    val riskType: RiskSignalType,
    /** 연속 주차. NEW = 1. 중간 OFF 1주 이상이면 리셋. */
    val persistentWeeks: Int,
    val details: SignalDetails
)

/** 성장 카드 1건. */
data class GrowthSignalItem(
    val personaId: String,
    val personaName: String,
    val isPreset: Boolean,
    val signalType: GrowthSignalType,
    val persistentWeeks: Int,
    val details: SignalDetails
)

/** noise floor 미달로 판정에서 제외된 페르소나. UI 포트폴리오 섹션 Tooltip 용. */
data class ExcludedPersonaItem(
    val personaId: String,
    val personaName: String,
    val reason: ExcludedReason
)

enum class RiskSignalType { CHURN_EXCESS, IDLE, ENGAGEMENT_DROP }

enum class GrowthSignalType { SUBS_SURGE, ENGAGEMENT_RISE, FIRST_SUBSCRIPTION }

enum class ExcludedReason {
    CHURN_BASELINE_BELOW_MIN,
    ENGAGEMENT_DELIVERIES_BELOW_MIN,
    IDLE_NOT_PRESET
}

/**
 * 신호 상세 — 재계산 가능한 숫자들을 담는다.
 * 프론트에서 `"prev → current (±Δabs)"` 같은 카드 렌더 규약을 충족시키도록
 * before / current / delta 값을 모두 포함한다.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ChurnExcessDetails::class, name = "CHURN_EXCESS"),
    JsonSubTypes.Type(value = IdleDetails::class, name = "IDLE"),
    JsonSubTypes.Type(value = EngagementDropDetails::class, name = "ENGAGEMENT_DROP"),
    JsonSubTypes.Type(value = SubsSurgeDetails::class, name = "SUBS_SURGE"),
    JsonSubTypes.Type(value = EngagementRiseDetails::class, name = "ENGAGEMENT_RISE"),
    JsonSubTypes.Type(value = FirstSubscriptionDetails::class, name = "FIRST_SUBSCRIPTION")
)
sealed interface SignalDetails

data class ChurnExcessDetails(
    val churnedSubs: Int,
    val newSubs: Int,
    val activeSubs: Int
) : SignalDetails

data class IdleDetails(
    /** 연속 유휴 주 수 (이번 주 포함). */
    val consecutiveWeeks: Int,
    val activeSubs: Int
) : SignalDetails

data class EngagementDropDetails(
    val engagementRate: Double,
    val prevEngagementRate: Double,
    /** 퍼센트포인트 변화. 음수. 예: -12. */
    val deltaPp: Int,
    val deliveredCount: Int,
    val totalClicks: Int
) : SignalDetails

data class SubsSurgeDetails(
    val activeSubs: Int,
    val prevActiveSubs: Int,
    val deltaAbs: Int,
    val deltaPct: Int
) : SignalDetails

data class EngagementRiseDetails(
    val engagementRate: Double,
    val prevEngagementRate: Double,
    /** 퍼센트포인트 변화. 양수. 예: +12. */
    val deltaPp: Int,
    val deliveredCount: Int,
    val totalClicks: Int
) : SignalDetails

data class FirstSubscriptionDetails(
    val activeSubs: Int,
    /** 페르소나 생성 후 경과 일 수. */
    val daysSinceCreation: Int
) : SignalDetails
