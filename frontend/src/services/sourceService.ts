import { api } from "@/lib/kyInstance";
import type { Source, SourceAnalyticsResponse, SourceCompliance, SourcePage } from "@/types/source";
import type { SourceCreateRequest, SourceApproveRequest } from "@/types/adminDto";

export type { SourceCreateRequest, SourceApproveRequest } from "@/types/adminDto";

export interface SourceUpdateRequest {
  name?: string;
  url?: string;
  sourceRegion?: "GLOBAL" | "DOMESTIC" | "UNKNOWN" | null;
  emoji?: string | null;
  isActive?: boolean;
  categoryId?: string;
  legalBasis?: string | null;
  summaryAllowed?: boolean;
  reviewNotes?: string | null;
  expectedUpdatedAt?: string | null;
}

export interface BulkActionResponse {
  successCount: number;
  failCount: number;
  results: Record<string, string>;
}

export interface SourceComplianceUpdateRequest {
  legalBasis?: string | null;
  summaryAllowed?: boolean;
  fulltextAllowed?: boolean;
  reviewNotes?: string | null;
  expectedUpdatedAt?: string | null;
}

export const sourceService = {
  /** 소스를 페이지네이션으로 조회한다. */
  getPage: (params?: URLSearchParams): Promise<SourcePage> => {
    const suffix = params?.toString() ? `?${params.toString()}` : "";
    return api.get(`admin/sources${suffix}`).json();
  },

  /** 전체 소스를 조회한다 (내부 용도). */
  getAll: (categoryId?: string): Promise<Source[]> => {
    const query = categoryId
      ? `?categoryId=${encodeURIComponent(categoryId)}&size=500`
      : "?size=500";
    return api
      .get(`admin/sources${query}`)
      .json<SourcePage>()
      .then((page) => page.content);
  },

  create: (data: SourceCreateRequest): Promise<Source> => api.post("admin/sources", { json: data }).json(),

  /** 단건 조회 — ChangeDetectionStrip 이 `updatedAt` 변경을 감지하기 위해 사용한다. */
  getById: (id: string): Promise<Source> =>
    api.get(`admin/sources/${encodeURIComponent(id)}`).json(),

  update: (id: string, data: SourceUpdateRequest): Promise<Source> =>
    api.put(`admin/sources/${encodeURIComponent(id)}`, { json: data }).json(),

  delete: (id: string): Promise<void> => api.delete(`admin/sources/${encodeURIComponent(id)}`).then(() => undefined),

  validateUrl: (url: string): Promise<{ valid: boolean; status?: string; reason: string }> =>
    api.post("admin/sources/validate-url", { json: { url } }).json(),

  verify: (id: string): Promise<{ status: string }> =>
    api.post(`admin/sources/${encodeURIComponent(id)}/verify`, { json: {} }).json(),

  approve: (id: string, data: SourceApproveRequest): Promise<Source> =>
    api.post(`admin/sources/${encodeURIComponent(id)}/approve`, { json: data }).json(),

  bulkVerify: (ids: string[]): Promise<BulkActionResponse> =>
    api.post("admin/sources/bulk/verify", { json: { ids } }).json(),

  bulkArchive: (ids: string[]): Promise<BulkActionResponse> =>
    api.post("admin/sources/bulk/archive", { json: { ids } }).json(),

  discoverSource: (
    query: string
  ): Promise<{
    knownMatch: { name: string; rssUrl: string; region: string } | null;
    discoveredFeeds: { url: string; title: string }[];
  }> => api.post("admin/sources/discover", { json: { query } }).json(),

  getAnalytics: (id: string, days?: number): Promise<SourceAnalyticsResponse> =>
    api.get(`admin/sources/${encodeURIComponent(id)}/analytics${days ? `?days=${days}` : ""}`).json(),

  getArticleCounts: (days?: number): Promise<{ counts: Record<string, number>; days: number }> =>
    api.get(`admin/sources/stats/article-counts${days ? `?days=${days}` : ""}`).json(),

  getCompliance: (id: string): Promise<SourceCompliance> =>
    api.get(`admin/source-compliance/${encodeURIComponent(id)}`).json(),

  updateCompliance: (id: string, data: SourceComplianceUpdateRequest): Promise<SourceCompliance> =>
    api.put(`admin/source-compliance/${encodeURIComponent(id)}`, { json: data }).json(),

  getCoverageGaps: (): Promise<CoverageGapsResponse> =>
    api.get("admin/sources/coverage-gaps").json(),

  getCrawlHistory: (id: string, days?: number): Promise<CrawlHistoryResponse> =>
    api.get(`admin/sources/${encodeURIComponent(id)}/crawl-history${days ? `?days=${days}` : ""}`).json(),

  getAiCosts: (days?: number): Promise<SourceAiCostsResponse> =>
    api.get(`admin/sources/stats/ai-costs${days ? `?days=${days}` : ""}`).json(),

  /** 재검토가 필요한 소스(만료 + 만료 임박 + 미검토) 건수를 반환한다. */
  getComplianceSummary: (): Promise<ComplianceSummaryResponse> =>
    api.get("admin/sources/compliance-summary").json(),
};

/** 저작권 검토 요약 — 사이드바 뱃지용 */
export interface ComplianceSummaryResponse {
  attentionCount: number;
}

export interface CoverageGap {
  categoryId: string;
  categoryName: string;
  type: string;
  detail: string;
  severity: string;
}

export interface CoverageGapsResponse {
  gaps: CoverageGap[];
}

export interface CrawlLogEntry {
  crawledAt: string;
  success: boolean;
  articlesFound: number;
  responseTimeMs: number | null;
  errorMessage?: string | null;
}

export interface CrawlHistoryResponse {
  sourceId: string;
  uptimePercent: number | null;
  avgResponseTimeMs: number | null;
  totalCrawls: number;
  successCount: number;
  failCount: number;
  logs: CrawlLogEntry[];
}

export interface SourceAiCostEntry {
  requestCount: number;
  tokensIn: number;
  tokensOut: number;
  estimatedUsd: number;
}

export interface SourceAiCostsResponse {
  costs: Record<string, SourceAiCostEntry>;
  days: number;
}
