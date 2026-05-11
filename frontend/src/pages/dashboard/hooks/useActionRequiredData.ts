import { useQuery } from "@tanstack/react-query";

import { costKeys } from "@/queries/costKeys";
import { deliveryKeys } from "@/queries/deliveryKeys";
import { pipelineKeys } from "@/queries/pipelineKeys";
import { costService } from "@/services/costService";
import { deliveryService } from "@/services/deliveryService";
import { pipelineService } from "@/services/pipelineService";

import {
  classifyActionRequired,
  type ActionRequiredItem,
} from "../model/dashboardState";

export interface UseActionRequiredDataResult {
  items: ActionRequiredItem[];
  isLoading: boolean;
  error: Error | null;
  refetch: () => void;
}

/**
 * Tier 1 — 즉각 조치가 필요한 항목을 집계해 반환한다.
 *
 * 24시간 내 발송 실패 수, 파이프라인 실패 수, 예산 경보 레벨을
 * classifyActionRequired 로 분류해 ActionRequiredItem 배열로 반환한다.
 */
export function useActionRequiredData(): UseActionRequiredDataResult {
  // 24시간 이내 발송 실패 건수를 totalCount 로 파악한다. size=1 로 최소 페이로드 요청.
  const deliveryFailures = useQuery({
    queryKey: [...deliveryKeys.all, "failuresWithin1d"] as const,
    queryFn: () =>
      deliveryService.listLogs(
        new URLSearchParams({ status: "FAILED", size: "1" }),
        "1d",
      ),
    staleTime: 30_000,
  });

  // 24시간 이내 파이프라인 실패 건수를 totalCount 로 파악한다.
  const pipelineFailures = useQuery({
    queryKey: [...pipelineKeys.all, "failuresWithin1d"] as const,
    queryFn: () =>
      pipelineService.listRuns(
        new URLSearchParams({ status: "FAILED", size: "1" }),
        "1d",
      ),
    staleTime: 30_000,
  });

  // 현재 예산 경보 레벨을 조회한다.
  const budgetAlert = useQuery({
    queryKey: costKeys.alertsCurrent(),
    queryFn: () => costService.getCurrentBudgetAlert(),
    staleTime: 30_000,
  });

  const items = classifyActionRequired({
    deliveryFailures: deliveryFailures.data?.totalCount ?? 0,
    pipelineFailures: pipelineFailures.data?.totalCount ?? 0,
    budgetLevel: budgetAlert.data?.currentLevel ?? null,
  });

  return {
    items,
    isLoading:
      deliveryFailures.isLoading ||
      pipelineFailures.isLoading ||
      budgetAlert.isLoading,
    error:
      (deliveryFailures.error as Error | null) ||
      (pipelineFailures.error as Error | null) ||
      (budgetAlert.error as Error | null),
    refetch: () => {
      void deliveryFailures.refetch();
      void pipelineFailures.refetch();
      void budgetAlert.refetch();
    },
  };
}
