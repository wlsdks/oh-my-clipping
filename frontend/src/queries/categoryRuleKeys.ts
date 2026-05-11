/**
 * PR-3-lite 운영 규칙 dry-run / 자동 제외 감사 쿼리 키 팩토리.
 *
 * 기존 `ruleService` 의 쿼리 키(있다면) 와 섞지 않고 별도 네임스페이스로 둔다.
 * dry-run 은 편집 중인 카테고리별 미리보기라서 변경 빈도가 높고, 감사 뷰는 필터 파라미터 조합으로
 * 캐시 엔트리가 많이 생기므로 각각의 라이프사이클을 독립 관리한다.
 */

/** 룰 dry-run 프리뷰 결과. */
export const categoryRuleKeys = {
  all: ["categoryRule"] as const,
  /** 특정 카테고리의 dry-run 결과. body(excludeEventTypes/days/maxSamples) 를 포함한 key 로 미리보기별 캐시. */
  dryRun: (categoryId: string, body?: object) => [...categoryRuleKeys.all, "dryRun", categoryId, body ?? null] as const,
  /** 단건 카테고리 룰 조회 (자동 제외 드로어의 룰 근거 렌더). */
  detail: (categoryId: string) => [...categoryRuleKeys.all, "detail", categoryId] as const
};

/** 자동 제외 감사 뷰 (리스트 + reason breakdown). */
export const autoExcludedKeys = {
  all: ["autoExcluded"] as const,
  /** 필터 파라미터 조합별 리스트 캐시. */
  list: (params: { categoryId?: string; reason?: string; days?: number; page?: number; size?: number }) =>
    [...autoExcludedKeys.all, "list", params] as const
};
