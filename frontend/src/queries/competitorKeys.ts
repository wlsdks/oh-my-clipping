export const competitorKeys = {
  all: ["competitors"] as const,
  lists: () => [...competitorKeys.all, "list"] as const,
  detail: (id: string) => [...competitorKeys.all, "detail", id] as const,
  timeline: (params?: Record<string, unknown>) => [...competitorKeys.all, "timeline", params] as const,
  sov: (params?: Record<string, unknown>) => [...competitorKeys.all, "sov", params] as const,
  preview: (keywords: string[]) => [...competitorKeys.all, "preview", keywords] as const,
};
