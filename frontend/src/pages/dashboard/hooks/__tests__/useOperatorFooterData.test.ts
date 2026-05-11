import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import React from "react";
import { describe, expect, test } from "vitest";

import { categoryKeys } from "@/queries/categoryKeys";
import { dashboardKeys } from "@/queries/dashboardKeys";
import { personaKeys } from "@/queries/personaKeys";
import { runtimeKeys } from "@/queries/runtimeKeys";

import { useOperatorFooterData } from "../useOperatorFooterData";

function makeWrapper(queryClient: QueryClient) {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

const MOCK_ACTIVE_SUBS = {
  activeCount: 12,
  newThisWeek: 2,
  deactivatedThisWeek: 0,
  netChange: 2,
};

function seedSetupComplete(queryClient: QueryClient) {
  queryClient.setQueryData(categoryKeys.lists(), [
    { id: "c1", slackChannelId: "C123456" },
  ]);
  queryClient.setQueryData(dashboardKeys.stats(), [{ id: "s1" }]);
  queryClient.setQueryData(personaKeys.lists(), [{ id: "p1" }]);
  queryClient.setQueryData(runtimeKeys.configs(), { someKey: "someValue" });
}

function seedSetupIncomplete(queryClient: QueryClient) {
  queryClient.setQueryData(categoryKeys.lists(), []);
  queryClient.setQueryData(dashboardKeys.stats(), []);
  queryClient.setQueryData(personaKeys.lists(), []);
  queryClient.setQueryData(runtimeKeys.configs(), null);
}

describe("useOperatorFooterData", () => {
  test("세팅 완료 상태이면 showGettingStarted가 false를 반환한다", async () => {
    const queryClient = makeQueryClient();
    queryClient.setQueryData(dashboardKeys.activeSubscriptionsSummary(), MOCK_ACTIVE_SUBS);
    seedSetupComplete(queryClient);

    const { result } = renderHook(() => useOperatorFooterData(), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.showGettingStarted).toBe(false);
    expect(result.current.activeSubscriptions).toEqual(MOCK_ACTIVE_SUBS);
    expect(result.current.error).toBeNull();
  });

  test("세팅 미완료 상태이면 showGettingStarted가 true를 반환한다", async () => {
    const queryClient = makeQueryClient();
    queryClient.setQueryData(dashboardKeys.activeSubscriptionsSummary(), MOCK_ACTIVE_SUBS);
    seedSetupIncomplete(queryClient);

    const { result } = renderHook(() => useOperatorFooterData(), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.showGettingStarted).toBe(true);
  });

  test("카테고리가 있어도 slackChannelId 없으면 showGettingStarted가 true를 반환한다", async () => {
    const queryClient = makeQueryClient();
    queryClient.setQueryData(dashboardKeys.activeSubscriptionsSummary(), MOCK_ACTIVE_SUBS);
    // slackChannelId 없는 카테고리 → isSetupComplete → false
    queryClient.setQueryData(categoryKeys.lists(), [{ id: "c1", slackChannelId: null }]);
    queryClient.setQueryData(dashboardKeys.stats(), [{ id: "s1" }]);
    queryClient.setQueryData(personaKeys.lists(), [{ id: "p1" }]);
    queryClient.setQueryData(runtimeKeys.configs(), { someKey: "v" });

    const { result } = renderHook(() => useOperatorFooterData(), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.showGettingStarted).toBe(true);
  });

  test("activeSubscriptions 미조회 시 undefined를 반환한다", async () => {
    const queryClient = makeQueryClient();
    // activeSubscriptions 미설정
    seedSetupComplete(queryClient);

    const { result } = renderHook(() => useOperatorFooterData(), {
      wrapper: makeWrapper(queryClient),
    });

    // loading 중에도 activeSubscriptions는 undefined
    expect(result.current.activeSubscriptions).toBeUndefined();
  });
});
