/**
 * 리뷰 정책 대시보드 쿼리 키 팩토리.
 *
 * 계층 구조:
 *  - `reviewPolicyKeys.all` — 전체 도메인 무효화 시
 *  - `reviewPolicyKeys.status()` — 카테고리별 정책 현황
 *  - `reviewPolicyKeys.distribution(categoryId, days)` — 점수 분포 (필터별)
 */
export const reviewPolicyKeys = {
  all: ["reviewPolicy"] as const,
  status: () => [...reviewPolicyKeys.all, "status"] as const,
  distribution: (categoryId: string | null, days: number) =>
    [...reviewPolicyKeys.all, "distribution", categoryId, days] as const,
};
