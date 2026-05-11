import { api } from "@/lib/kyInstance";
import type {
  ReviewPolicyStatusResponse,
  ScoreDistribution,
} from "@/types/reviewPolicy";

/**
 * 관리자 검토 정책 대시보드 API 서비스.
 *
 * 엔드포인트:
 *  - `GET /api/admin/review-items/policy-status` — 카테고리별 정책/지표 집계
 *  - `GET /api/admin/review-items/score-distribution` — importance 10버킷 히스토그램
 */
export const reviewPolicyService = {
  /** 카테고리별 리뷰 정책 현황 조회. */
  getPolicyStatus: (): Promise<ReviewPolicyStatusResponse> =>
    api.get("admin/review-items/policy-status").json(),

  /**
   * importance_score 점수 분포 조회.
   *
   * @param params.categoryId 특정 카테고리 필터 (미지정 시 전체)
   * @param params.days 집계 기간(일). 서버에서 1~90 으로 clamp. 미지정 시 서버 기본값(7)
   */
  getScoreDistribution: (params?: {
    categoryId?: string;
    days?: number;
  }): Promise<ScoreDistribution> => {
    // searchParams 에 undefined 를 넣지 않도록 존재하는 키만 선택적으로 담는다.
    const search: Record<string, string> = {};
    if (params?.categoryId) search.categoryId = params.categoryId;
    if (params?.days !== undefined) search.days = String(params.days);
    return api
      .get("admin/review-items/score-distribution", {
        searchParams: Object.keys(search).length > 0 ? search : undefined,
      })
      .json();
  },
};
