import type {
  ExcludedReason,
  GrowthType,
  RiskType,
} from "@/types/personaAnalytics";

/**
 * Persona risk/growth signal 한국어 라벨 + Tooltip 본문.
 *
 * 스펙 §5.3 — 라벨 분리: 백엔드는 영문 enum 만 내려주고 프론트가 사용자 표시 문구를 소유한다.
 * 컴포넌트 내부에 한국어 enum-to-label 맵을 직접 만들지 말고 이 상수를 참조한다.
 */

export const RISK_LABELS: Record<RiskType, string> = {
  CHURN_EXCESS: "이탈 초과",
  IDLE: "유휴",
  ENGAGEMENT_DROP: "참여 하락",
};

export const GROWTH_LABELS: Record<GrowthType, string> = {
  SUBS_SURGE: "구독 급증",
  ENGAGEMENT_RISE: "참여 상승",
  FIRST_SUBSCRIPTION: "첫 구독 진입",
};

export const EXCLUDED_REASON_LABELS: Record<ExcludedReason | string, string> = {
  CHURN_BASELINE_BELOW_MIN: "구독자 수 미달",
  ENGAGEMENT_DELIVERIES_BELOW_MIN: "발송 수 미달",
  IDLE_NOT_PRESET: "커스텀 페르소나 제외",
};

export const RISK_TOOLTIP: Record<RiskType, string> = {
  CHURN_EXCESS:
    "이탈 구독자 수가 새 구독자 수보다 많고, 누적 구독자가 기준(8)을 넘을 때.",
  IDLE: "최근 4주 연속 발송이 없고 이번 주 활성 구독자 0인 템플릿.",
  ENGAGEMENT_DROP:
    "참여율이 전주 대비 10pp 이상 하락. 양쪽 주 모두 발송 30건 이상.",
};

export const GROWTH_TOOLTIP: Record<GrowthType, string> = {
  SUBS_SURGE: "활성 구독자 수가 전주 대비 +20% 이상이면서 증가 절댓값 3 이상.",
  ENGAGEMENT_RISE:
    "참여율이 전주 대비 10pp 이상 상승. 양쪽 주 모두 발송 30건 이상.",
  FIRST_SUBSCRIPTION: "페르소나 생성 후 4주 이내 첫 활성 구독.",
};
