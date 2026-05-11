export const reviewKeys = {
  all: ["review"] as const,
  queue: (params?: Record<string, unknown>) =>
    params ? ([...reviewKeys.all, "queue", params] as const) : ([...reviewKeys.all, "queue"] as const),
  detail: (id: string) => [...reviewKeys.all, "detail", id] as const,
  audits: (summaryId: string) => [...reviewKeys.all, "audits", summaryId] as const,
  summary: () => [...reviewKeys.all, "summary"] as const,
  stats: (period: string) => [...reviewKeys.all, "stats", period] as const,
};
