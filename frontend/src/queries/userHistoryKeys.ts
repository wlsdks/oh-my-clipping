export const userHistoryKeys = {
  all: ["userHistory"] as const,
  articles: (params?: Record<string, unknown>) =>
    [...userHistoryKeys.all, "articles", params] as const,
  detail: (id: string) => [...userHistoryKeys.all, "detail", id] as const,
  monthlyStats: (yearMonth: string) =>
    [...userHistoryKeys.all, "monthly", yearMonth] as const,
  undelivered: () => [...userHistoryKeys.all, "undelivered"] as const,
};
