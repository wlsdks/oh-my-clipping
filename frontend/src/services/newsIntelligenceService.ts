import { api } from "@/lib/kyInstance";
import type {
  BriefingItem,
  KeywordTrendResponse,
  CompetitorSnapshotItem,
  CompetitorTimelineItem,
  SovResponse,
  TopArticlesResponse,
  SentimentTrendResponse,
  CompetitorSentimentResponse,
  KeywordEntityResponse,
  AiQaResponse,
} from "@/shared/types/admin";

function buildQuery(params: Record<string, string | number | undefined | null>): string {
  const sp = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value != null && value !== "") sp.set(key, String(value));
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export const newsIntelligenceService = {
  /** 오늘의 뉴스 브리핑 조회 (user-facing) */
  getBriefings: (categoryId?: string): Promise<{ briefings: BriefingItem[] }> =>
    api.get(`user/briefing/today${buildQuery({ categoryId })}`).json(),

  /** 키워드 트렌드 조회 (admin) */
  getKeywordTrend: (params?: {
    days?: number;
    top?: number;
    categoryId?: string;
  }): Promise<KeywordTrendResponse> =>
    api.get(`admin/keywords/trend${buildQuery(params ?? {})}`).json(),

  /** 키워드 트렌드 조회 (user — from/to 기간 지정) */
  getUserKeywordTrend: (params: {
    from: string;
    to: string;
    top?: number;
    categoryId?: string;
  }): Promise<KeywordTrendResponse> =>
    api.get(`user/keywords/trend${buildQuery(params)}`).json(),

  /** 경쟁사 스냅샷 조회 */
  getCompetitorSnapshot: (params?: {
    days?: number;
    limit?: number;
  }): Promise<{ items: CompetitorSnapshotItem[] }> =>
    api.get(`admin/competitors/snapshot${buildQuery(params ?? {})}`).json(),

  /** 경쟁사 타임라인 조회 */
  getCompetitorTimeline: (params?: {
    days?: number;
    competitorId?: string;
    eventType?: string;
    sentiment?: string;
  }): Promise<{ items: CompetitorTimelineItem[] }> =>
    api.get(`admin/competitors/timeline${buildQuery(params ?? {})}`).json(),

  /** 경쟁사 기사 점유율 */
  getCompetitorSov: (days = 30): Promise<SovResponse> =>
    api.get(`admin/competitors/sov${buildQuery({ days })}`).json(),

  /** 경쟁사 논조 비교 */
  getCompetitorSentiment: (days = 7): Promise<CompetitorSentimentResponse> =>
    api.get(`admin/competitors/sentiment${buildQuery({ days })}`).json(),

  /** 논조 추이 */
  getSentimentTrend: (params?: {
    days?: number;
    categoryId?: string;
  }): Promise<SentimentTrendResponse> =>
    api.get(`admin/sentiment/trend${buildQuery(params ?? {})}`).json(),

  /** 주요 기사 목록 */
  getTopArticles: (params?: {
    days?: number;
    limit?: number;
    categoryId?: string;
    sentiment?: string;
    eventType?: string;
    keyword?: string;
    date?: string;
  }): Promise<TopArticlesResponse> =>
    api.get(`admin/articles/top${buildQuery(params ?? {})}`).json(),

  /** 키워드 엔티티 분류 */
  getKeywordEntities: (params?: {
    days?: number;
    categoryId?: string;
  }): Promise<KeywordEntityResponse> =>
    api.get(`admin/keyword-entities${buildQuery(params ?? {})}`).json(),

  /** AI Q&A */
  askAiQuestion: (payload: {
    question: string;
    days?: number;
    categoryId?: string;
  }): Promise<AiQaResponse> =>
    api.post("admin/report/ai-qa", { json: payload }).json(),
};
