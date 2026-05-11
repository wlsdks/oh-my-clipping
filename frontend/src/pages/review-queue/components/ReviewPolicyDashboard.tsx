import { useQuery, useQueryClient } from "@tanstack/react-query";
import { reviewPolicyService } from "@/services/reviewPolicyService";
import { reviewPolicyKeys } from "@/queries/reviewPolicyKeys";
import { ruleService } from "@/services/ruleService";
import { PolicyStatusGrid } from "./PolicyStatusGrid";
import { EmptyThresholdBanner } from "./EmptyThresholdBanner";
import { ScoreDistributionChart } from "./ScoreDistributionChart";

interface ReviewPolicyDashboardProps {
  onCategoryClick: (categoryId: string) => void;
}

// 점수 분포 기본 집계 기간(일)
const DEFAULT_DISTRIBUTION_DAYS = 7;

/**
 * 리뷰 정책 대시보드 컨테이너.
 *
 * - `GET /policy-status`, `GET /score-distribution` 두 쿼리를 병렬로 로드한다.
 * - 로딩/에러 상태를 상위에서 한 번에 처리하고, 성공 시 하위 컴포넌트로 데이터 분배.
 * - 카테고리 카드 클릭 → 상위(`ReviewQueuePage`)의 카테고리 필터와 연결(`onCategoryClick`).
 * - 카드 내부 `ThresholdInlineEditor` 저장 → `PUT /api/admin/category-rules/{id}` 호출 후
 *   정책 상태 쿼리를 무효화해 전체 그리드와 배너가 새 값을 반영한다.
 */
export function ReviewPolicyDashboard({ onCategoryClick }: ReviewPolicyDashboardProps) {
  const queryClient = useQueryClient();

  // 정책 현황: 로딩/에러를 이 쿼리 기준으로 판단한다 (핵심 데이터)
  const {
    data: status,
    isLoading: statusLoading,
    isError: statusError,
  } = useQuery({
    queryKey: reviewPolicyKeys.status(),
    queryFn: () => reviewPolicyService.getPolicyStatus(),
    staleTime: 30_000,
  });

  // 점수 분포: 보조 데이터. 실패해도 대시보드 전체를 막지 않는다.
  const { data: distribution } = useQuery({
    queryKey: reviewPolicyKeys.distribution(null, DEFAULT_DISTRIBUTION_DAYS),
    queryFn: () => reviewPolicyService.getScoreDistribution({ days: DEFAULT_DISTRIBUTION_DAYS }),
    staleTime: 30_000,
  });

  // 인라인 editor 가 호출한다. null 이면 clearAutoApproveThreshold=true 로 해제, 숫자면 값 저장.
  // 저장 성공/실패 토스트는 editor 자체에서 처리한다 — 이 함수는 API 호출 결과만 resolve/reject 한다.
  const handleThresholdChange = async (categoryId: string, value: number | null) => {
    if (value === null) {
      await ruleService.updateCategoryRule(categoryId, { clearAutoApproveThreshold: true });
    } else {
      await ruleService.updateCategoryRule(categoryId, { autoApproveThreshold: value });
    }
    // 성공 시 policy-status 쿼리 무효화 → 그리드/배너/차트가 새 값으로 리렌더된다.
    await queryClient.invalidateQueries({ queryKey: reviewPolicyKeys.status() });
  };

  if (statusLoading) {
    return (
      <section
        className="mb-6"
        data-testid="review-policy-dashboard"
        aria-busy="true"
        aria-live="polite"
      >
        <p className="text-sm text-muted-foreground">정책 현황을 불러오는 중...</p>
      </section>
    );
  }

  if (statusError || !status) {
    return (
      <section className="mb-6" data-testid="review-policy-dashboard" role="alert">
        <p className="text-sm text-destructive">정책 현황을 불러오지 못했어요</p>
      </section>
    );
  }

  return (
    <section className="space-y-4 mb-6" data-testid="review-policy-dashboard">
      <EmptyThresholdBanner categories={status.categories} />
      <PolicyStatusGrid
        categories={status.categories}
        onCategoryClick={onCategoryClick}
        onThresholdChange={handleThresholdChange}
      />
      {distribution && (
        <ScoreDistributionChart distribution={distribution} threshold={null} />
      )}
    </section>
  );
}
