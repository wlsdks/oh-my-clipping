import { useQuery } from "@tanstack/react-query";

import { categoryKeys } from "@/queries/categoryKeys";
import { dashboardKeys } from "@/queries/dashboardKeys";
import { personaKeys } from "@/queries/personaKeys";
import { runtimeKeys } from "@/queries/runtimeKeys";
import { categoryService } from "@/services/categoryService";
import { dashboardService } from "@/services/dashboardService";
import { personaService } from "@/services/personaService";
import { runtimeService } from "@/services/runtimeService";

import type { ActiveSubscriptionsSummaryResponse } from "@/services/dashboardService";

import { isSetupComplete } from "../model/dashboardState";

export interface UseOperatorFooterDataResult {
  activeSubscriptions: ActiveSubscriptionsSummaryResponse | undefined;
  /** true 이면 시작하기 배너를 표시한다. */
  showGettingStarted: boolean;
  isLoading: boolean;
  error: Error | null;
}

/**
 * Tier 4 — 운영자 푸터 영역 데이터를 반환한다.
 *
 * 활성 구독 요약(activeSubscriptionsSummary)과
 * 초기 세팅 완료 여부(isSetupComplete)를 계산해 제공한다.
 *
 * isSetupComplete 는 categories / clipping-settings / personas / runtime
 * 4개 데이터 소스에 의존하므로 함께 조회한다.
 * 이 쿼리들은 다른 훅(useDashboardData)과 queryKey 가 동일하므로
 * 캐시에서 서빙될 가능성이 높아 네트워크 비용이 최소화된다.
 */
export function useOperatorFooterData(): UseOperatorFooterDataResult {
  // 활성 구독 요약 (주 1회 수준으로 변화 → 5분 stale).
  const activeSubs = useQuery({
    queryKey: dashboardKeys.activeSubscriptionsSummary(),
    queryFn: () => dashboardService.getActiveSubscriptionsSummary(),
    staleTime: 300_000,
  });

  // isSetupComplete 판정용: 카테고리 목록.
  const categories = useQuery({
    queryKey: categoryKeys.lists(),
    queryFn: () => categoryService.getAll(),
    staleTime: 300_000,
  });

  // isSetupComplete 판정용: 클리핑 설정 목록.
  const settings = useQuery({
    queryKey: dashboardKeys.stats(),
    queryFn: () => dashboardService.listClippingSettings(),
    staleTime: 300_000,
  });

  // isSetupComplete 판정용: 페르소나 목록.
  const personas = useQuery({
    queryKey: personaKeys.lists(),
    queryFn: () => personaService.getAll(),
    staleTime: 300_000,
  });

  // isSetupComplete 판정용: 런타임 설정.
  const runtime = useQuery({
    queryKey: runtimeKeys.configs(),
    queryFn: () => runtimeService.getSettings(),
    staleTime: 300_000,
  });

  // 세팅 완료 여부를 판정한다 — 미완료면 시작하기 배너를 표시한다.
  const setupDone = isSetupComplete({
    categories: categories.data ?? [],
    settings: settings.data ?? [],
    personas: personas.data ?? [],
    runtime: runtime.data ?? null,
  });

  return {
    activeSubscriptions: activeSubs.data,
    showGettingStarted: !setupDone,
    isLoading:
      activeSubs.isLoading ||
      categories.isLoading ||
      settings.isLoading ||
      personas.isLoading ||
      runtime.isLoading,
    error:
      (activeSubs.error ||
        categories.error ||
        settings.error ||
        personas.error ||
        runtime.error) as Error | null,
  };
}
