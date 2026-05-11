import { api } from "@/lib/kyInstance";
import type {
  ReviewQueueItem,
  ReviewSummaryResponse,
  ReviewStatsResponse,
  BulkRevertItem,
  BulkActionResponse,
} from "@/types/review";

export interface ReviewItemActionRequest {
  reason?: string | null;
  reviewedBy?: string | null;
}

export const reviewService = {
  listItems: (params?: {
    categoryId?: string;
    status?: string;
    limit?: number;
    /**
     * 전체 조회 시 카테고리별 top-N 샘플링.
     * 각 카테고리에서 우선순위 기준 최대 N개씩 뽑아 limit 범위 내 합집합을 반환한다.
     * categoryId가 지정되면 서버에서 무시된다.
     */
    perCategory?: number;
  }): Promise<ReviewQueueItem[]> => {
    const query = new URLSearchParams();
    if (params?.categoryId) query.set("categoryId", params.categoryId);
    if (params?.status) query.set("status", params.status);
    if (params?.limit != null) query.set("limit", String(params.limit));
    if (params?.perCategory != null) query.set("perCategory", String(params.perCategory));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`admin/review-items${suffix}`).json();
  },

  approve: (summaryId: string, data?: ReviewItemActionRequest): Promise<{ summaryId: string; status: string }> =>
    api.post(`admin/review-items/${encodeURIComponent(summaryId)}/approve`, { json: data ?? {} }).json(),

  exclude: (summaryId: string, data?: ReviewItemActionRequest): Promise<{ summaryId: string; status: string }> =>
    api.post(`admin/review-items/${encodeURIComponent(summaryId)}/exclude`, { json: data ?? {} }).json(),

  markForReview: (summaryId: string, data?: ReviewItemActionRequest): Promise<{ summaryId: string; status: string }> =>
    api.post(`admin/review-items/${encodeURIComponent(summaryId)}/review`, { json: data ?? {} }).json(),

  getSummary: (): Promise<ReviewSummaryResponse> =>
    api.get("admin/review-items/summary").json(),

  getStats: (period: string = "7d"): Promise<ReviewStatsResponse> =>
    api.get(`admin/review-items/stats`, { searchParams: { period } }).json(),

  bulkApprove: (ids: string[], reviewNote?: string): Promise<BulkActionResponse> =>
    api.post("admin/review-items/bulk-approve", { json: { ids, reviewNote } }).json(),

  bulkExclude: (ids: string[], reviewNote?: string): Promise<BulkActionResponse> =>
    api.post("admin/review-items/bulk-exclude", { json: { ids, reviewNote } }).json(),

  bulkRevert: (reverts: BulkRevertItem[]): Promise<BulkActionResponse> =>
    api.post("admin/review-items/bulk-revert", { json: { reverts } }).json(),
};
