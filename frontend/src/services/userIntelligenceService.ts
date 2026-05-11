import { api } from "@/lib/kyInstance";
import type {
  BriefingItem,
  KeywordTrendResponse,
  CompetitorSnapshotItem,
  CompetitorTimelineItem,
  TopArticlesResponse,
} from "@/types/newsReport";
import type { ArticleDetail } from "@/types/insight";

export const userIntelligenceService = {
  getBriefing: (categoryId?: string): Promise<{ briefings: BriefingItem[] }> => {
    const params = new URLSearchParams();
    if (categoryId) params.set("categoryId", categoryId);
    const qs = params.toString();
    return api.get(`user/briefing/today${qs ? `?${qs}` : ""}`).json();
  },

  getKeywordTrend: (params?: {
    days?: number;
    top?: number;
    categoryId?: string;
    from?: string;
    to?: string;
  }): Promise<KeywordTrendResponse> => {
    const sp = new URLSearchParams();
    if (params?.from) sp.set("from", params.from);
    if (params?.to) sp.set("to", params.to);
    if (params?.days) sp.set("days", String(params.days));
    if (params?.top) sp.set("top", String(params.top));
    if (params?.categoryId) sp.set("categoryId", params.categoryId);
    const qs = sp.toString();
    return api.get(`user/keywords/trend${qs ? `?${qs}` : ""}`).json();
  },

  getCompetitorSnapshot: (params?: {
    days?: number;
    limit?: number;
  }): Promise<{ items: CompetitorSnapshotItem[] }> => {
    const sp = new URLSearchParams();
    if (params?.days) sp.set("days", String(params.days));
    if (params?.limit) sp.set("limit", String(params.limit));
    const qs = sp.toString();
    return api.get(`user/competitors/snapshot${qs ? `?${qs}` : ""}`).json();
  },

  getCompetitorTimeline: (params?: {
    days?: number;
    competitorId?: string;
    eventType?: string;
  }): Promise<{ items: CompetitorTimelineItem[] }> => {
    const sp = new URLSearchParams();
    if (params?.days) sp.set("days", String(params.days));
    if (params?.competitorId) sp.set("competitorId", params.competitorId);
    if (params?.eventType) sp.set("eventType", params.eventType);
    const qs = sp.toString();
    return api.get(`user/competitors/timeline${qs ? `?${qs}` : ""}`).json();
  },

  getCompetitorArticleDetail: (summaryId: string): Promise<ArticleDetail> =>
    api.get(`user/competitors/articles/${encodeURIComponent(summaryId)}`).json(),

  getTopArticles: (params?: {
    days?: number;
    limit?: number;
    categoryId?: string;
    sentiment?: string;
    eventType?: string;
    keyword?: string;
    date?: string;
    from?: string;
    to?: string;
  }): Promise<TopArticlesResponse> => {
    const query = new URLSearchParams();
    if (params?.from) query.set("from", params.from);
    if (params?.to) query.set("to", params.to);
    if (params?.days) query.set("days", String(params.days));
    if (params?.limit) query.set("limit", String(params.limit));
    if (params?.categoryId) query.set("categoryId", params.categoryId);
    if (params?.sentiment) query.set("sentiment", params.sentiment);
    if (params?.eventType) query.set("eventType", params.eventType);
    if (params?.keyword) query.set("keyword", params.keyword);
    if (params?.date) query.set("date", params.date);
    const qs = query.toString();
    return api.get(`user/articles/top${qs ? `?${qs}` : ""}`).json();
  },

  triggerPipeline: (categoryId: string, data: { sendToSlack?: boolean; unsentOnly?: boolean }): Promise<unknown> =>
    api.post(`user/setup/clipping/${encodeURIComponent(categoryId)}/pipeline`, { json: data }).json(),
};
