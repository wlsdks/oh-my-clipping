export const dbMetricsKeys = {
  all: ["db-metrics"] as const,
  snapshot: () => [...dbMetricsKeys.all, "snapshot"] as const,
};
