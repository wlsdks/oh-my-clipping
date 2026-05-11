export const costKeys = {
  all: ["costs"] as const,
  overview: (params: { from: string; to: string; categoryId?: string }) =>
    [...costKeys.all, "overview", params] as const,
  hourly: (params: { date: string; categoryId?: string }) => [...costKeys.all, "hourly", params] as const,
  models: (params: { from: string; to: string; categoryId?: string }) => [...costKeys.all, "models", params] as const,
  reliability: (params: { from: string; to: string; categoryId?: string }) =>
    [...costKeys.all, "reliability", params] as const,
  detail: (params: { from: string; to: string; categoryId?: string }) => [...costKeys.all, "detail", params] as const,
  budget: () => [...costKeys.all, "budget"] as const,
  alertsCurrent: () => [...costKeys.all, "alertsCurrent"] as const,
};
