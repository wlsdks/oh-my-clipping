import type { Category } from "@/types/category";
import { getCategoryStatus, type CategoryStatusType } from "./constants";

export type SortField = "name" | "status" | "sourceCount" | "subscriberCount" | "lastDeliveryAt";
export type SortDirection = "asc" | "desc";

export interface SortState {
  field: SortField;
  direction: SortDirection;
}

/** 상태 심각도 순서 (danger가 가장 높음) */
const STATUS_SEVERITY: Record<CategoryStatusType, number> = {
  danger: 3,
  warning: 2,
  neutral: 1,
  success: 0,
};

/** 카테고리를 지정된 필드와 방향으로 정렬한다. */
export function sortCategories(
  categories: Category[],
  sort: SortState,
): Category[] {
  const sorted = [...categories];
  const dir = sort.direction === "asc" ? 1 : -1;

  sorted.sort((a, b) => {
    let cmp = 0;

    switch (sort.field) {
      case "name":
        cmp = a.name.localeCompare(b.name, "ko");
        break;
      case "status": {
        const sa = STATUS_SEVERITY[getCategoryStatus(a).type];
        const sb = STATUS_SEVERITY[getCategoryStatus(b).type];
        cmp = sa - sb;
        break;
      }
      case "sourceCount":
        cmp = a.sourceCount - b.sourceCount;
        break;
      case "subscriberCount":
        cmp = a.subscriberCount - b.subscriberCount;
        break;
      case "lastDeliveryAt": {
        const da = a.lastDeliveryAt ?? "";
        const db = b.lastDeliveryAt ?? "";
        cmp = da.localeCompare(db);
        break;
      }
    }

    return cmp * dir;
  });

  return sorted;
}
