async function get<T>(path: string): Promise<T> {
  const r = await fetch(path, { credentials: "include" });
  if (r.status === 401 || r.status === 403) {
    window.location.href = "/admin/login?status=expired";
    throw new Error("Unauthorized");
  }
  if (!r.ok) throw new Error(`API ${r.status}`);
  return r.json();
}

/* -- 응답 타입 -- */

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

export interface CategoryStatItem {
  categoryId: string;
  categoryName: string;
  clicks: number;
  impressions: number;
  ctr: number;
  sharePercent: number;
}

/* -- API 클라이언트 -- */

export const analyticsApi = {
  getDau: (days = 7) => get<{ data: DauRow[] }>(`/api/admin/analytics/dau?days=${days}`),

  getWizardFunnel: (days = 30) => get<{ data: WizardFunnelRow[] }>(`/api/admin/analytics/wizard-funnel?days=${days}`),

  /* -- from/to 기반 메서드 (기간 범위 지원) -- */

  getDauByRange: (from: string, to: string) =>
    get<{ data: DauRow[] }>(`/api/admin/analytics/dau?from=${from}&to=${to}`),

  getArticleRanking: (from: string, to: string, sort = "clicks", limit = 20) =>
    get<{ data: ArticleRankItem[] }>(
      `/api/admin/analytics/article-ranking?from=${from}&to=${to}&sort=${sort}&limit=${limit}`
    ),

  getCategoryStats: (from: string, to: string) =>
    get<{ data: CategoryStatItem[] }>(`/api/admin/analytics/category-stats?from=${from}&to=${to}`)
};
