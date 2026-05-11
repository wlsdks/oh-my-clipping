import { api } from "@/lib/kyInstance";
import type { CategoryRule, RuleStatsResponse, ExcludedItemsResponse } from "@/types/category";

export interface CategoryRuleUpdateRequest {
  includeKeywords?: string[];
  excludeKeywords?: string[];
  riskTags?: string[];
  includeThreshold?: number;
  reviewThreshold?: number;
  uncertainToReview?: boolean;
  autoExcludeEnabled?: boolean;
  /** 자동 승인 임계값 (0~1). null/undefined 는 변경 없음, clearAutoApproveThreshold=true 로 해제. */
  autoApproveThreshold?: number | null;
  /** true 이면 autoApproveThreshold 를 null 로 해제한다. autoApproveThreshold 값보다 우선. */
  clearAutoApproveThreshold?: boolean;
  updatedBy?: string | null;
  /**
   * 낙관적 잠금용 updated_at. 서버가 보유한 값과 다르면 409 STALE_EDIT로 응답한다.
   * 미지정 시 경합 검증을 생략한다.
   */
  expectedUpdatedAt?: string | null;
}

export const ruleService = {
  getCategoryRule: (categoryId: string): Promise<CategoryRule> =>
    api.get(`admin/category-rules/${encodeURIComponent(categoryId)}`).json(),

  updateCategoryRule: (categoryId: string, data: CategoryRuleUpdateRequest): Promise<CategoryRule> =>
    api.put(`admin/category-rules/${encodeURIComponent(categoryId)}`, { json: data }).json(),

  getRuleStats: (days: number = 7): Promise<RuleStatsResponse> =>
    api.get("admin/category-rules/stats", { searchParams: { days } }).json(),

  getExcludedItems: (categoryId: string, limit: number = 5): Promise<ExcludedItemsResponse> =>
    api
      .get(`admin/category-rules/${encodeURIComponent(categoryId)}/excluded-items`, { searchParams: { limit } })
      .json()
};
