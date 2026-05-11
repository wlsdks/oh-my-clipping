/**
 * Legacy admin API client.
 *
 * Most methods have been migrated to domain services under `@/services/*`.
 * The remaining methods here are only used by `features/news-intelligence/`
 * and should be migrated there in a future pass.
 */
import type {
  KeywordEntityResponse,
  AiQaResponse,
  TopArticlesResponse,
} from "../types/admin";
import { requestJson } from "./httpClient";

export const adminApi = {
  // ── 키워드 엔티티 분류 API ──
  getKeywordEntities(params?: { days?: number; categoryId?: string }) {
    const query = new URLSearchParams();
    if (params?.days) query.set("days", String(params.days));
    if (params?.categoryId) query.set("categoryId", params.categoryId);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return requestJson<KeywordEntityResponse>(`/api/admin/keyword-entities${suffix}`);
  },

  // ── AI Q&A API ──
  askAiQuestion(payload: { question: string; days?: number; categoryId?: string }) {
    return requestJson<AiQaResponse>("/api/admin/report/ai-qa", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },

  // ── 기사 Top API (DrilldownModal) ──
  getTopArticles(params?: {
    days?: number;
    limit?: number;
    categoryId?: string;
    sentiment?: string;
    eventType?: string;
    keyword?: string;
    date?: string;
  }) {
    const query = new URLSearchParams();
    if (params?.days) query.set("days", String(params.days));
    if (params?.limit) query.set("limit", String(params.limit));
    if (params?.categoryId) query.set("categoryId", params.categoryId);
    if (params?.sentiment) query.set("sentiment", params.sentiment);
    if (params?.eventType) query.set("eventType", params.eventType);
    if (params?.keyword) query.set("keyword", params.keyword);
    if (params?.date) query.set("date", params.date);
    const qs = query.toString();
    return requestJson<TopArticlesResponse>(`/api/admin/articles/top${qs ? `?${qs}` : ""}`);
  },
};
