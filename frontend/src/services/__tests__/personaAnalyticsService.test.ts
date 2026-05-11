// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse } from "msw";

vi.mock("@/store/authStore", () => ({
  authStore: { getState: vi.fn(() => ({ logout: vi.fn() })) }
}));

vi.mock("@/lib/kyInstance", async () => {
  const ky = (await import("ky")).default;
  const { authStore } = await import("@/store/authStore");
  return {
    api: ky.create({
      prefixUrl: "http://localhost/api",
      credentials: "include",
      headers: { Accept: "application/json" },
      hooks: {
        afterResponse: [
          async (_req: unknown, _opts: unknown, res: Response) => {
            if (res.status === 401) authStore.getState().logout();
          }
        ]
      }
    })
  };
});

import { personaAnalyticsService } from "@/services/personaAnalyticsService";
import type {
  BackfillResult,
  LiveSnapshotResponse,
  PersonaBatchRunDto,
  WeeklyTrendsResponse,
} from "@/types/personaAnalytics";

const mockLiveSnapshot: LiveSnapshotResponse = {
  totals: {
    totalStyles: 18,
    presetCount: 12,
    customCount: 6,
    activeSubscriptions: 247,
    presetUsageRate: 0.78,
    customStyleRatio: 0.33,
    weekOverWeekDelta: null
  },
  presetPortfolio: [
    {
      personaId: "p1",
      personaName: "테크 에디터",
      activeSubs: 42,
      weekOverWeekDelta: null,
      engagementRate: null,
      status: "HEALTHY",
      lastDeliveredAt: null
    }
  ],
  customSummary: {
    totalCustomPersonas: 6,
    activeCustomSubscriptions: 12,
    newThisWeek: 0,
    recentPersonas: []
  },
  asOf: "2026-04-09T00:00:00Z"
};

const server = setupServer();

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const mockTrendsResponse: WeeklyTrendsResponse = {
  weeks: ["2026-W10", "2026-W11", "2026-W12"],
  series: [
    {
      personaId: "p1",
      personaName: "테크 에디터",
      isPreset: true,
      activeSubs: [10, 12, 15],
      engagedUsers: [5, 6, 8],
      deliveredCount: [20, 24, 30],
    }
  ]
};

const mockBatchRuns: PersonaBatchRunDto[] = [
  {
    id: "run-1",
    runId: "uuid-run-1",
    triggerType: "SCHEDULED",
    weekStart: "2026-04-07",
    startedAt: "2026-04-07T02:00:00Z",
    finishedAt: "2026-04-07T02:01:00Z",
    overallStatus: "SUCCESS",
    snapshotStatus: "SUCCESS",
    personasScanned: 18,
    errorMessage: null,
  }
];

const mockBackfillResult: BackfillResult = {
  weeksProcessed: 12,
  personasAggregated: 18,
  snapshotRowsCreated: 216,
  durationMs: 3500,
};

describe("personaAnalyticsService", () => {
  describe("getLive", () => {
    it("admin/analytics/personas/live 를 호출하고 LiveSnapshotResponse 를 반환한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/analytics/personas/live", () =>
          HttpResponse.json(mockLiveSnapshot)
        )
      );

      const result = await personaAnalyticsService.getLive();

      expect(result.totals.totalStyles).toBe(18);
      expect(result.totals.activeSubscriptions).toBe(247);
      expect(result.totals.presetUsageRate).toBeCloseTo(0.78);
      expect(result.presetPortfolio).toHaveLength(1);
      expect(result.presetPortfolio[0].personaName).toBe("테크 에디터");
      expect(result.presetPortfolio[0].status).toBe("HEALTHY");
      expect(result.customSummary.totalCustomPersonas).toBe(6);
    });

    it("Slice 1 응답의 시계열 필드는 null 로 들어온다", async () => {
      server.use(
        http.get("http://localhost/api/admin/analytics/personas/live", () =>
          HttpResponse.json(mockLiveSnapshot)
        )
      );

      const result = await personaAnalyticsService.getLive();

      expect(result.totals.weekOverWeekDelta).toBeNull();
      expect(result.presetPortfolio[0].weekOverWeekDelta).toBeNull();
      expect(result.presetPortfolio[0].engagementRate).toBeNull();
      expect(result.presetPortfolio[0].lastDeliveredAt).toBeNull();
    });
  });

  describe("getTrends", () => {
    it("admin/analytics/personas/trends 를 호출하고 WeeklyTrendsResponse 를 반환한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/analytics/personas/trends", () =>
          HttpResponse.json(mockTrendsResponse)
        )
      );

      const result = await personaAnalyticsService.getTrends(12);

      expect(result.weeks).toHaveLength(3);
      expect(result.series).toHaveLength(1);
      expect(result.series[0].personaName).toBe("테크 에디터");
      expect(result.series[0].isPreset).toBe(true);
    });

    it("weeks 기본값 12 로 호출된다", async () => {
      let capturedUrl = "";
      server.use(
        http.get("http://localhost/api/admin/analytics/personas/trends", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(mockTrendsResponse);
        })
      );

      await personaAnalyticsService.getTrends();

      expect(capturedUrl).toContain("weeks=12");
    });
  });

  describe("getBatchRuns", () => {
    it("admin/analytics/personas/batch-runs 를 호출하고 배열을 반환한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/analytics/personas/batch-runs", () =>
          HttpResponse.json(mockBatchRuns)
        )
      );

      const result = await personaAnalyticsService.getBatchRuns(10);

      expect(result).toHaveLength(1);
      expect(result[0].triggerType).toBe("SCHEDULED");
      expect(result[0].overallStatus).toBe("SUCCESS");
      expect(result[0].personasScanned).toBe(18);
    });
  });

  describe("runBackfill", () => {
    it("admin/analytics/personas/backfill 를 POST 로 호출하고 BackfillResult 를 반환한다", async () => {
      server.use(
        http.post("http://localhost/api/admin/analytics/personas/backfill", () =>
          HttpResponse.json(mockBackfillResult)
        )
      );

      const result = await personaAnalyticsService.runBackfill(12);

      expect(result.weeksProcessed).toBe(12);
      expect(result.snapshotRowsCreated).toBe(216);
      expect(result.durationMs).toBe(3500);
    });

    it("weeks 파라미터가 쿼리스트링에 포함된다", async () => {
      let capturedUrl = "";
      server.use(
        http.post("http://localhost/api/admin/analytics/personas/backfill", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(mockBackfillResult);
        })
      );

      await personaAnalyticsService.runBackfill(26);

      expect(capturedUrl).toContain("weeks=26");
    });
  });
});
