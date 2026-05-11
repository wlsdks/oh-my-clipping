import type { BadgeKind } from "./sidebarTooltips";

export interface BadgeData {
  count: number;
  oldestCreatedAt: string | null;
}

export interface SidebarBadges {
  userAccounts: BadgeData;
  reviewQueue: BadgeData;
  subscriptions: BadgeData;
  delivery: BadgeData;
  pipeline: BadgeData;
  sources: BadgeData;
}

const BADGE_KIND_IDS: ReadonlySet<BadgeKind> = new Set<BadgeKind>([
  "userAccounts",
  "reviewQueue",
  "subscriptions",
  "delivery",
  "pipeline",
  "sources",
]);

export function isBadgeKind(id: string): id is BadgeKind {
  return (BADGE_KIND_IDS as ReadonlySet<string>).has(id);
}

/**
 * ISO-8601 날짜 문자열 배열에서 가장 오래된 값을 반환한다.
 * - null/undefined/빈 문자열은 건너뜀.
 * - `new Date(s).getTime()` 이 NaN 인 malformed 문자열도 건너뜀 (poisoning 방지).
 */
export function oldestIsoDate(dates: Array<string | undefined | null>): string | null {
  let oldest: string | null = null;
  let oldestTs = Number.POSITIVE_INFINITY;
  for (const d of dates) {
    if (!d) continue;
    const ts = new Date(d).getTime();
    if (Number.isNaN(ts)) continue;
    if (ts < oldestTs) {
      oldest = d;
      oldestTs = ts;
    }
  }
  return oldest;
}
