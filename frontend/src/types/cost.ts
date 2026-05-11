// Overview
export interface CostOverview {
  from: string;
  to: string;
  totalCostUsd: number;
  totalRequests: number;
  dailyAvgRequests: number;
  projectedMonthEndUsd: number;
  previousPeriodCostUsd: number;
  costChangePercent: number;
  budgetUsd: number | null;
  budgetUsedPercent: number | null;
  dailyBreakdown: DailyCostRow[];
}

export interface DailyCostRow {
  date: string;
  inputCostUsd: number;
  outputCostUsd: number;
  totalCostUsd: number;
  requestCount: number;
}

export interface HourlyCost {
  date: string;
  hours: HourlyCostRow[];
}

export interface HourlyCostRow {
  hour: number;
  inputCostUsd: number;
  outputCostUsd: number;
  totalCostUsd: number;
  requestCount: number;
}

// Models
export interface CostModels {
  from: string;
  to: string;
  modelCount: number;
  costPerArticleUsd: number;
  previousCostPerArticleUsd: number;
  models: ModelCostRow[];
  promptVersions: PromptVersionRow[];
  categoryBreakdown: CategoryCostRow[];
}

export interface ModelCostRow {
  model: string;
  requestCount: number;
  inputCostUsd: number;
  outputCostUsd: number;
  totalCostUsd: number;
  costPercent: number;
}

export interface PromptVersionRow {
  promptVersion: string;
  requestCount: number;
  avgTokensIn: number;
  avgTokensOut: number;
  costPerArticleUsd: number;
  avgDurationMs: number;
}

export interface CategoryCostRow {
  categoryId: string;
  categoryName: string;
  totalCostUsd: number;
  costPercent: number;
  requestCount: number;
}

// Reliability
export interface CostReliability {
  from: string;
  to: string;
  successRate: number;
  emptyResultRate: number;
  failureRate: number;
  avgDurationMs: number;
  p50DurationMs: number;
  p95DurationMs: number;
  dailyBreakdown: DailyReliabilityRow[];
  topErrors: ErrorGroupRow[];
}

export interface DailyReliabilityRow {
  date: string;
  succeeded: number;
  emptyResult: number;
  failed: number;
  avgDurationMs: number;
  p50DurationMs: number;
  p95DurationMs: number;
}

export interface ErrorGroupRow {
  errorPattern: string;
  count: number;
  lastOccurred: string;
  affectedCategories: string[];
}

// Detail
export interface CostDetail {
  from: string;
  to: string;
  inputCostPerMillionUsd: number;
  outputCostPerMillionUsd: number;
  rows: CostDetailRow[];
}

export interface CostDetailRow {
  channelId: string;
  categoryId: string;
  categoryName: string;
  requestCount: number;
  tokensIn: number;
  tokensOut: number;
  estimatedUsd: number;
  costPercent: number;
}

// LLM Cost Summary (for analytics CostTab)
export interface LlmCostSummary {
  from: string;
  to: string;
  inputCostPerMillionUsd: number;
  outputCostPerMillionUsd: number;
  totalRequestCount: number;
  totalTokensIn: number;
  totalTokensOut: number;
  totalEstimatedUsd: number;
  rows: LlmCostRow[];
}

export interface LlmCostRow {
  channelId: string;
  categoryId: string;
  categoryName: string;
  requestCount: number;
  tokensIn: number;
  tokensOut: number;
  estimatedUsd: number;
}

// Budget
export interface BudgetSettings {
  monthlyBudgetUsd: number;
  alertThresholdPercent: number;
  slackAlertEnabled: boolean;
}

export interface BudgetSettingsRequest {
  monthlyBudgetUsd: number;
  alertThresholdPercent: number;
  slackAlertEnabled: boolean;
}
