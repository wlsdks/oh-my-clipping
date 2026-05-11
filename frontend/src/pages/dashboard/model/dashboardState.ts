// computeSystemHealth와 SystemHealth는 utils/systemHealth.ts로 이전됐습니다.
// 기존 import가 깨지지 않도록 re-export합니다.
export type { SystemHealth } from "@/utils/systemHealth";
export { computeSystemHealth } from "@/utils/systemHealth";

// ─── 1. computeUrgencyPreview ──────────────────────────────────────────────

/** 두 Date 간의 달력 일수 차이를 반환한다 (UTC 기준 자정 비교). */
function calendarDaysDiff(later: Date, earlier: Date): number {
  const laterMidnight = Date.UTC(later.getUTCFullYear(), later.getUTCMonth(), later.getUTCDate());
  const earlierMidnight = Date.UTC(
    earlier.getUTCFullYear(),
    earlier.getUTCMonth(),
    earlier.getUTCDate(),
  );
  return Math.floor((laterMidnight - earlierMidnight) / (1000 * 60 * 60 * 24));
}

/** 두 Date 간의 시간 차이를 반환한다 (버림). */
function hoursDiff(later: Date, earlier: Date): number {
  return Math.floor((later.getTime() - earlier.getTime()) / (1000 * 60 * 60));
}

/**
 * 항목 배열에서 가장 오래된 createdAt 기준으로 "가장 오래된 N일 전" 또는 "N시간 전" 문자열을 반환한다.
 * 빈 배열이면 빈 문자열을 반환한다.
 */
export function computeUrgencyPreview<T extends { createdAt: string }>(
  items: T[],
  now: Date,
): string {
  if (items.length === 0) return "";
  const oldest = items.reduce((a, b) =>
    new Date(a.createdAt) < new Date(b.createdAt) ? a : b
  );
  const oldestDate = new Date(oldest.createdAt);
  const days = calendarDaysDiff(now, oldestDate);
  if (days >= 1) return `가장 오래된 ${days}일 전`;
  const hours = hoursDiff(now, oldestDate);
  return `가장 오래된 ${hours}시간 전`;
}

// ─── 2. classifyActionRequired ─────────────────────────────────────────────

export type BudgetLevel = "CRITICAL_90" | "CRITICAL_100" | null;
export type ActionRequiredSeverity = "warning" | "danger";
export type ActionRequiredType = "delivery_failed" | "pipeline_failed" | "budget_alert";

export interface ActionRequiredItem {
  type: ActionRequiredType;
  severity: ActionRequiredSeverity;
  count?: number;
  budgetLevel?: BudgetLevel;
}

/**
 * 발송/파이프라인 실패 수와 예산 레벨을 받아 조치 필요 항목 목록을 반환한다.
 * 문제가 없는 항목은 결과에 포함하지 않는다.
 */
export function classifyActionRequired(input: {
  deliveryFailures: number;
  pipelineFailures: number;
  budgetLevel: BudgetLevel;
}): ActionRequiredItem[] {
  const items: ActionRequiredItem[] = [];
  if (input.deliveryFailures > 0) {
    items.push({ type: "delivery_failed", severity: "danger", count: input.deliveryFailures });
  }
  if (input.pipelineFailures > 0) {
    items.push({ type: "pipeline_failed", severity: "danger", count: input.pipelineFailures });
  }
  if (input.budgetLevel === "CRITICAL_100") {
    items.push({ type: "budget_alert", severity: "danger", budgetLevel: "CRITICAL_100" });
  } else if (input.budgetLevel === "CRITICAL_90") {
    items.push({ type: "budget_alert", severity: "warning", budgetLevel: "CRITICAL_90" });
  }
  return items;
}

// ─── 3. computeClickRateTrend ──────────────────────────────────────────────

export interface TrendIndicator {
  direction: "up" | "down" | "neutral";
  deltaPct: number;
}

/**
 * 어제 클릭률과 7일 평균·표준편차를 비교해 추세 방향을 반환한다.
 * |z-score| ≤ 1이면 neutral, 초과하면 up 또는 down.
 */
export function computeClickRateTrend(
  yesterday: number,
  sevenDayAvg: number,
  sevenDayStdDev: number,
): TrendIndicator {
  const delta = yesterday - sevenDayAvg;
  const sigma = sevenDayStdDev || 0.0001;
  const zScore = Math.abs(delta) / sigma;
  if (zScore <= 1) return { direction: "neutral", deltaPct: delta };
  return { direction: delta > 0 ? "up" : "down", deltaPct: delta };
}

// ─── 4. computeSparklineData ───────────────────────────────────────────────

/**
 * 비용 일별 행 배열을 스파크라인 차트용 데이터로 변환한다.
 * date: "YYYY-MM-DD" → "MM/DD", value: totalCostUsd
 */
export function computeSparklineData(
  rows: Array<{ date: string; totalCostUsd: number }>,
): Array<{ date: string; value: number }> {
  return rows.map((r) => ({
    date: r.date.slice(5).replace("-", "/"),
    value: r.totalCostUsd,
  }));
}

/** 조치 필요 항목 한 건 */
export interface ActionItem {
  id: string;
  label: string;
  count: number;
  href: string;
  severity: "danger" | "warning" | "info";
}

interface ActionItemInput {
  pendingAccounts: number;
  pendingRequests: number;
  pendingReviews: number;
  failedDeliveries: number;
  failedRuns: number;
}

/** 심각도 정렬 우선순위 */
const SEVERITY_ORDER: Record<ActionItem["severity"], number> = {
  danger: 0,
  warning: 1,
  info: 2,
};

/**
 * 대시보드 상단의 "지금 확인이 필요해요" 카드에 노출할 액션 아이템 목록을 계산한다.
 * count가 0인 항목은 제외하며, 심각도(danger → warning → info) 순으로 정렬한다.
 */
export function computeActionItems(input: ActionItemInput): ActionItem[] {
  const items: ActionItem[] = [
    {
      id: "pending-accounts",
      label: "가입 승인",
      count: input.pendingAccounts,
      href: "/admin/user-accounts?tab=approval",
      severity: "danger",
    },
    {
      id: "failed-deliveries",
      label: "발송 실패",
      count: input.failedDeliveries,
      href: "/admin/delivery?status=FAILED",
      severity: "danger",
    },
    {
      id: "failed-runs",
      label: "파이프라인 실패",
      count: input.failedRuns,
      href: "/admin/pipeline?status=FAILED",
      severity: "danger",
    },
    {
      id: "pending-requests",
      label: "구독 요청 승인",
      count: input.pendingRequests,
      href: "/admin/subscriptions?filter=pending",
      severity: "warning",
    },
    {
      id: "pending-reviews",
      label: "뉴스 검토",
      count: input.pendingReviews,
      href: "/admin/review-queue",
      severity: "warning",
    },
  ];

  return items
    .filter((item) => item.count > 0)
    .sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity]);
}

interface SetupInput {
  categories: { slackChannelId?: string | null }[];
  settings: unknown[];
  personas: unknown[];
  runtime: object | null;
}

/**
 * 초기 세팅이 완료됐는지 판정한다.
 * - 카테고리/브리핑 설정/페르소나가 1개 이상
 * - 카테고리 중 하나에 Slack 채널이 지정됨
 */
export function isSetupComplete(input: SetupInput): boolean {
  if (input.categories.length === 0) return false;
  if (input.settings.length === 0) return false;
  if (input.personas.length === 0) return false;
  return input.categories.some((c) => Boolean(c.slackChannelId?.trim()));
}
