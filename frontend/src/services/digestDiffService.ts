import { api } from "@/lib/kyInstance";

/** `digest_diff_log` 단일 행의 도메인 표현. */
export interface DigestDiffEntry {
  id: string;
  categoryId: string;
  digestDate: string;       // ISO date (yyyy-MM-dd)
  legacySummary: string | null;
  newSummary: string | null;
  newMode: string | null;
  sectionsCount: number;
  articlesCount: number;
  crossMatchCount: number;
  createdAt: string;         // ISO datetime
}

/** GET /api/admin/digest-diff 페이지 응답. */
export interface DigestDiffListResponse {
  content: DigestDiffEntry[];
  totalElements: number;
  page: number;
  size: number;
}

/** `digestDiffService.list` 호출 파라미터. categoryId 는 필수. */
export interface DigestDiffListFilter {
  categoryId: string;
  from?: string;   // yyyy-MM-dd
  to?: string;     // yyyy-MM-dd
  page?: number;
  size?: number;
}

export const digestDiffService = {
  /**
   * Shadow mode diff 기록 목록을 페이지네이션으로 조회한다.
   *
   * categoryId 는 필수. from/to 미지정 시 백엔드가 최근 30일로 기본 적용.
   */
  list: (filter: DigestDiffListFilter): Promise<DigestDiffListResponse> => {
    const params = new URLSearchParams();
    params.set("categoryId", filter.categoryId);
    if (filter.from) params.set("from", filter.from);
    if (filter.to) params.set("to", filter.to);
    if (filter.page !== undefined) params.set("page", String(filter.page));
    if (filter.size !== undefined) params.set("size", String(filter.size));
    return api.get(`admin/digest-diff?${params.toString()}`).json<DigestDiffListResponse>();
  },
};
