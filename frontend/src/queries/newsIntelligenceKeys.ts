export const newsIntelligenceKeys = {
  all: ["news-intelligence"] as const,
  briefings: (params?: Record<string, unknown>) =>
    params
      ? ([...newsIntelligenceKeys.all, "briefings", params] as const)
      : ([...newsIntelligenceKeys.all, "briefings"] as const),
  keywordTrend: (params?: Record<string, unknown>) =>
    params
      ? ([...newsIntelligenceKeys.all, "keyword-trend", params] as const)
      : ([...newsIntelligenceKeys.all, "keyword-trend"] as const),
  competitorSnapshot: (params?: Record<string, unknown>) =>
    params
      ? ([...newsIntelligenceKeys.all, "competitor-snapshot", params] as const)
      : ([...newsIntelligenceKeys.all, "competitor-snapshot"] as const),
  competitorTimeline: (params?: Record<string, unknown>) =>
    params
      ? ([...newsIntelligenceKeys.all, "competitor-timeline", params] as const)
      : ([...newsIntelligenceKeys.all, "competitor-timeline"] as const),
  competitorSov: (params?: Record<string, unknown>) =>
    params
      ? ([...newsIntelligenceKeys.all, "competitor-sov", params] as const)
      : ([...newsIntelligenceKeys.all, "competitor-sov"] as const),
  competitorSentiment: (params?: Record<string, unknown>) =>
    params
      ? ([...newsIntelligenceKeys.all, "competitor-sentiment", params] as const)
      : ([...newsIntelligenceKeys.all, "competitor-sentiment"] as const),
  sentimentTrend: (params?: Record<string, unknown>) =>
    params
      ? ([...newsIntelligenceKeys.all, "sentiment-trend", params] as const)
      : ([...newsIntelligenceKeys.all, "sentiment-trend"] as const),
  topArticles: (params?: Record<string, unknown>) =>
    params
      ? ([...newsIntelligenceKeys.all, "top-articles", params] as const)
      : ([...newsIntelligenceKeys.all, "top-articles"] as const),
  keywordEntities: (params?: Record<string, unknown>) =>
    params
      ? ([...newsIntelligenceKeys.all, "keyword-entities", params] as const)
      : ([...newsIntelligenceKeys.all, "keyword-entities"] as const),
  userKeywordTrend: (params: { from: string; to: string; top: number }) =>
    [...newsIntelligenceKeys.all, "user-keyword-trend", params] as const,
};
