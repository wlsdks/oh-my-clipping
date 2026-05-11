import { describe, expect, it } from "vitest";

import type { SystemStatusResponse } from "@/types/systemStatus";
import {
  computeActionItems,
  computeSystemHealth,
  isSetupComplete,
  computeUrgencyPreview,
  classifyActionRequired,
  computeClickRateTrend,
  computeSparklineData,
} from "../dashboardState";

describe("computeActionItems", () => {
  const empty = {
    pendingAccounts: 0,
    pendingRequests: 0,
    pendingReviews: 0,
    failedDeliveries: 0,
    failedRuns: 0,
  };

  it("모든 카운트가 0이면 빈 배열을 반환한다", () => {
    expect(computeActionItems(empty)).toEqual([]);
  });

  it("count가 0인 항목은 제외한다", () => {
    const items = computeActionItems({ ...empty, pendingAccounts: 3 });
    expect(items).toHaveLength(1);
    expect(items[0].id).toBe("pending-accounts");
    expect(items[0].count).toBe(3);
  });

  it("danger 항목을 warning보다 앞에 정렬한다", () => {
    const items = computeActionItems({
      ...empty,
      pendingRequests: 5,
      pendingAccounts: 2,
    });
    expect(items[0].severity).toBe("danger");
    expect(items[1].severity).toBe("warning");
  });

  it("각 아이템은 링크 href를 포함한다", () => {
    const items = computeActionItems({ ...empty, failedRuns: 1 });
    expect(items[0].href).toBe("/admin/pipeline?status=FAILED");
  });
});

describe("computeSystemHealth", () => {
  const baseStatus: SystemStatusResponse = {
    server: { uptime: "", javaVersion: "", activeProfiles: [], memoryUsedMb: 0, memoryMaxMb: 0 },
    database: { connected: true, poolActive: 0, poolIdle: 0, poolTotal: 0 },
    slack: { botTokenConfigured: true, defaultChannelId: null, healthy: true, lastCheckTime: null },
    ai: { circuitBreakerState: "CLOSED", canCall: true, consecutiveOpenCount: 0, totalOpenCount: 0, lastOpenedAt: null },
    jobQueue: { pendingJobs: 0, threshold: 100 },
    schedulers: [],
  };

  it("status 가 undefined 이면 기본 정상으로 처리한다", () => {
    const health = computeSystemHealth(undefined);
    expect(health.ok).toBe(true);
    expect(health.message).toBe("모든 시스템 정상");
  });

  it("DB가 끊기면 이상으로 표시한다", () => {
    const health = computeSystemHealth({
      ...baseStatus,
      database: { connected: false, poolActive: 0, poolIdle: 0, poolTotal: 0 },
    });
    expect(health.ok).toBe(false);
    expect(health.dbOk).toBe(false);
    expect(health.message).toContain("DB 연결 끊김");
  });

  it("DB 와 Slack 둘 다 끊기면 두 항목을 모두 메시지에 포함한다", () => {
    const health = computeSystemHealth({
      ...baseStatus,
      database: { connected: false, poolActive: 0, poolIdle: 0, poolTotal: 0 },
      slack: { botTokenConfigured: false, defaultChannelId: null, healthy: false, lastCheckTime: null },
    });
    expect(health.ok).toBe(false);
    expect(health.message).toContain("DB 연결 끊김");
    expect(health.message).toContain("Slack 연결 끊김");
  });

  it("AI 자동 차단이 걸리면 이상으로 표시한다", () => {
    const health = computeSystemHealth({
      ...baseStatus,
      ai: { circuitBreakerState: "OPEN", canCall: false, consecutiveOpenCount: 1, totalOpenCount: 3, lastOpenedAt: "2026-04-10T08:00:00Z" },
    });
    expect(health.ok).toBe(false);
    expect(health.aiOk).toBe(false);
    expect(health.message).toContain("AI 요청 일시 차단됨");
  });

  it("작업 대기열이 임계값을 초과하면 이상으로 표시한다", () => {
    const health = computeSystemHealth({
      ...baseStatus,
      jobQueue: { pendingJobs: 150, threshold: 100 },
    });
    expect(health.ok).toBe(false);
    expect(health.jobQueueOk).toBe(false);
    expect(health.message).toContain("작업 대기열 처리 지연");
  });

  it("모든 시스템이 정상이면 ok=true를 반환한다", () => {
    const health = computeSystemHealth(baseStatus);
    expect(health.ok).toBe(true);
    expect(health.aiOk).toBe(true);
    expect(health.jobQueueOk).toBe(true);
  });
});

describe("computeUrgencyPreview", () => {
  it("빈 배열은 빈 문자열", () => {
    expect(computeUrgencyPreview([], new Date())).toBe("");
  });

  it("가장 오래된 3일 전", () => {
    const now = new Date("2026-04-18T12:00:00Z");
    const items = [
      { createdAt: "2026-04-15T12:00:00Z" },
      { createdAt: "2026-04-17T12:00:00Z" },
    ];
    expect(computeUrgencyPreview(items, now)).toBe("가장 오래된 3일 전");
  });

  it("1일 미만은 N시간 전", () => {
    const now = new Date("2026-04-18T12:00:00Z");
    const items = [{ createdAt: "2026-04-18T03:00:00Z" }];
    expect(computeUrgencyPreview(items, now)).toBe("가장 오래된 9시간 전");
  });
});

describe("classifyActionRequired", () => {
  it("0건 + 예산 정상이면 빈 배열", () => {
    expect(
      classifyActionRequired({ deliveryFailures: 0, pipelineFailures: 0, budgetLevel: null }),
    ).toEqual([]);
  });

  it("발송 실패 3건만 있으면 1개 항목", () => {
    const r = classifyActionRequired({ deliveryFailures: 3, pipelineFailures: 0, budgetLevel: null });
    expect(r).toHaveLength(1);
    expect(r[0]).toEqual({ type: "delivery_failed", severity: "danger", count: 3 });
  });

  it("CRITICAL_100 은 danger, CRITICAL_90 은 warning", () => {
    const r100 = classifyActionRequired({
      deliveryFailures: 0, pipelineFailures: 0, budgetLevel: "CRITICAL_100",
    });
    expect(r100[0].severity).toBe("danger");

    const r90 = classifyActionRequired({
      deliveryFailures: 0, pipelineFailures: 0, budgetLevel: "CRITICAL_90",
    });
    expect(r90[0].severity).toBe("warning");
  });

  it("3종 모두 있으면 3항목 반환", () => {
    const r = classifyActionRequired({
      deliveryFailures: 1, pipelineFailures: 2, budgetLevel: "CRITICAL_90",
    });
    expect(r).toHaveLength(3);
  });
});

describe("computeClickRateTrend", () => {
  it("±1σ 이내는 neutral", () => {
    // delta=1, σ=3 → z=0.33 → neutral
    expect(computeClickRateTrend(15, 14, 3).direction).toBe("neutral");
  });

  it("+1σ 초과 상승은 up", () => {
    // delta=6, σ=3 → z=2 → up
    expect(computeClickRateTrend(20, 14, 3).direction).toBe("up");
  });

  it("-1σ 초과 하락은 down", () => {
    // delta=-6, σ=3 → z=2 → down
    expect(computeClickRateTrend(8, 14, 3).direction).toBe("down");
  });

  it("deltaPct 는 yesterday - sevenDayAvg", () => {
    const r = computeClickRateTrend(20, 14, 3);
    expect(r.deltaPct).toBeCloseTo(6, 5);
  });

  it("stddev 0 이어도 분모 0 에러 없음", () => {
    expect(() => computeClickRateTrend(15, 14, 0)).not.toThrow();
  });
});

describe("computeSparklineData", () => {
  it("date YYYY-MM-DD → MM/DD 변환", () => {
    const input = [
      { date: "2026-04-12", totalCostUsd: 10.5 },
      { date: "2026-04-18", totalCostUsd: 15.2 },
    ];
    expect(computeSparklineData(input)).toEqual([
      { date: "04/12", value: 10.5 },
      { date: "04/18", value: 15.2 },
    ]);
  });

  it("빈 배열은 빈 배열", () => {
    expect(computeSparklineData([])).toEqual([]);
  });
});

describe("isSetupComplete", () => {
  it("카테고리가 없으면 false", () => {
    expect(
      isSetupComplete({ categories: [], settings: [1], personas: [1], runtime: null }),
    ).toBe(false);
  });

  it("카테고리에 채널이 없고 runtime만 있으면 false", () => {
    expect(
      isSetupComplete({
        categories: [{ slackChannelId: null }],
        settings: [1],
        personas: [1],
        runtime: {},
      }),
    ).toBe(false);
  });

  it("카테고리 중 하나가 slackChannelId 를 가지면 true", () => {
    expect(
      isSetupComplete({
        categories: [{ slackChannelId: "C456" }],
        settings: [1],
        personas: [1],
        runtime: null,
      }),
    ).toBe(true);
  });

  it("채널이 전혀 없으면 false", () => {
    expect(
      isSetupComplete({
        categories: [{ slackChannelId: null }],
        settings: [1],
        personas: [1],
        runtime: null,
      }),
    ).toBe(false);
  });
});
