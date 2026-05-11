export const insightKeys = {
  all: ["insight"] as const,
  keywords: (params: { days: number; categoryId?: string }) =>
    [...insightKeys.all, "keywords", params] as const,
  sentiment: (params: { days: number; categoryId?: string }) =>
    [...insightKeys.all, "sentiment", params] as const,
};
