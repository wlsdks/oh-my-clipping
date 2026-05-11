export const deliveryKeys = {
  all: ["delivery"] as const,
  summary: (date?: string) => [...deliveryKeys.all, "summary", date] as const,
  logs: () => [...deliveryKeys.all, "logs"] as const,
  logsList: (params?: Record<string, unknown>) =>
    [...deliveryKeys.logs(), "list", params] as const,
};
