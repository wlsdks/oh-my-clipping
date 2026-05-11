import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import React from "react";
import { describe, expect, test } from "vitest";

import { dashboardKeys } from "@/queries/dashboardKeys";
import { deliveryKeys } from "@/queries/deliveryKeys";
import { pipelineKeys } from "@/queries/pipelineKeys";

import { useOpsMetricsData } from "../useOpsMetricsData";

function makeWrapper(queryClient: QueryClient) {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

const MOCK_FORECAST = {
  expectedRunCount: 5,
  expectedDigestCount: 3,
  nextRunAtKst: "2026-04-17T09:00:00",
};

const MOCK_ENGAGEMENT = {
  yesterdayClickRate: 0.12,
  sevenDayAvgClickRate: 0.1,
  sevenDayStdDev: 0.02,
  feedbackPositiveYesterday: 8,
  feedbackNegativeYesterday: 2,
};

const MOCK_PIPELINE_PAGE = {
  content: [{ id: "p1", status: "SUCCEEDED" }],
  totalCount: 1,
  page: 0,
  size: 100,
};

const MOCK_DELIVERY_PAGE = {
  content: [{ id: "d1", status: "SENT" }],
  totalCount: 1,
  page: 0,
  size: 100,
};

const MOCK_OPS_SUMMARY = {
  delivery: { total: 42, sent: 40, failed: 2 },
  pipeline: { total: 10, success: 9, failed: 1 },
};

describe("useOpsMetricsData", () => {
  test("모든 쿼리가 채워지면 각 데이터를 반환한다", async () => {
    const queryClient = makeQueryClient();

    queryClient.setQueryData(dashboardKeys.forecast(), MOCK_FORECAST);
    queryClient.setQueryData(
      [...pipelineKeys.all, "summary24h"],
      MOCK_PIPELINE_PAGE,
    );
    queryClient.setQueryData(
      [...deliveryKeys.all, "summary24h"],
      MOCK_DELIVERY_PAGE,
    );
    queryClient.setQueryData(
      dashboardKeys.userEngagementTrend(),
      MOCK_ENGAGEMENT,
    );
    queryClient.setQueryData(dashboardKeys.opsSummary(), MOCK_OPS_SUMMARY);

    const { result } = renderHook(() => useOpsMetricsData(), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.forecast).toEqual(MOCK_FORECAST);
    expect(result.current.pipelineSummary).toEqual(MOCK_PIPELINE_PAGE);
    expect(result.current.deliverySummary).toEqual(MOCK_DELIVERY_PAGE);
    expect(result.current.engagement).toEqual(MOCK_ENGAGEMENT);
    expect(result.current.opsSummary).toEqual(MOCK_OPS_SUMMARY);
    expect(result.current.error).toBeNull();
  });

  test("데이터가 없으면 각 필드가 undefined를 반환한다", async () => {
    const queryClient = makeQueryClient();

    // 캐시에 아무것도 없으면 isLoading=true 이므로
    // 빈 값으로 초기화해 pending 없이 테스트
    queryClient.setQueryData(dashboardKeys.forecast(), undefined);
    queryClient.setQueryData([...pipelineKeys.all, "summary24h"], undefined);
    queryClient.setQueryData([...deliveryKeys.all, "summary24h"], undefined);
    queryClient.setQueryData(dashboardKeys.userEngagementTrend(), undefined);
    queryClient.setQueryData(dashboardKeys.opsSummary(), undefined);

    const { result } = renderHook(() => useOpsMetricsData(), {
      wrapper: makeWrapper(queryClient),
    });

    // undefined 로 설정해도 로딩은 계속 될 수 있으므로 별도 확인
    expect(result.current.forecast).toBeUndefined();
    expect(result.current.pipelineSummary).toBeUndefined();
    expect(result.current.deliverySummary).toBeUndefined();
    expect(result.current.engagement).toBeUndefined();
    expect(result.current.opsSummary).toBeUndefined();
  });

  test("isLoading은 모든 쿼리가 완료될 때 false가 된다", async () => {
    const queryClient = makeQueryClient();

    queryClient.setQueryData(dashboardKeys.forecast(), MOCK_FORECAST);
    queryClient.setQueryData(
      [...pipelineKeys.all, "summary24h"],
      MOCK_PIPELINE_PAGE,
    );
    queryClient.setQueryData(
      [...deliveryKeys.all, "summary24h"],
      MOCK_DELIVERY_PAGE,
    );
    queryClient.setQueryData(
      dashboardKeys.userEngagementTrend(),
      MOCK_ENGAGEMENT,
    );
    queryClient.setQueryData(dashboardKeys.opsSummary(), MOCK_OPS_SUMMARY);

    const { result } = renderHook(() => useOpsMetricsData(), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
  });
});
