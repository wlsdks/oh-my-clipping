import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import React from "react";
import { describe, expect, test } from "vitest";

import { reviewKeys } from "@/queries/reviewKeys";
import { userKeys } from "@/queries/userKeys";

import { usePendingTasksData } from "../usePendingTasksData";

function makeWrapper(queryClient: QueryClient) {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

const YESTERDAY_ISO = new Date(Date.now() - 86_400_000).toISOString();
const TWO_DAYS_AGO_ISO = new Date(Date.now() - 2 * 86_400_000).toISOString();

describe("usePendingTasksData", () => {
  test("세 큐 모두 정상 데이터일 때 건수와 urgencyPreview를 반환한다", async () => {
    const queryClient = makeQueryClient();

    queryClient.setQueryData(userKeys.accounts({ status: "PENDING" }), [
      { id: "a1", createdAt: TWO_DAYS_AGO_ISO },
      { id: "a2", createdAt: YESTERDAY_ISO },
    ]);
    queryClient.setQueryData(userKeys.requests({ status: "PENDING" }), [
      { id: "r1", createdAt: YESTERDAY_ISO },
    ]);
    queryClient.setQueryData(reviewKeys.queue({ status: "REVIEW" }), []);

    const { result } = renderHook(() => usePendingTasksData(), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.userAccounts.count).toBe(2);
    // TWO_DAYS_AGO 가 가장 오래된 → "가장 오래된 2일 전"
    expect(result.current.userAccounts.urgencyPreview).toMatch(/가장 오래된 \d+일 전/);

    expect(result.current.clippingRequests.count).toBe(1);
    expect(result.current.clippingRequests.urgencyPreview).not.toBe("");

    expect(result.current.reviewItems.count).toBe(0);
    expect(result.current.reviewItems.urgencyPreview).toBe("");
  });

  test("모든 큐가 비면 건수 0, urgencyPreview 빈 문자열을 반환한다", async () => {
    const queryClient = makeQueryClient();

    queryClient.setQueryData(userKeys.accounts({ status: "PENDING" }), []);
    queryClient.setQueryData(userKeys.requests({ status: "PENDING" }), []);
    queryClient.setQueryData(reviewKeys.queue({ status: "REVIEW" }), []);

    const { result } = renderHook(() => usePendingTasksData(), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.userAccounts.count).toBe(0);
    expect(result.current.userAccounts.urgencyPreview).toBe("");
    expect(result.current.clippingRequests.urgencyPreview).toBe("");
    expect(result.current.reviewItems.urgencyPreview).toBe("");
    expect(result.current.error).toBeNull();
  });

  test("데이터 미설정 시 isLoading true를 유지한다", () => {
    const queryClient = makeQueryClient();
    // 데이터를 캐시에 설정하지 않음 → 실제 fetch 시도 → loading=true

    const { result } = renderHook(() => usePendingTasksData(), {
      wrapper: makeWrapper(queryClient),
    });

    expect(result.current.isLoading).toBe(true);
  });
});
