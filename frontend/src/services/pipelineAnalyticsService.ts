import { api } from "@/lib/kyInstance";

export interface PipelineSummaryResponse {
  todayCollected: number;
  todayDuplicateSkipped: number;
  todaySummarized: number;
  todayRejected: number;
  todayFailed: number;
  todayDeliverySent: number;
  todayDeliverySkipped: number;
  todayDeliveryFailed: number;
  todayCostUsd: number;
  monthlyBudgetUsagePercent: number;
}

export interface PipelineDailyRow {
  date: string;
  collected: number;
  duplicateSkipped: number;
  summarizeSucceeded: number;
  summarizeRejected: number;
  summarizeFailed: number;
  deliverySent: number;
  deliverySkipped: number;
  deliveryFailed: number;
}

export interface PipelinePeriodSummary {
  rejectReasons: Record<string, number>;
}

export interface PipelineDailyResponse {
  days: PipelineDailyRow[];
  periodSummary: PipelinePeriodSummary;
}

export interface DeliveryMatrixCategory {
  categoryId: string;
  categoryName: string;
  sent: number;
  skipped: number;
  failed: number;
}

export interface DeliveryMatrixUser {
  userId: string;
  username: string;
  categories: DeliveryMatrixCategory[];
}

export interface DeliveryMatrixResponse {
  users: DeliveryMatrixUser[];
}

export const pipelineAnalyticsService = {
  getSummary: (): Promise<PipelineSummaryResponse> =>
    api.get("admin/analytics/pipeline-summary").json(),

  getDaily: (days: number): Promise<PipelineDailyResponse> =>
    api.get(`admin/analytics/pipeline-daily?days=${days}`).json(),

  getDeliveryMatrix: (days: number): Promise<DeliveryMatrixResponse> =>
    api.get(`admin/analytics/delivery-matrix?days=${days}`).json(),
};
