import type { UserMonthlyStatRow } from "@/types/insight";

/** "급상승" 배너를 띄울 최소 변화율 (30% 이상) */
export const RISING_CHANGE_RATE_THRESHOLD = 0.3;
/** "급상승" 배너를 띄울 최소 등장 빈도. 1~2건짜리 키워드의 +N%가 헤드라인으로 올라오지 않게 한다. */
export const RISING_MIN_TOTAL_COUNT = 5;

export interface ChangeBadge {
  label: string;
  type: "up" | "down" | "flat" | "none";
}

/**
 * 전월 대비 변화율을 계산해 라벨과 의미(type)을 함께 반환한다.
 *
 * - `prevLoaded=false`: 직전 달 데이터가 아직 준비되지 않았거나 조회 실패.
 *   "-"로 표시하고 up/down 색상을 적용하지 않는다 (로딩 중 깜빡임 방지).
 * - `prev=0 & cur>0`: 첫 데이터. "신규" 뱃지.
 * - 그 외: ±N% 변화.
 */
export function formatChange(current: number, previous: number, prevLoaded: boolean): ChangeBadge {
  if (!prevLoaded) return { label: "-", type: "none" };
  if (previous === 0) return { label: current > 0 ? "신규" : "-", type: current > 0 ? "up" : "none" };
  const diff = current - previous;
  const pct = Math.round((diff / previous) * 100);
  if (pct === 0) return { label: "동일", type: "flat" };
  if (pct > 0) return { label: `+${pct}%`, type: "up" };
  return { label: `${pct}%`, type: "down" };
}

/** 키워드 트렌드 단일 항목 */
export interface TrendKeyword {
  keyword: string;
  totalCount: number;
  /** 월 후반부 평균 / 전반부 평균 대비 변화율 (0.42 = +42%) */
  changeRate: number;
}

/**
 * "급상승" 배너의 헤드라인 키워드를 고른다.
 *
 * 두 조건을 모두 만족해야 한다:
 *  (a) `changeRate >= RISING_CHANGE_RATE_THRESHOLD` — +5%를 "급상승"으로 과장하지 않는다.
 *  (b) `totalCount >= RISING_MIN_TOTAL_COUNT` — 1~2건짜리 키워드가 헤드라인으로 올라오지 않게 한다.
 */
export function pickTopRisingKeyword(keywords: TrendKeyword[]): TrendKeyword | null {
  const candidates = keywords.filter(
    (k) => k.changeRate >= RISING_CHANGE_RATE_THRESHOLD && k.totalCount >= RISING_MIN_TOTAL_COUNT
  );
  if (candidates.length === 0) return null;
  return [...candidates].sort((a, b) => b.changeRate - a.changeRate)[0];
}

/** 카테고리별 집계 (중복 일자 합산) */
export interface CategoryAggregate {
  categoryId: string;
  categoryName: string;
  itemsSent: number;
  topKeywords: string[];
}

/**
 * 동일 카테고리의 여러 일자 행을 하나로 합산한다.
 * `topKeywords`는 등장 빈도 내림차순의 합집합으로 정렬한다.
 * 반환 순서는 `itemsSent` 내림차순.
 */
export function aggregateByCategory(stats: UserMonthlyStatRow[]): CategoryAggregate[] {
  const byId = new Map<string, CategoryAggregate & { keywordCounts: Map<string, number> }>();
  for (const row of stats) {
    const existing = byId.get(row.categoryId);
    if (existing) {
      existing.itemsSent += row.itemsSent;
      for (const kw of row.topKeywords) {
        existing.keywordCounts.set(kw, (existing.keywordCounts.get(kw) ?? 0) + 1);
      }
    } else {
      const keywordCounts = new Map<string, number>();
      for (const kw of row.topKeywords) keywordCounts.set(kw, 1);
      byId.set(row.categoryId, {
        categoryId: row.categoryId,
        categoryName: row.categoryName,
        itemsSent: row.itemsSent,
        topKeywords: [],
        keywordCounts
      });
    }
  }
  return Array.from(byId.values())
    .map(({ keywordCounts, ...rest }) => ({
      ...rest,
      topKeywords: Array.from(keywordCounts.entries())
        .sort((a, b) => b[1] - a[1])
        .map(([kw]) => kw)
    }))
    .sort((a, b) => b.itemsSent - a.itemsSent);
}
