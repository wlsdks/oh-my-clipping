export interface ReviewQueueItem {
  summaryId: string;
  categoryId: string;
  categoryName: string;
  title: string;
  summary: string;
  sourceLink: string;
  keywords: string[];
  importanceScore: number;
  suggestedStatus: "INCLUDE" | "REVIEW" | "EXCLUDE";
  currentStatus: "INCLUDE" | "REVIEW" | "EXCLUDE";
  statusReason: string;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  priorityScore: number;
  priorityLabel: string;
  createdAt: string;
}

export interface ReviewItemAudit {
  id: string;
  summaryId: string;
  categoryId: string;
  fromStatus?: "INCLUDE" | "REVIEW" | "EXCLUDE" | null;
  toStatus: "INCLUDE" | "REVIEW" | "EXCLUDE";
  reason?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  createdAt: string;
}

export interface ReviewSummaryResponse {
  totalCount: number;
  reviewCount: number;
  includeCount: number;
  excludeCount: number;
  categories: CategorySummary[];
}

export interface CategorySummary {
  categoryId: string;
  categoryName: string;
  totalCount: number;
  reviewCount: number;
  includeCount: number;
  excludeCount: number;
  suggestedIncludeCount: number;
}

export interface ReviewStatsResponse {
  period: string;
  totalReviewed: number;
  overallAccuracy: number;
  includeAccuracy: number;
  excludeAccuracy: number;
  overriddenCount: number;
  previousPeriodAccuracy: number | null;
  categoryBreakdown: CategoryAccuracy[];
}

export interface CategoryAccuracy {
  categoryId: string;
  categoryName: string;
  totalReviewed: number;
  accuracy: number;
  includeAccuracy: number;
  excludeAccuracy: number;
}

export interface BulkRevertItem {
  id: string;
  previousStatus: "INCLUDE" | "REVIEW" | "EXCLUDE";
}

export interface BulkActionResponse {
  succeeded: string[];
  failed: { id: string; reason: string; code: string }[];
}
