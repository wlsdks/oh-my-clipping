import type { DigestDiffListFilter } from "@/services/digestDiffService";

/** Digest diff 쿼리 키 팩토리. */
export const digestDiffKeys = {
  all: ["digestDiff"] as const,
  lists: () => [...digestDiffKeys.all, "list"] as const,
  list: (filter: DigestDiffListFilter) =>
    [...digestDiffKeys.lists(), filter] as const,
};
