import { api } from "@/lib/kyInstance";
import type { AutoExcludedResponse, CategoryRule, RestoreFromAutoExcludeResult } from "@/types/autoExcludedItem";
import type { CategoryRuleDryRunRequest, RuleDryRunResult } from "@/types/categoryRule";

/**
 * PR-3-lite 운영 규칙 dry-run + 자동 제외 감사/복구 API.
 *
 * 기존 룰 CRUD(`ruleService`) 와 별도로 분리. dry-run/감사 뷰는 운영 대시보드 패널에서만 쓰이고
 * rule 편집 페이지의 핵심 도메인이 아니기 때문이다. 기존 서비스를 확장하면 쿼리 키 계층이 섞여
 * 무효화가 꼬이므로 독립 키 팩토리(`categoryRuleKeys`/`autoExcludedKeys`) 와 짝지어 쓴다.
 *
 * 엔드포인트:
 *  - `GET  /api/admin/category-rules/{categoryId}` — 단건 카테고리 룰 조회 (드로어 룰 근거)
 *  - `POST /api/admin/category-rules/{categoryId}/dry-run` — dry-run 시뮬레이션
 *  - `GET  /api/admin/review-items/auto-excluded` — 자동 제외 감사 뷰 (페이지 + 분포)
 *  - `POST /api/admin/review-items/{summaryId}/restore-to-review` — 자동 제외 항목을 REVIEW 로 복구
 */
export const categoryRuleService = {
  /**
   * 단건 카테고리 룰 조회. 자동 제외 드로어에서 룰 근거(차단 목록 / 필수 키워드) 렌더에 사용.
   * 서버: `GET /api/admin/category-rules/{categoryId}` — `CategoryRuleAdminController.kt`.
   *
   * NOTE: 기존 `ruleService.getCategoryRule` 과 동일 엔드포인트. 자동 제외 드로어는
   * 쿼리 키 네임스페이스를 `categoryRuleKeys.detail` 로 묶기 위해 이 서비스를 통해 호출한다.
   */
  getById: (categoryId: string): Promise<CategoryRule> =>
    api.get(`admin/category-rules/${encodeURIComponent(categoryId)}`).json(),

  /**
   * 룰 dry-run 시뮬레이션.
   *
   * 서버는 read-only 로 동작하고 어떤 감사/결정도 persist 하지 않는다.
   * `days`/`maxSamples` 를 생략해도 body 에는 `undefined` 로 남지만 ky 가 JSON 직렬화 시 해당 키를
   * 빠뜨리므로 서버의 기본값(30일/5샘플) 로직이 작동한다.
   */
  dryRun: (categoryId: string, request: CategoryRuleDryRunRequest): Promise<RuleDryRunResult> =>
    api
      .post(`admin/category-rules/${encodeURIComponent(categoryId)}/dry-run`, {
        json: request
      })
      .json(),

  /**
   * 자동 제외된 항목 감사 조회.
   *
   * 모든 파라미터가 optional. undefined 인 파라미터는 쿼리스트링에 포함하지 않아 서버 기본값을 타게 한다.
   * `categoryId` 빈 문자열도 쿼리 누락과 동등하게 처리(== 전체).
   */
  listAutoExcluded: (params: {
    categoryId?: string;
    reason?: string;
    days?: number;
    page?: number;
    size?: number;
  }): Promise<AutoExcludedResponse> => {
    // undefined/빈 문자열을 제외한 값만 searchParams 에 담는다 — 빈 키가 "?categoryId=" 로 붙지 않게.
    const search: Record<string, string> = {};
    if (params.categoryId) search.categoryId = params.categoryId;
    if (params.reason) search.reason = params.reason;
    if (params.days !== undefined) search.days = String(params.days);
    if (params.page !== undefined) search.page = String(params.page);
    if (params.size !== undefined) search.size = String(params.size);
    return api
      .get("admin/review-items/auto-excluded", {
        searchParams: Object.keys(search).length > 0 ? search : undefined
      })
      .json();
  },

  /**
   * 자동 제외된 항목을 REVIEW 로 복구.
   *
   * 보호 조건(서버에서 검증):
   *  - 존재하지 않는 summary → 404
   *  - `policy-auto` 가 아닌 항목 or EXCLUDE 가 아닌 상태 → 409
   * 성공 시 항상 `newStatus === "REVIEW"`.
   */
  restoreFromAutoExclude: (summaryId: string): Promise<RestoreFromAutoExcludeResult> =>
    api.post(`admin/review-items/${encodeURIComponent(summaryId)}/restore-to-review`).json()
};
