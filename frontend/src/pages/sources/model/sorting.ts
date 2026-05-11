import type { Source } from "@/types/source";

export type SourceSortField = "name" | "reliability" | "category" | "updatedAt";
export type SortDirection = "asc" | "desc";

export interface SourceSortState {
  field: SourceSortField;
  direction: SortDirection;
}

/** 정렬 방향 토글 헬퍼 */
export function nextSourceSort(
  current: SourceSortState,
  field: SourceSortField,
): SourceSortState {
  if (current.field === field) {
    return { field, direction: current.direction === "asc" ? "desc" : "asc" };
  }
  return { field, direction: "asc" };
}

/** 소스를 지정된 필드와 방향으로 정렬한다. */
export function sortSourcesBy(
  sources: Source[],
  sort: SourceSortState,
  categoryMap?: Map<string, { name: string }>,
): Source[] {
  const sorted = [...sources];
  const dir = sort.direction === "asc" ? 1 : -1;

  sorted.sort((a, b) => {
    let cmp = 0;

    switch (sort.field) {
      case "name":
        cmp = a.name.localeCompare(b.name, "ko");
        break;
      case "reliability":
        // 수집 건강 상태 기준 정렬: crawlFailCount가 낮을수록 건강
        cmp = a.crawlFailCount - b.crawlFailCount;
        break;
      case "category": {
        const ca = categoryMap?.get(a.categoryId)?.name ?? "";
        const cb = categoryMap?.get(b.categoryId)?.name ?? "";
        cmp = ca.localeCompare(cb, "ko");
        break;
      }
      case "updatedAt":
        cmp = a.updatedAt.localeCompare(b.updatedAt);
        break;
    }

    return cmp * dir;
  });

  return sorted;
}
