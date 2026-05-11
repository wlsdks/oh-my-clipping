import { useQuery } from "@tanstack/react-query";

import { reviewKeys } from "@/queries/reviewKeys";
import { userKeys } from "@/queries/userKeys";
import { reviewService } from "@/services/reviewService";
import { userService } from "@/services/userService";

import { computeUrgencyPreview } from "../model/dashboardState";

export interface PendingTaskSummary {
  count: number;
  /** "가장 오래된 N일 전" 또는 ""  */
  urgencyPreview: string;
}

export interface UsePendingTasksDataResult {
  userAccounts: PendingTaskSummary;
  clippingRequests: PendingTaskSummary;
  reviewItems: PendingTaskSummary;
  isLoading: boolean;
  error: Error | null;
}

/**
 * Tier 2 — 처리 대기 중인 업무 3종의 건수와 긴급도 미리보기를 반환한다.
 *
 * 각 큐별로 배열을 직접 수신하고, computeUrgencyPreview 로
 * "가장 오래된 N일 전" 문자열을 계산해 제공한다.
 */
export function usePendingTasksData(): UsePendingTasksDataResult {
  const now = new Date();

  // PENDING 상태 사용자 계정 목록을 조회한다.
  const accounts = useQuery({
    queryKey: userKeys.accounts({ status: "PENDING" }),
    queryFn: () => userService.listAdminUserAccounts("PENDING"),
    staleTime: 30_000,
  });

  // PENDING 상태 클리핑 신청 목록을 조회한다.
  const requests = useQuery({
    queryKey: userKeys.requests({ status: "PENDING" }),
    queryFn: () => userService.listAdminClippingRequests("PENDING"),
    staleTime: 30_000,
  });

  // REVIEW 상태 뉴스 검토 항목 목록을 조회한다.
  const reviews = useQuery({
    queryKey: reviewKeys.queue({ status: "REVIEW" }),
    queryFn: () => reviewService.listItems({ status: "REVIEW" }),
    staleTime: 30_000,
  });

  return {
    userAccounts: {
      count: accounts.data?.length ?? 0,
      urgencyPreview: computeUrgencyPreview(accounts.data ?? [], now),
    },
    clippingRequests: {
      count: requests.data?.length ?? 0,
      urgencyPreview: computeUrgencyPreview(requests.data ?? [], now),
    },
    reviewItems: {
      count: reviews.data?.length ?? 0,
      urgencyPreview: computeUrgencyPreview(reviews.data ?? [], now),
    },
    isLoading: accounts.isLoading || requests.isLoading || reviews.isLoading,
    error:
      (accounts.error || requests.error || reviews.error) as Error | null,
  };
}
