export const dashboardKeys = {
  all: ["dashboard"] as const,
  stats: () => [...dashboardKeys.all, "stats"] as const,
  articles: (params?: Record<string, unknown>) =>
    params ? ([...dashboardKeys.all, "articles", params] as const) : ([...dashboardKeys.all, "articles"] as const),
  forecast: () => [...dashboardKeys.all, "forecast"] as const,
  userEngagementTrend: () => [...dashboardKeys.all, "userEngagementTrend"] as const,
  activeSubscriptionsSummary: () => [...dashboardKeys.all, "activeSubscriptionsSummary"] as const,
  opsSummary: () => [...dashboardKeys.all, "opsSummary"] as const,
};
