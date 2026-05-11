export const userClippingKeys = {
  all: ["user"] as const,
  briefingToday: () => [...userClippingKeys.all, "briefing", "today"] as const,
  competitorSnapshot: (params: Record<string, unknown>) =>
    [...userClippingKeys.all, "competitor-snapshot", params] as const,
  keywordTrendAll: (from: string, to: string) =>
    [...userClippingKeys.all, "keyword-trend", "all", from, to] as const,
  keywordTrendCategories: (from: string, to: string, categoryIds: string) =>
    [...userClippingKeys.all, "keyword-trend", "categories", from, to, categoryIds] as const,
  topArticles: (from: string, to: string) =>
    [...userClippingKeys.all, "top-articles", from, to] as const,
};
