/** Undo 토스트 유지 시간 (ms) */
export const TOAST_UNDO_DURATION_MS = 10_000;

/** 제외 기사 fetch limit */
export const EXCLUDED_ITEMS_LIMIT = 50;

/** 키워드 그룹당 표시 샘플 수 */
export const SAMPLES_PER_GROUP = 3;

/** 최대 키워드 그룹 표시 수 */
export const MAX_KEYWORD_GROUPS = 20;

/** 날짜 범위 옵션 (일 단위) */
export const DATE_RANGE_OPTIONS = [
  { value: 1, label: "오늘" },
  { value: 7, label: "7일" },
  { value: 30, label: "30일" },
] as const;

export type DateRangeValue = (typeof DATE_RANGE_OPTIONS)[number]["value"];
export const DEFAULT_DATE_RANGE: DateRangeValue = 7;
