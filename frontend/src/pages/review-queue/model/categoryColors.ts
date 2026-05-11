/** Muted 10-color palette for category identification bars. */
export const CATEGORY_PALETTE = [
  "var(--chart-1, #3182F6)",
  "var(--chart-2, #00C880)",
  "var(--chart-3, #8B5CF6)",
  "var(--chart-4, #F59E0B)",
  "var(--chart-5, #06B6D4)",
  "var(--chart-6, #EC4899)",
  "var(--chart-7, #10B981)",
  "var(--chart-8, #6366F1)",
  "var(--chart-9, #F97316)",
  "var(--chart-10, #14B8A6)",
] as const;

function simpleHash(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 31 + str.charCodeAt(i)) | 0;
  }
  return Math.abs(hash);
}

export function getCategoryColor(categoryId: string): string {
  return CATEGORY_PALETTE[simpleHash(categoryId) % CATEGORY_PALETTE.length];
}
