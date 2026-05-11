export const sourceKeys = {
  all: ["sources"] as const,
  lists: () => [...sourceKeys.all, "list"] as const,
  list: (params?: Record<string, unknown>) =>
    [...sourceKeys.all, "list", params] as const,
  listsByCategoryId: (categoryId: string) => [...sourceKeys.all, "list", { categoryId }] as const,
  detail: (id: string) => [...sourceKeys.all, "detail", id] as const,
  compliance: (id: string) => [...sourceKeys.all, "compliance", id] as const,
  complianceSummary: () => [...sourceKeys.all, "compliance-summary"] as const,
  articleCounts: (days: number) => [...sourceKeys.all, "article-counts", days] as const,
  analytics: (id: string, days: number) => [...sourceKeys.all, "analytics", id, days] as const,
  coverageGaps: () => [...sourceKeys.all, "coverage-gaps"] as const,
  crawlHistory: (id: string, days: number) => [...sourceKeys.all, "crawl-history", id, days] as const,
  aiCosts: (days: number) => [...sourceKeys.all, "ai-costs", days] as const,
};
