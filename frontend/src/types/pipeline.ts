export type PipelineStepName = "COLLECT" | "SUMMARIZE" | "DIGEST";
export type PipelineRunStatusType = "RUNNING" | "SUCCEEDED" | "FAILED" | "PARTIAL";
export type PipelineStepStatusType = "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";

export interface PipelineStepTraceRecord {
  id: string;
  step: PipelineStepName;
  status: PipelineStepStatusType;
  startedAt: string;
  endedAt?: string | null;
  durationMs?: number | null;
  detail?: string | null;
}

export interface PipelineRunRecord {
  id: string;
  categoryId: string;
  categoryName?: string | null;
  triggeredBy?: string | null;
  status: PipelineRunStatusType;
  orchestrationMode?: string | null;
  totalCollected: number;
  totalSummarized: number;
  totalDigestSelected: number;
  postedToSlack: boolean;
  startedAt: string;
  endedAt?: string | null;
  durationMs?: number | null;
  errorMessage?: string | null;
  stepTraces?: PipelineStepTraceRecord[] | null;
}

export interface PipelineRunsPage {
  content: PipelineRunRecord[];
  totalCount: number;
  page: number;
  size: number;
}

export interface PipelineExecuteResponse {
  runId: string;
}
