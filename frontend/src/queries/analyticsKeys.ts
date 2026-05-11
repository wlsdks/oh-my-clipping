export const analyticsKeys = {
  all: ["analytics"] as const,
  dau: (days?: number) => ["analytics", "dau", days] as const,
  dauRange: (from: string, to: string) => ["analytics", "dau", from, to] as const,
  funnel: (days?: number) => ["analytics", "funnel", days] as const,
  articleRanking: (from: string, to: string, sort?: string, limit?: number) =>
    ["analytics", "articleRanking", from, to, sort, limit] as const,
  categoryStats: (from: string, to: string) => ["analytics", "categoryStats", from, to] as const,
  userRequestStats: () => ["analytics", "userRequestStats"] as const,

  // --- merged from insightKeys ---
  monthlyStats: (yearMonth: string, categoryId?: string) =>
    [...analyticsKeys.all, "monthlyStats", yearMonth, categoryId] as const,
  dailyKpi: (params?: Record<string, unknown>) =>
    [...analyticsKeys.all, "dailyKpi", params] as const,
  hotFeedback: (params?: Record<string, unknown>) =>
    [...analyticsKeys.all, "hotFeedback", params] as const,
  qualitySummary: (days?: number) =>
    [...analyticsKeys.all, "qualitySummary", days] as const,
  clickRate: (days?: number) =>
    [...analyticsKeys.all, "clickRate", days] as const,

  // --- merged from costKeys ---
  costSummary: (days?: number) =>
    [...analyticsKeys.all, "costSummary", days] as const,
};
