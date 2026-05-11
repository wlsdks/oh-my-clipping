export const newsReportKeys = {
  all: ["news-report"] as const,
  briefing: (params?: Record<string, unknown>) =>
    params ? ([...newsReportKeys.all, "briefing", params] as const) : ([...newsReportKeys.all, "briefing"] as const),
  keywordTrend: (params?: Record<string, unknown>) =>
    params
      ? ([...newsReportKeys.all, "keyword-trend", params] as const)
      : ([...newsReportKeys.all, "keyword-trend"] as const),
  competitorSnapshot: (params?: Record<string, unknown>) =>
    params
      ? ([...newsReportKeys.all, "competitor-snapshot", params] as const)
      : ([...newsReportKeys.all, "competitor-snapshot"] as const),
  competitorTimeline: (params?: Record<string, unknown>) =>
    params
      ? ([...newsReportKeys.all, "competitor-timeline", params] as const)
      : ([...newsReportKeys.all, "competitor-timeline"] as const),
  competitorSov: (params?: Record<string, unknown>) =>
    params
      ? ([...newsReportKeys.all, "competitor-sov", params] as const)
      : ([...newsReportKeys.all, "competitor-sov"] as const),
  topArticles: (params?: Record<string, unknown>) =>
    params
      ? ([...newsReportKeys.all, "top-articles", params] as const)
      : ([...newsReportKeys.all, "top-articles"] as const),
  competitors: () => [...newsReportKeys.all, "competitors"] as const,
  reportSettings: () => [...newsReportKeys.all, "report-settings"] as const,
  reportReleases: (params?: Record<string, unknown>) =>
    params
      ? ([...newsReportKeys.all, "report-releases", params] as const)
      : ([...newsReportKeys.all, "report-releases"] as const),
  sentimentTrend: (params?: Record<string, unknown>) =>
    params
      ? ([...newsReportKeys.all, "sentiment-trend", params] as const)
      : ([...newsReportKeys.all, "sentiment-trend"] as const),
  competitorArticleDetail: (summaryId: string) =>
    [...newsReportKeys.all, "competitor-article-detail", summaryId] as const,
};
