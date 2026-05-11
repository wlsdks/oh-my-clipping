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

import { costService } from "@/services/costService";
import type {
  CostOverview,
  HourlyCost,
  CostModels,
  CostReliability,
  CostDetail,
  BudgetSettings
} from "@/types/cost";

const mockOverview: CostOverview = {
  from: "2026-01-01",
  to: "2026-01-31",
  totalCostUsd: 12.5,
  totalRequests: 500,
  dailyAvgRequests: 16,
  projectedMonthEndUsd: 15.0,
  previousPeriodCostUsd: 10.0,
  costChangePercent: 25.0,
  budgetUsd: 50.0,
  budgetUsedPercent: 25.0,
  dailyBreakdown: []
};

const mockHourlyCost: HourlyCost = {
  date: "2026-01-15",
  hours: [
    { hour: 9, inputCostUsd: 0.1, outputCostUsd: 0.3, totalCostUsd: 0.4, requestCount: 10 }
  ]
};

const mockModels: CostModels = {
  from: "2026-01-01",
  to: "2026-01-31",
  modelCount: 2,
  costPerArticleUsd: 0.02,
  previousCostPerArticleUsd: 0.018,
  models: [],
  promptVersions: [],
  categoryBreakdown: []
};

const mockReliability: CostReliability = {
  from: "2026-01-01",
  to: "2026-01-31",
  successRate: 0.97,
  emptyResultRate: 0.01,
  failureRate: 0.02,
  avgDurationMs: 1200,
  p50DurationMs: 900,
  p95DurationMs: 2500,
  dailyBreakdown: [],
  topErrors: []
};

const mockDetail: CostDetail = {
  from: "2026-01-01",
  to: "2026-01-31",
  inputCostPerMillionUsd: 3.0,
  outputCostPerMillionUsd: 15.0,
  rows: []
};

const mockBudget: BudgetSettings = {
  monthlyBudgetUsd: 100.0,
  alertThresholdPercent: 80,
  slackAlertEnabled: true
};

const handlers = [
  http.get("http://localhost/api/admin/costs/overview", () => HttpResponse.json(mockOverview)),
  http.get("http://localhost/api/admin/costs/overview/hourly", () => HttpResponse.json(mockHourlyCost)),
  http.get("http://localhost/api/admin/costs/models", () => HttpResponse.json(mockModels)),
  http.get("http://localhost/api/admin/costs/reliability", () => HttpResponse.json(mockReliability)),
  http.get("http://localhost/api/admin/costs/detail", () => HttpResponse.json(mockDetail)),
  http.get("http://localhost/api/admin/costs/budget", () => HttpResponse.json(mockBudget)),
  http.put("http://localhost/api/admin/costs/budget", () => HttpResponse.json(mockBudget))
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("costService", () => {
  describe("getOverview", () => {
    it("비용 개요를 반환해야 한다", async () => {
      const result = await costService.getOverview("2026-01-01", "2026-01-31");
      expect(result.totalCostUsd).toBe(12.5);
      expect(result.totalRequests).toBe(500);
      expect(result.budgetUsedPercent).toBe(25.0);
    });

    it("from/to/categoryId를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/costs/overview", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockOverview);
        })
      );

      await costService.getOverview("2026-01-01", "2026-01-31", "cat-1");

      expect(capturedSearch?.get("from")).toBe("2026-01-01");
      expect(capturedSearch?.get("to")).toBe("2026-01-31");
      expect(capturedSearch?.get("categoryId")).toBe("cat-1");
    });
  });

  describe("getHourly", () => {
    it("시간별 비용 데이터를 반환해야 한다", async () => {
      const result = await costService.getHourly("2026-01-15");
      expect(result.date).toBe("2026-01-15");
      expect(result.hours).toHaveLength(1);
      expect(result.hours[0].hour).toBe(9);
    });

    it("date와 categoryId를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/costs/overview/hourly", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockHourlyCost);
        })
      );

      await costService.getHourly("2026-01-15", "cat-1");

      expect(capturedSearch?.get("date")).toBe("2026-01-15");
      expect(capturedSearch?.get("categoryId")).toBe("cat-1");
    });
  });

  describe("getModels", () => {
    it("모델별 비용 데이터를 반환해야 한다", async () => {
      const result = await costService.getModels("2026-01-01", "2026-01-31");
      expect(result.modelCount).toBe(2);
      expect(result.costPerArticleUsd).toBe(0.02);
    });
  });

  describe("getReliability", () => {
    it("신뢰성 지표를 반환해야 한다", async () => {
      const result = await costService.getReliability("2026-01-01", "2026-01-31");
      expect(result.successRate).toBe(0.97);
      expect(result.failureRate).toBe(0.02);
      expect(result.p95DurationMs).toBe(2500);
    });
  });

  describe("getDetail", () => {
    it("비용 상세 내역을 반환해야 한다", async () => {
      const result = await costService.getDetail("2026-01-01", "2026-01-31");
      expect(result.inputCostPerMillionUsd).toBe(3.0);
      expect(result.outputCostPerMillionUsd).toBe(15.0);
    });
  });

  describe("getBudget", () => {
    it("예산 설정을 반환해야 한다", async () => {
      const result = await costService.getBudget();
      expect(result.monthlyBudgetUsd).toBe(100.0);
      expect(result.alertThresholdPercent).toBe(80);
      expect(result.slackAlertEnabled).toBe(true);
    });
  });

  describe("updateBudget", () => {
    it("예산 업데이트 body를 PUT으로 전송하고 결과를 반환해야 한다", async () => {
      let capturedBody: unknown;
      server.use(
        http.put("http://localhost/api/admin/costs/budget", async ({ request }) => {
          capturedBody = await request.json();
          return HttpResponse.json(mockBudget);
        })
      );

      const result = await costService.updateBudget({
        monthlyBudgetUsd: 150.0,
        alertThresholdPercent: 90,
        slackAlertEnabled: false
      });

      expect(capturedBody).toEqual({
        monthlyBudgetUsd: 150.0,
        alertThresholdPercent: 90,
        slackAlertEnabled: false
      });
      expect(result.monthlyBudgetUsd).toBe(100.0); // mock returns existing settings
      expect(result.alertThresholdPercent).toBe(80);
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/costs/overview", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(costService.getOverview("2026-01-01", "2026-01-31")).rejects.toThrow();
    });
  });
});
