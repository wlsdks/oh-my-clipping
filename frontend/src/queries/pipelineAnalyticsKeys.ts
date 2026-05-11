export const pipelineAnalyticsKeys = {
  all: ["pipeline-analytics"] as const,
  summary: () => [...pipelineAnalyticsKeys.all, "summary"] as const,
  daily: (days: number) => [...pipelineAnalyticsKeys.all, "daily", days] as const,
  deliveryMatrix: (days: number) => [...pipelineAnalyticsKeys.all, "delivery-matrix", days] as const,
};
