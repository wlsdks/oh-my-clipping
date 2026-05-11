import { api } from "@/lib/kyInstance";
import type {
  KeywordTrendResponse,
  CompetitorSnapshotItem,
  CompetitorTimelineItem,
  SovResponse,
  TopArticlesResponse,
  ReportSettings,
  SentimentTrendResponse
} from "@/types/newsReport";
import type { ReportReleaseItem } from "@/types/visualCard";

export const newsReportService = {
  getKeywordTrend: (params?: { days?: number; top?: number; categoryId?: string }): Promise<KeywordTrendResponse> => {
    const query = new URLSearchParams();
    if (typeof params?.days === "number") query.set("days", String(params.days));
    if (typeof params?.top === "number") query.set("top", String(params.top));
    if (params?.categoryId) query.set("categoryId", params.categoryId);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`admin/keywords/trend${suffix}`).json();
  },

  getCompetitorSnapshot: (params?: { days?: number; limit?: number }): Promise<{ items: CompetitorSnapshotItem[] }> => {
    const query = new URLSearchParams();
    if (typeof params?.days === "number") query.set("days", String(params.days));
    if (typeof params?.limit === "number") query.set("limit", String(params.limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`admin/competitors/snapshot${suffix}`).json();
  },

  getTopArticles: (params?: { days?: number; limit?: number; categoryId?: string }): Promise<TopArticlesResponse> => {
    const query = new URLSearchParams();
    if (params?.days) query.set("days", String(params.days));
    if (params?.limit) query.set("limit", String(params.limit));
    if (params?.categoryId) query.set("categoryId", params.categoryId);
    const qs = query.toString();
    return api.get(`admin/articles/top${qs ? `?${qs}` : ""}`).json();
  },

  getCompetitorTimeline: (params?: {
    days?: number;
    competitorId?: string;
    eventType?: string;
  }): Promise<{ items: CompetitorTimelineItem[] }> => {
    const query = new URLSearchParams();
    if (typeof params?.days === "number") query.set("days", String(params.days));
    if (params?.competitorId) query.set("competitorId", params.competitorId);
    if (params?.eventType) query.set("eventType", params.eventType);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`admin/competitors/timeline${suffix}`).json();
  },

  getCompetitorSov: (days = 30): Promise<SovResponse> =>
    api.get(`admin/competitors/sov?days=${encodeURIComponent(String(days))}`).json(),

  // ── 자동 리포트 설정 API ──
  getReportSettings: (): Promise<ReportSettings> => api.get("admin/report-settings").json(),

  updateReportSettings: (data: Partial<ReportSettings>): Promise<ReportSettings> =>
    api.put("admin/report-settings", { json: data }).json(),

  // ── 리포트 릴리즈 API ──
  listReportReleases: (params?: {
    days?: number;
    categoryId?: string;
    limit?: number;
  }): Promise<ReportReleaseItem[]> => {
    const query = new URLSearchParams();
    if (typeof params?.days === "number") query.set("days", String(params.days));
    if (params?.categoryId) query.set("categoryId", params.categoryId);
    if (typeof params?.limit === "number") query.set("limit", String(params.limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`admin/ops-reports/releases${suffix}`).json();
  },

  // ── 감성 트렌드 API ──
  getSentimentTrend: (params?: { days?: number; categoryId?: string }): Promise<SentimentTrendResponse> => {
    const query = new URLSearchParams();
    if (typeof params?.days === "number") query.set("days", String(params.days));
    if (params?.categoryId) query.set("categoryId", params.categoryId);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`admin/sentiment/trend${suffix}`).json();
  },

};
