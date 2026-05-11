/** Backfill 쿼리 키 팩토리. */
export const backfillKeys = {
  all: ["backfill"] as const,
  preview: (filter: { confidence: string; includeMedium: boolean; categoryId?: string }) =>
    [...backfillKeys.all, "preview", filter] as const,
};
