export const visualCardKeys = {
  all: ["visual-cards"] as const,
  lists: (params?: Record<string, unknown>) =>
    params ? ([...visualCardKeys.all, "list", params] as const) : ([...visualCardKeys.all, "list"] as const),
  detail: (id: string) => [...visualCardKeys.all, "detail", id] as const,
  reportReleases: (params?: Record<string, unknown>) =>
    params
      ? ([...visualCardKeys.all, "report-releases", params] as const)
      : ([...visualCardKeys.all, "report-releases"] as const),
  trendSnapshots: (params?: Record<string, unknown>) =>
    params
      ? ([...visualCardKeys.all, "trend-snapshots", params] as const)
      : ([...visualCardKeys.all, "trend-snapshots"] as const)
};
