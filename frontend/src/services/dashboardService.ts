import { api } from "@/lib/kyInstance";
import type { ClippingSetting, PipelineRunResult } from "@/types/dashboard";
import type { OpsSummary } from "@/types/ops";

export interface ForecastResponse {
  expectedRunCount: number;
  expectedDigestCount: number;
  nextRunAtKst: string;
}

export interface UserEngagementTrendResponse {
  yesterdayClickRate: number;
  sevenDayAvgClickRate: number;
  sevenDayStdDev: number;
  feedbackPositiveYesterday: number;
  feedbackNegativeYesterday: number;
}

export interface ActiveSubscriptionsSummaryResponse {
  activeCount: number;
  newThisWeek: number;
  deactivatedThisWeek: number;
  netChange: number;
}

export const dashboardService = {
  listClippingSettings: (): Promise<ClippingSetting[]> => api.get("admin/clipping/settings").json(),

  runPipeline: (
    categoryId: string,
    data: {
      hoursBack?: number;
      maxItems?: number;
      unsentOnly?: boolean;
      sendToSlack?: boolean;
      slackChannelId?: string | null;
      ralphLoopEnabled?: boolean;
      ralphLoopMaxIterations?: number;
      ralphLoopStopPhrase?: string;
    }
  ): Promise<PipelineRunResult> =>
    api.post(`admin/clipping/${encodeURIComponent(categoryId)}/pipeline`, { json: data }).json(),

  getForecast: (): Promise<ForecastResponse> =>
    api.get("admin/dashboard/forecast").json<ForecastResponse>(),

  getUserEngagementTrend: (): Promise<UserEngagementTrendResponse> =>
    api.get("admin/dashboard/user-engagement-trend").json<UserEngagementTrendResponse>(),

  getActiveSubscriptionsSummary: (): Promise<ActiveSubscriptionsSummaryResponse> =>
    api.get("admin/dashboard/active-subscriptions-summary").json<ActiveSubscriptionsSummaryResponse>(),

  getOpsSummary: (): Promise<OpsSummary> =>
    api.get("admin/dashboard/ops-summary").json<OpsSummary>(),
};
