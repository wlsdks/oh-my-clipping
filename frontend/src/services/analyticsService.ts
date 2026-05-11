import { api } from "@/lib/kyInstance";
import type {
  StatRow,
  DailyOperationalKpiRow,
  HotFeedbackResult,
  QualitySummary,
} from "@/types/insight";
import type { LlmCostSummary } from "@/types/cost";

function clampOpsQualityDays(days: number): number {
  return Math.min(Math.max(days, 7), 90);
}

export interface DauRow {
  date: string;
  count: number;
}

export interface WizardFunnelRow {
  step: string;
  enters: number;
  completes: number;
  dropRate: number;
}

export interface ArticleRankItem {
  rank: number;
  summaryId: string;
  title: string | null;
  categoryName: string | null;
  sourceName: string | null;
  publishedAt: string | null;
  clicks: number;
  impressions: number;
  ctr: number;
  bookmarks: number;
}

export interface ClickRateSummary {
  totalClicks: number;
  totalDeliveries: number;
  clickRate: number;
}

export interface CategoryStatItem {
  categoryId: string;
  categoryName: string;
  clicks: number;
  impressions: number;
  ctr: number;
  sharePercent: number;
}

export interface UserRequestStats {
  pendingCount: number;
  approvedCount: number;
  rejectedCount: number;
  totalCount: number;
  avgApprovalHours: number | null;
  topTopics: Array<{ requestName: string; count: number }>;
  rejectionReasons: Array<{ reason: string; count: number }>;
  weeklyProcessedCount: number;
}

export const analyticsService = {
  getDau: (days = 7): Promise<{ data: DauRow[] }> =>
    api.get(`admin/analytics/dau?days=${days}`).json(),

  getDauByRange: (from: string, to: string): Promise<{ data: DauRow[] }> =>
    api.get(`admin/analytics/dau?from=${from}&to=${to}`).json(),

  getWizardFunnel: (days = 30): Promise<{ data: WizardFunnelRow[] }> =>
    api.get(`admin/analytics/wizard-funnel?days=${days}`).json(),

  getArticleRanking: (
    from: string,
    to: string,
    sort = "clicks",
    limit = 20
  ): Promise<{ data: ArticleRankItem[] }> =>
    api
      .get(
        `admin/analytics/article-ranking?from=${from}&to=${to}&sort=${sort}&limit=${limit}`
      )
      .json(),

  getClickRateSummary: (days = 7): Promise<ClickRateSummary> =>
    api.get(`admin/analytics/click-rate?days=${days}`).json(),

  getCategoryStats: (from: string, to: string): Promise<{ data: CategoryStatItem[] }> =>
    api.get(`admin/analytics/category-stats?from=${from}&to=${to}`).json(),

  getUserRequestStats: (): Promise<UserRequestStats> =>
    api.get("admin/user-requests/stats").json(),

  // --- merged from insightService (admin-only endpoints) ---
  getMonthlyStats: (yearMonth: string, categoryId?: string): Promise<StatRow[]> => {
    const params = new URLSearchParams({ yearMonth });
    if (categoryId) params.set("categoryId", categoryId);
    return api.get(`admin/stats/monthly?${params.toString()}`).json();
  },

  getDailyOperationalKpi: (params: {
    categoryId?: string;
    from?: string;
    to?: string;
  }): Promise<DailyOperationalKpiRow[]> => {
    const query = new URLSearchParams();
    if (params.categoryId) query.set("categoryId", params.categoryId);
    if (params.from) query.set("from", params.from);
    if (params.to) query.set("to", params.to);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`admin/stats/daily-kpi${suffix}`).json();
  },

  getHotFeedback: (params: {
    categoryId?: string;
    limit?: number;
    days?: number;
  }): Promise<HotFeedbackResult> => {
    const query = new URLSearchParams();
    if (params.categoryId) query.set("categoryId", params.categoryId);
    if (typeof params.limit === "number") query.set("limit", String(params.limit));
    if (typeof params.days === "number") query.set("days", String(params.days));
    const suffix = query.size > 0 ? `?${query.toString()}` : "";
    return api.get(`admin/feedback/hot${suffix}`).json();
  },

  getOpsQualitySummary: (days = 30): Promise<QualitySummary> =>
    api.get(`admin/ops-reports/summary?days=${encodeURIComponent(String(clampOpsQualityDays(days)))}`).json(),

  // --- merged from costService ---
  getLlmCostSummary: (days = 30): Promise<LlmCostSummary> =>
    api.get(`admin/costs/llm?days=${encodeURIComponent(String(days))}`).json(),
};
