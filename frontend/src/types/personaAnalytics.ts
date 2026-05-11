/**
 * 페르소나 분석 도메인 타입.
 *
 * 백엔드 LiveSnapshotResponse 와 1:1 매칭. Slice 1 단계에서 시계열 인프라가
 * 없어 weekOverWeekDelta / engagementRate / lastDeliveredAt / newThisWeek 같은
 * 필드는 항상 null 이다 (Slice 2 부터 채워진다).
 *
 * Slice 2~5 에서 추가될 타입 (WeeklyTrendsResponse, PersonaAnomaly,
 * CustomClustersResponse 등) 은 해당 슬라이스 plan 에서 확장한다.
 */

export type PortfolioStatus = "HEALTHY" | "WATCHING" | "DECLINING" | "UNUSED";

export interface TotalsCard {
  totalStyles: number;
  presetCount: number;
  customCount: number;
  activeSubscriptions: number;
  /** 활성 구독 중 프리셋 페르소나가 차지하는 비율 (0.0 ~ 1.0). */
  presetUsageRate: number;
  /** 전체 스타일 중 커스텀 비율 (0.0 ~ 1.0). */
  customStyleRatio: number;
  /** 전주 대비 활성 구독 변화량. Slice 2 부터 채워진다. */
  weekOverWeekDelta: number | null;
}

export interface PresetPortfolioItem {
  personaId: string;
  personaName: string;
  activeSubs: number;
  /** Slice 2 부터 채워진다. */
  weekOverWeekDelta: number | null;
  /** 0.0 ~ 1.0. Slice 2 부터 채워진다. */
  engagementRate: number | null;
  status: PortfolioStatus;
  /** ISO timestamp. Slice 2 부터 채워진다. */
  lastDeliveredAt: string | null;
}

export interface RecentCustomPersona {
  id: string;
  personaName: string;
  userName: string;
  systemPromptPreview: string;
  createdAt: string;
}

export interface CustomSummary {
  totalCustomPersonas: number;
  activeCustomSubscriptions: number;
  /** Slice 2 부터 채워진다. */
  newThisWeek: number;
  recentPersonas: RecentCustomPersona[];
}

export interface LiveSnapshotResponse {
  totals: TotalsCard;
  presetPortfolio: PresetPortfolioItem[];
  customSummary: CustomSummary;
  asOf: string;
}

// ---------------------------------------------------------------------------
// Slice 2: 주간 트렌드 시계열
// ---------------------------------------------------------------------------

export interface PersonaTrendSeries {
  personaId: string;
  personaName: string;
  isPreset: boolean;
  activeSubs: number[];
  engagedUsers: number[];
  deliveredCount: number[];
}

export interface WeeklyTrendsResponse {
  weeks: string[];
  series: PersonaTrendSeries[];
}

export interface BackfillResult {
  weeksProcessed: number;
  personasAggregated: number;
  snapshotRowsCreated: number;
  durationMs: number;
}

export interface PersonaBatchRunDto {
  id: string;
  runId: string;
  triggerType: "SCHEDULED" | "MANUAL" | "BACKFILL";
  weekStart: string;
  startedAt: string;
  finishedAt: string | null;
  overallStatus: string;
  snapshotStatus: string | null;
  personasScanned: number;
  errorMessage: string | null;
}

// ---------------------------------------------------------------------------
// Persona signals (/api/admin/analytics/personas/signals)
// Spec: docs/superpowers/specs/2026-04-17-persona-insights-redesign-design.md §4.1
// Kotlin source of truth: service/analytics/dto/SignalsResponse.kt
// ---------------------------------------------------------------------------

export type RiskType = "CHURN_EXCESS" | "IDLE" | "ENGAGEMENT_DROP";
export type GrowthType = "SUBS_SURGE" | "ENGAGEMENT_RISE" | "FIRST_SUBSCRIPTION";
export type PersonaSignalType = RiskType | GrowthType;

export type ExcludedReason =
  | "CHURN_BASELINE_BELOW_MIN"
  | "ENGAGEMENT_DELIVERIES_BELOW_MIN"
  | "IDLE_NOT_PRESET";

// Detail payloads — discriminated union on the `type` field, matching the
// backend's `@JsonTypeInfo(property = "type")` sealed hierarchy.
export interface ChurnExcessDetails {
  type: "CHURN_EXCESS";
  churnedSubs: number;
  newSubs: number;
  activeSubs: number;
}

export interface IdleDetails {
  type: "IDLE";
  consecutiveWeeks: number;
  activeSubs: number;
}

export interface EngagementDropDetails {
  type: "ENGAGEMENT_DROP";
  engagementRate: number;
  prevEngagementRate: number;
  deltaPp: number;
  deliveredCount: number;
  totalClicks: number;
}

export interface SubsSurgeDetails {
  type: "SUBS_SURGE";
  activeSubs: number;
  prevActiveSubs: number;
  deltaAbs: number;
  deltaPct: number;
}

export interface EngagementRiseDetails {
  type: "ENGAGEMENT_RISE";
  engagementRate: number;
  prevEngagementRate: number;
  deltaPp: number;
  deliveredCount: number;
  totalClicks: number;
}

export interface FirstSubscriptionDetails {
  type: "FIRST_SUBSCRIPTION";
  activeSubs: number;
  daysSinceCreation: number;
}

export type RiskSignalDetails =
  | ChurnExcessDetails
  | IdleDetails
  | EngagementDropDetails;

export type GrowthSignalDetails =
  | SubsSurgeDetails
  | EngagementRiseDetails
  | FirstSubscriptionDetails;

export interface RiskSignalItem {
  personaId: string;
  personaName: string;
  isPreset: boolean;
  riskType: RiskType;
  persistentWeeks: number;
  details: RiskSignalDetails;
}

export interface GrowthSignalItem {
  personaId: string;
  personaName: string;
  isPreset: boolean;
  signalType: GrowthType;
  persistentWeeks: number;
  details: GrowthSignalDetails;
}

export interface ExcludedPersonaItem {
  personaId: string;
  personaName: string;
  reason: ExcludedReason;
}

export interface SignalsResponse {
  asOfWeekIso: string;
  asOfSnapshotDate: string;
  isWeekComplete: boolean;
  risks: RiskSignalItem[];
  growth: GrowthSignalItem[];
  excluded: ExcludedPersonaItem[];
}
