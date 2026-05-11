import { relativeTime } from "@/utils/date";
import type { Source } from "@/types/source";

/** 소스 건강 상태 — 크롤 실패 횟수 기반 단계 분류 */
export type HealthLevel = "healthy" | "warning" | "error" | "pending" | "archived";

export function getHealthLevel(source: {
  crawlApproved?: boolean;
  isActive: boolean;
  crawlFailCount: number;
  lastSuccessAt?: string | null;
}): HealthLevel {
  // 비활성(보관) 상태
  if (source.crawlApproved && !source.isActive) return "archived";
  // 10회 이상 실패 → 에러 (미승인보다 우선)
  if (source.crawlFailCount >= 10) return "error";
  // 미승인 또는 비활성
  if (!source.crawlApproved || !source.isActive) return "pending";
  // 1~9회 실패 → 주의
  if (source.crawlFailCount >= 1) return "warning";
  // 아직 한 번도 수집 성공하지 못함
  if (!source.lastSuccessAt) return "pending";
  return "healthy";
}

export type StatusKey = "error" | "unapproved" | "active" | "inactive";

export const STATUS_PRIORITY: Record<StatusKey, number> = {
  error: 0, unapproved: 1, active: 2, inactive: 3,
};

/** getHealthLevel 기반으로 그루핑 키를 반환한다 */
export function getStatusKey(s: Source): StatusKey {
  // verificationStatus FAILED는 crawlFailCount와 무관하게 에러 처리
  if (s.verificationStatus === "FAILED") return "error";
  const health = getHealthLevel(s);
  switch (health) {
    case "error": return "error";
    case "warning": return "active";
    case "healthy": return "active";
    case "pending": return s.crawlApproved ? "active" : "unapproved";
    case "archived": return "inactive";
  }
}

export function getSourceSubText(s: Source): string {
  const health = getHealthLevel(s);
  switch (health) {
    case "error":
      return `연결 실패 (${s.crawlFailCount}회) · ${relativeTime(s.updatedAt)} 수정`;
    case "warning":
      return `주의 (실패 ${s.crawlFailCount}회) · ${relativeTime(s.updatedAt)} 수정`;
    case "pending":
      if (!s.crawlApproved) return `승인 대기 중 · ${relativeTime(s.createdAt)} 등록`;
      return `수집 대기중 · ${relativeTime(s.updatedAt)} 수정`;
    case "healthy":
      return s.lastSuccessAt
        ? `마지막 수집 ${relativeTime(s.lastSuccessAt)} · ${relativeTime(s.updatedAt)} 수정`
        : `수집 대기중 · ${relativeTime(s.updatedAt)} 수정`;
    case "archived":
      return `비활성 · ${relativeTime(s.updatedAt)} 수정`;
  }
}

export function getIconBg(status: StatusKey): string {
  const map: Record<StatusKey, string> = {
    error: "bg-[var(--status-danger-bg)]",
    unapproved: "bg-[var(--status-warning-bg)]",
    active: "bg-[var(--status-neutral-bg)]",
    inactive: "bg-muted",
  };
  return map[status];
}

export function regionLabel(r: string): string {
  if (r === "DOMESTIC") return "국내";
  if (r === "GLOBAL") return "해외";
  return "";
}

export function sortSources(sources: Source[]): Source[] {
  return [...sources].sort((a, b) => {
    const pa = STATUS_PRIORITY[getStatusKey(a)];
    const pb = STATUS_PRIORITY[getStatusKey(b)];
    if (pa !== pb) return pa - pb;
    return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
  });
}
