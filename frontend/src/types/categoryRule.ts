export interface CategoryRuleDryRunRequest {
  excludeEventTypes: string[];
  days?: number;
  maxSamples?: number;
}

export interface DryRunSample {
  summaryId: string;
  title: string;
  eventType: string | null;
  score: number;
  reason: string;
}

export interface RuleDryRunResult {
  analyzedCount: number;
  wouldAutoExclude: number;
  wouldStayUnchanged: number;
  samples: DryRunSample[];
}
