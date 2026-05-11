import type { UserAccountApproval } from "@/types/user";

// 내부 활동 상태 분류 (지표 카드 및 테이블 dot 색상에 사용)
export type ActivityStatus = "ACTIVE_7D" | "SEMI_ACTIVE_30D" | "INACTIVE_30D" | "NEVER";

// 단순화된 필터 활동 상태 (칩 3개: 전체 / 활성 / 비활성)
export type SimplifiedActivityFilter = "ALL" | "ACTIVE" | "INACTIVE";

// 필터 상태 타입
export interface MemberFilters {
  department: string | null; // null = 전체, "__none__" = 부서 없음
  role: "ALL" | "ADMIN" | "USER";
  activityStatus: SimplifiedActivityFilter;
  subscription: "ALL" | "HAS" | "NONE";
}

export const DEFAULT_FILTERS: MemberFilters = {
  department: null,
  role: "ALL",
  activityStatus: "ALL",
  subscription: "ALL",
};

const DAYS_MS = 24 * 60 * 60 * 1000;

/** lastLoginAt 값으로 내부 활동 상태를 분류한다 (지표 카드 / dot 색상용) */
export function getActivityStatus(lastLoginAt: string | null | undefined): ActivityStatus {
  if (!lastLoginAt) return "NEVER";

  const date = new Date(lastLoginAt);
  if (Number.isNaN(date.getTime())) return "NEVER";

  const diffDays = Math.floor((Date.now() - date.getTime()) / DAYS_MS);

  if (diffDays < 7) return "ACTIVE_7D";
  if (diffDays < 30) return "SEMI_ACTIVE_30D";
  return "INACTIVE_30D";
}

/** lastLoginAt 값으로 단순화된 활동 여부를 판별한다 (30일 기준) */
function isActiveWithin30Days(lastLoginAt: string | null | undefined): boolean {
  if (!lastLoginAt) return false;
  const date = new Date(lastLoginAt);
  if (Number.isNaN(date.getTime())) return false;
  const diffDays = Math.floor((Date.now() - date.getTime()) / DAYS_MS);
  return diffDays < 30;
}

/** 회원이 필터 조건에 일치하는지 판별한다 (AND 조건) */
export function matchesFilters(member: UserAccountApproval, filters: MemberFilters): boolean {
  // 부서 필터
  if (filters.department !== null) {
    if (filters.department === "__none__") {
      if (member.department != null && member.department !== "") return false;
    } else {
      if (member.department !== filters.department) return false;
    }
  }

  // 역할 필터
  if (filters.role !== "ALL" && member.role !== filters.role) return false;

  // 활동 상태 필터 (30일 기준: 활성/비활성)
  if (filters.activityStatus !== "ALL") {
    const active = isActiveWithin30Days(member.lastLoginAt);
    if (filters.activityStatus === "ACTIVE" && !active) return false;
    if (filters.activityStatus === "INACTIVE" && active) return false;
  }

  // 구독 필터
  if (filters.subscription !== "ALL") {
    const count = member.subscriptionCount ?? 0;
    if (filters.subscription === "HAS" && count === 0) return false;
    if (filters.subscription === "NONE" && count > 0) return false;
  }

  return true;
}

/** 활동 상태별 dot 색상 클래스를 반환한다 */
export function getActivityDotClass(status: ActivityStatus): string {
  switch (status) {
    case "ACTIVE_7D":
      return "bg-[var(--status-success-text)]";
    case "SEMI_ACTIVE_30D":
      return "bg-[var(--status-warning-text)]";
    case "INACTIVE_30D":
      return "bg-[var(--status-danger-text)]";
    case "NEVER":
      return "bg-muted-foreground/50";
  }
}

/** 필터가 기본값인지 확인한다 */
export function isDefaultFilters(filters: MemberFilters): boolean {
  return (
    filters.department === null &&
    filters.role === "ALL" &&
    filters.activityStatus === "ALL" &&
    filters.subscription === "ALL"
  );
}

/** 데이터에서 고유 부서 목록을 추출한다 (정렬됨) */
export function extractDepartments(members: UserAccountApproval[]): string[] {
  const depts = new Set<string>();
  for (const m of members) {
    if (m.department) depts.add(m.department);
  }
  return [...depts].sort();
}
