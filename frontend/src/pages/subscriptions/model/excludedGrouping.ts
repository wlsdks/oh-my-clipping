import type { ExcludedItem } from "@/types/category";
import { SAMPLES_PER_GROUP, MAX_KEYWORD_GROUPS } from "./excludedConstants";

export interface KeywordGroup {
  keyword: string;
  count: number;
  latestExcludedAt: string;
  samples: ExcludedItem[];
}

/** 제외 기사 목록을 matchedKeyword로 그룹화. null 키워드 항목은 제외. */
export function groupByKeyword(items: ExcludedItem[]): KeywordGroup[] {
  const map = new Map<string, ExcludedItem[]>();

  for (const item of items) {
    if (!item.matchedKeyword) continue;
    const arr = map.get(item.matchedKeyword) ?? [];
    arr.push(item);
    map.set(item.matchedKeyword, arr);
  }

  const groups: KeywordGroup[] = [];
  for (const [keyword, groupItems] of map.entries()) {
    const sorted = [...groupItems].sort((a, b) => b.excludedAt.localeCompare(a.excludedAt));
    groups.push({
      keyword,
      count: sorted.length,
      latestExcludedAt: sorted[0].excludedAt,
      samples: sorted.slice(0, SAMPLES_PER_GROUP),
    });
  }

  groups.sort((a, b) => {
    if (b.count !== a.count) return b.count - a.count;
    return b.latestExcludedAt.localeCompare(a.latestExcludedAt);
  });

  return groups.slice(0, MAX_KEYWORD_GROUPS);
}
