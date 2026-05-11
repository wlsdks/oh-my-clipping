export interface ClippingSetting {
  categoryId: string;
  categoryName: string;
  categoryUpdatedAt: string;
  isActive: boolean;
  slackChannelId?: string | null;
  maxItems: number;
  retentionKeepDays: number;
  retentionEnabled: boolean;
  retentionSource: string;
}

export interface DigestItemResult {
  summaryId: string;
  title: string;
  summary: string;
  keywords: string[];
  importanceScore: number;
  whyImportant: string;
  sourceLink: string;
  createdAt: string;
}

export interface DigestResult {
  categoryId: string;
  categoryName: string;
  unsentOnly: boolean;
  totalCandidates: number;
  selectedCount: number;
  postedToSlack: boolean;
  slackChannelId?: string | null;
  slackMessageTs?: string | null;
  markedSentCount: number;
  digestText: string;
  items: DigestItemResult[];
}

export interface CollectCategoryResult {
  categoryId: string;
  categoryName: string;
  collected: number;
  newItems: number;
}

export interface CollectResult {
  totalCollected: number;
  newItems: number;
  duplicateSkipped: number;
  categories: CollectCategoryResult[];
}

export interface SummarizeCategoryResult {
  categoryId: string;
  categoryName: string;
  summarized: number;
}

export interface SummarizeResult {
  totalSummarized: number;
  categories: SummarizeCategoryResult[];
}

export interface PipelineStepTrace {
  step: string;
  status: "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";
  startedAt: string;
  endedAt: string;
  detail?: string | null;
}

export interface PipelineRunResult {
  collect: CollectResult;
  summarize: SummarizeResult;
  digest: DigestResult;
  orchestrationMode?: "DETERMINISTIC" | "RALPH";
  fallbackApplied?: boolean;
  orchestrationWarnings?: string[];
  stepTraces?: PipelineStepTrace[];
  loopEnabled?: boolean;
  loopIterationCount?: number;
  loopStopReason?: "STOP_PHRASE_DETECTED" | "NO_PROGRESS" | "MAX_ITERATIONS_REACHED" | "LOOP_DISABLED";
  loopStopPhrase?: string | null;
}
