import { useQuery } from "@tanstack/react-query";

import { dashboardKeys } from "@/queries/dashboardKeys";
import { deliveryKeys } from "@/queries/deliveryKeys";
import { pipelineKeys } from "@/queries/pipelineKeys";
import { dashboardService } from "@/services/dashboardService";
import { deliveryService } from "@/services/deliveryService";
import { pipelineService } from "@/services/pipelineService";

import type {
  ForecastResponse,
  UserEngagementTrendResponse,
} from "@/services/dashboardService";
import type { DeliveryLogsPage } from "@/types/delivery";
import type { OpsSummary } from "@/types/ops";
import type { PipelineRunsPage } from "@/types/pipeline";

export interface UseOpsMetricsDataResult {
  forecast: ForecastResponse | undefined;
  pipelineSummary: PipelineRunsPage | undefined;
  deliverySummary: DeliveryLogsPage | undefined;
  engagement: UserEngagementTrendResponse | undefined;
  opsSummary: OpsSummary | undefined;
  isLoading: boolean;
  error: Error | null;
}

/**
 * Tier 3 — 운영 지표 데이터를 집계해 반환한다.
 *
 * 오늘 발송 예측(forecast), 24시간 파이프라인/발송 요약, 사용자 참여도 추세를
 * 각각 독립적인 쿼리로 조회한다.
 *
 * 비용 개요는 costService.getOverview 가 (from, to) 날짜 파라미터를 필수로 요구하므로
 * 이 훅에서 직접 다루지 않고, 날짜 계산이 필요한 상위 컴포넌트에 위임한다.
 */
export function useOpsMetricsData(): UseOpsMetricsDataResult {
  // 오늘 스케줄 예측을 조회한다 (변화가 적어 5분 stale).
  const forecast = useQuery({
    queryKey: dashboardKeys.forecast(),
    queryFn: () => dashboardService.getForecast(),
    staleTime: 300_000,
  });

  // 24시간 이내 파이프라인 실행 목록을 최대 100건 조회해 통계 기반을 제공한다.
  const pipelineSummary = useQuery({
    queryKey: [...pipelineKeys.all, "summary24h"] as const,
    queryFn: () =>
      pipelineService.listRuns(new URLSearchParams({ size: "100" }), "1d"),
    staleTime: 30_000,
  });

  // 24시간 이내 발송 로그를 최대 100건 조회해 통계 기반을 제공한다.
  const deliverySummary = useQuery({
    queryKey: [...deliveryKeys.all, "summary24h"] as const,
    queryFn: () =>
      deliveryService.listLogs(new URLSearchParams({ size: "100" }), "1d"),
    staleTime: 30_000,
  });

  // 사용자 참여도 추세(클릭률/피드백)를 조회한다 (5분 stale).
  const engagement = useQuery({
    queryKey: dashboardKeys.userEngagementTrend(),
    queryFn: () => dashboardService.getUserEngagementTrend(),
    staleTime: 300_000,
  });

  // 운영 요약(오늘 KST 기준 서버 pre-aggregated 카운트)을 조회한다.
  // status 카운트(성공/실패)의 정확한 소스이며, 목록 100건 상한의 샘플링 문제가 없다.
  const opsSummary = useQuery({
    queryKey: dashboardKeys.opsSummary(),
    queryFn: () => dashboardService.getOpsSummary(),
    staleTime: 30_000,
  });

  return {
    forecast: forecast.data,
    pipelineSummary: pipelineSummary.data,
    deliverySummary: deliverySummary.data,
    engagement: engagement.data,
    opsSummary: opsSummary.data,
    isLoading:
      forecast.isLoading ||
      pipelineSummary.isLoading ||
      deliverySummary.isLoading ||
      engagement.isLoading ||
      opsSummary.isLoading,
    error:
      (forecast.error ||
        pipelineSummary.error ||
        deliverySummary.error ||
        engagement.error ||
        opsSummary.error) as Error | null,
  };
}
