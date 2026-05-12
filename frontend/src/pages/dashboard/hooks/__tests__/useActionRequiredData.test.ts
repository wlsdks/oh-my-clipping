import { renderHook, waitFor } from "@testing-library/react";
import { describe, expect, test } from "vitest";

import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";
import { costKeys } from "@/queries/costKeys";
import { deliveryKeys } from "@/queries/deliveryKeys";
import { pipelineKeys } from "@/queries/pipelineKeys";

import { useActionRequiredData } from "../useActionRequiredData";

describe("useActionRequiredData", () => {
  test("발송+예산 실패가 있으면 두 개의 조치 항목을 반환한다", async () => {
    const queryClient = createTestQueryClient();

    queryClient.setQueryData([...deliveryKeys.all, "failuresWithin1d"], {
      content: [],
      totalCount: 3,
      page: 0,
      size: 1,
    });
    queryClient.setQueryData([...pipelineKeys.all, "failuresWithin1d"], {
      content: [],
      totalCount: 0,
      page: 0,
      size: 1,
    });
    queryClient.setQueryData(costKeys.alertsCurrent(), {
      monthId: "2026-04",
      currentLevel: "CRITICAL_90",
      usagePct: 92,
      remainingDays: 12,
    });

    const { result } = renderHook(() => useActionRequiredData(), {
      wrapper: createQueryClientWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.items).toHaveLength(2);
    expect(result.current.items[0].type).toBe("delivery_failed");
    expect(result.current.items[0].count).toBe(3);
    expect(result.current.items[1].type).toBe("budget_alert");
    expect(result.current.items[1].severity).toBe("warning");
    expect(result.current.error).toBeNull();
  });

  test("파이프라인 실패만 있으면 pipeline_failed 항목 하나만 반환한다", async () => {
    const queryClient = createTestQueryClient();

    queryClient.setQueryData([...deliveryKeys.all, "failuresWithin1d"], {
      content: [],
      totalCount: 0,
      page: 0,
      size: 1,
    });
    queryClient.setQueryData([...pipelineKeys.all, "failuresWithin1d"], {
      content: [],
      totalCount: 5,
      page: 0,
      size: 1,
    });
    queryClient.setQueryData(costKeys.alertsCurrent(), {
      monthId: "2026-04",
      currentLevel: null,
      usagePct: 50,
      remainingDays: 20,
    });

    const { result } = renderHook(() => useActionRequiredData(), {
      wrapper: createQueryClientWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].type).toBe("pipeline_failed");
    expect(result.current.items[0].severity).toBe("danger");
  });

  test("아무 문제 없으면 빈 배열을 반환한다", async () => {
    const queryClient = createTestQueryClient();

    queryClient.setQueryData([...deliveryKeys.all, "failuresWithin1d"], {
      content: [],
      totalCount: 0,
      page: 0,
      size: 1,
    });
    queryClient.setQueryData([...pipelineKeys.all, "failuresWithin1d"], {
      content: [],
      totalCount: 0,
      page: 0,
      size: 1,
    });
    queryClient.setQueryData(costKeys.alertsCurrent(), {
      monthId: "2026-04",
      currentLevel: null,
      usagePct: 30,
      remainingDays: 25,
    });

    const { result } = renderHook(() => useActionRequiredData(), {
      wrapper: createQueryClientWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.items).toHaveLength(0);
  });

  test("CRITICAL_100 예산 경보는 danger severity를 반환한다", async () => {
    const queryClient = createTestQueryClient();

    queryClient.setQueryData([...deliveryKeys.all, "failuresWithin1d"], {
      content: [],
      totalCount: 0,
      page: 0,
      size: 1,
    });
    queryClient.setQueryData([...pipelineKeys.all, "failuresWithin1d"], {
      content: [],
      totalCount: 0,
      page: 0,
      size: 1,
    });
    queryClient.setQueryData(costKeys.alertsCurrent(), {
      monthId: "2026-04",
      currentLevel: "CRITICAL_100",
      usagePct: 101,
      remainingDays: 0,
    });

    const { result } = renderHook(() => useActionRequiredData(), {
      wrapper: createQueryClientWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].type).toBe("budget_alert");
    expect(result.current.items[0].severity).toBe("danger");
    expect(result.current.items[0].budgetLevel).toBe("CRITICAL_100");
  });
});
