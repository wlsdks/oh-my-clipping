import { api } from "@/lib/kyInstance";

/** 단일 RSS 소스에 대한 기업 매칭 후보. */
export interface BackfillCandidate {
  sourceId: string;
  sourceUrl: string;
  sourceName: string;
  categoryId: string;
  categoryName: string;
  matchedCompanyName: string;
  stockCode: string | null;
  confidence: "high" | "medium" | "low";
  precision: number;
}

/** GET /api/admin/organizations/backfill/preview 응답. */
export interface BackfillPreviewResponse {
  candidates: BackfillCandidate[];
  total: number;
  byConfidence: { high: number; medium: number; low: number };
}

/** POST /api/admin/organizations/backfill/apply 응답. */
export interface BackfillApplyResponse {
  total: number;
  succeeded: number;
  failed: number;
  errors: Array<{ candidateId: string; reason: string }>;
  affectedCategoryIds: string[];
}

/** preview 필터 파라미터. */
export interface BackfillPreviewFilter {
  confidence: string;
  includeMedium?: boolean;
  categoryId?: string;
}

export const backfillService = {
  /**
   * 기업 매칭 후보 목록을 미리보기한다.
   *
   * confidence, includeMedium, categoryId 파라미터로 필터링한다.
   */
  preview: (filter: BackfillPreviewFilter): Promise<BackfillPreviewResponse> => {
    const params = new URLSearchParams();
    params.set("confidence", filter.confidence);
    if (filter.includeMedium) params.set("includeMedium", "true");
    if (filter.categoryId && filter.categoryId.trim()) {
      params.set("categoryId", filter.categoryId.trim());
    }
    return api
      .get(`admin/organizations/backfill/preview?${params.toString()}`)
      .json<BackfillPreviewResponse>();
  },

  /**
   * 선택한 후보 ID 목록을 실제로 적용한다.
   *
   * 한 번에 최대 100개까지 적용 가능하다. 초과 시 백엔드에서 400 반환.
   */
  apply: (body: { candidateIds: string[] }): Promise<BackfillApplyResponse> =>
    api
      .post("admin/organizations/backfill/apply", { json: body })
      .json<BackfillApplyResponse>(),
};
