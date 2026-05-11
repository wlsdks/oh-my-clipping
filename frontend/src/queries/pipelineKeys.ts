export const pipelineKeys = {
  all: ["pipeline"] as const,
  runs: () => [...pipelineKeys.all, "runs"] as const,
  runsList: (params?: Record<string, unknown>) =>
    [...pipelineKeys.runs(), "list", params] as const,
  runDetail: (runId: string) =>
    [...pipelineKeys.runs(), "detail", runId] as const,
  latest: (categoryId: string) =>
    [...pipelineKeys.all, "latest", categoryId] as const,
};
