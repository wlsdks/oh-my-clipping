// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse, type JsonBodyType } from "msw";

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

import { analyticsService } from "@/services/analyticsService";
import type { DauRow, WizardFunnelRow, ArticleRankItem, CategoryStatItem, UserRequestStats } from "@/services/analyticsService";
import type { StatRow, DailyOperationalKpiRow, HotFeedbackResult, QualitySummary } from "@/types/insight";
import type { LlmCostSummary } from "@/types/cost";

const mockDauData: DauRow[] = [
  { date: "2026-01-01", count: 150 },
  { date: "2026-01-02", count: 180 }
];

const mockFunnelData: WizardFunnelRow[] = [
  { step: "시작", enters: 100, completes: 80, dropRate: 20 },
  { step: "소스 선택", enters: 80, completes: 60, dropRate: 25 }
];

const mockArticleRank: ArticleRankItem[] = [
  {
    rank: 1,
    summaryId: "sum-1",
    title: "AI 뉴스",
    categoryName: "IT/기술",
    sourceName: "TechCrunch",
    publishedAt: "2026-01-01T00:00:00Z",
    clicks: 500,
    impressions: 1000,
    ctr: 50,
    bookmarks: 20
  }
];

const mockCategoryStats: CategoryStatItem[] = [
  {
    categoryId: "cat-1",
    categoryName: "IT/기술",
    clicks: 1000,
    impressions: 5000,
    ctr: 20,
    sharePercent: 45.5
  }
];

const mockUserRequestStats: UserRequestStats = {
  pendingCount: 5,
  approvedCount: 20,
  rejectedCount: 3,
  totalCount: 28,
  avgApprovalHours: 4.5,
  topTopics: [{ requestName: "AI 뉴스", count: 8 }],
  rejectionReasons: [{ reason: "중복", count: 2 }],
  weeklyProcessedCount: 10
};

const mockStatRow: StatRow = {
  id: "stat-1",
  categoryId: "cat-1",
  statDate: "2026-01",
  itemsCollected: 500,
  itemsDuplicates: 50,
  itemsSummarized: 300,
  itemsSent: 200,
  slackSendAttempts: 210,
  slackSendSuccesses: 200,
  topKeywords: ["AI", "클라우드"],
  avgImportanceScore: 0.72
};

const mockDailyKpi: DailyOperationalKpiRow = {
  statDate: "2026-01-01",
  categoryId: "cat-1",
  itemsCollected: 100,
  excludedCount: 10,
  itemsDuplicates: 5,
  noiseRate: 0.1,
  duplicateRate: 0.05,
  reviewLeadTimeHours: 2.5,
  llmEstimatedCostUsd: 0.15,
  sendAttempts: 85,
  sendSuccesses: 83,
  sendSuccessRate: 0.976
};

const mockHotFeedback: HotFeedbackResult = {
  from: "2026-01-01",
  to: "2026-01-07",
  totalCandidates: 50,
  items: [
    {
      summaryId: "sum-1",
      title: "인기 기사",
      sourceLink: "https://example.com",
      likeCount: 30,
      neutralCount: 10,
      dislikeCount: 2,
      totalCount: 42,
      score: 0.95
    }
  ]
};

const mockQualitySummary: QualitySummary = {
  from: "2026-01-01",
  to: "2026-01-31",
  days: 30,
  itemsCollected: 1500,
  itemsSummarized: 1000,
  itemsSent: 800,
  reviewPendingCount: 50,
  reviewPendingRate: 0.05,
  excludeRate: 0.1,
  feedbackTotal: 200,
  feedbackPositiveRate: 0.85,
  sendSuccessRate: 0.98,
  recommendations: ["품질 양호"]
};

const mockLlmCost: LlmCostSummary = {
  from: "2026-01-01",
  to: "2026-01-31",
  inputCostPerMillionUsd: 3.0,
  outputCostPerMillionUsd: 15.0,
  totalRequestCount: 1000,
  totalTokensIn: 500000,
  totalTokensOut: 200000,
  totalEstimatedUsd: 4.5,
  rows: []
};

const handlers = [
  // getDau
  http.get("http://localhost/api/admin/analytics/dau", () =>
    HttpResponse.json({ data: mockDauData })
  ),

  // getWizardFunnel
  http.get("http://localhost/api/admin/analytics/wizard-funnel", () =>
    HttpResponse.json({ data: mockFunnelData })
  ),

  // getArticleRanking
  http.get("http://localhost/api/admin/analytics/article-ranking", () =>
    HttpResponse.json({ data: mockArticleRank })
  ),

  // getCategoryStats
  http.get("http://localhost/api/admin/analytics/category-stats", () =>
    HttpResponse.json({ data: mockCategoryStats })
  ),

  // getUserRequestStats
  http.get("http://localhost/api/admin/user-requests/stats", () =>
    HttpResponse.json(mockUserRequestStats)
  ),

  // getMonthlyStats
  http.get("http://localhost/api/admin/stats/monthly", () =>
    HttpResponse.json([mockStatRow])
  ),

  // getDailyOperationalKpi
  http.get("http://localhost/api/admin/stats/daily-kpi", () =>
    HttpResponse.json([mockDailyKpi])
  ),

  // getHotFeedback
  http.get("http://localhost/api/admin/feedback/hot", () =>
    HttpResponse.json(mockHotFeedback)
  ),

  // getOpsQualitySummary
  http.get("http://localhost/api/admin/ops-reports/summary", () =>
    HttpResponse.json(mockQualitySummary)
  ),

  // getLlmCostSummary
  http.get("http://localhost/api/admin/costs/llm", () =>
    HttpResponse.json(mockLlmCost)
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

/**
 * URL 쿼리스트링을 캡처하는 MSW 핸들러 헬퍼.
 */
function captureGet(
  url: string,
  capture: { search?: URLSearchParams },
  responseBody: JsonBodyType
) {
  return http.get(url, ({ request }) => {
    capture.search = new URL(request.url).searchParams;
    return HttpResponse.json(responseBody);
  });
}

describe("analyticsService", () => {
  describe("getDau", () => {
    it("기본 7일 DAU 데이터를 반환해야 한다", async () => {
      const result = await analyticsService.getDau();
      expect(result.data).toEqual(mockDauData);
      expect(result.data).toHaveLength(2);
    });

    it("지정된 일수를 days 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/analytics/dau", capture, { data: mockDauData }));

      const result = await analyticsService.getDau(30);

      expect(capture.search?.get("days")).toBe("30");
      expect(result.data).toHaveLength(2);
    });
  });

  describe("getDauByRange", () => {
    it("from/to 쿼리스트링으로 날짜 범위를 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/analytics/dau", capture, { data: mockDauData }));

      const result = await analyticsService.getDauByRange("2026-01-01", "2026-01-07");

      expect(capture.search?.get("from")).toBe("2026-01-01");
      expect(capture.search?.get("to")).toBe("2026-01-07");
      expect(result.data).toEqual(mockDauData);
    });
  });

  describe("getWizardFunnel", () => {
    it("위자드 퍼널 데이터를 반환해야 한다", async () => {
      const result = await analyticsService.getWizardFunnel();
      expect(result.data).toEqual(mockFunnelData);
      expect(result.data).toHaveLength(2);
    });

    it("days 쿼리스트링으로 지정된 일수의 퍼널을 요청해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/analytics/wizard-funnel", capture, { data: mockFunnelData }));

      const result = await analyticsService.getWizardFunnel(7);

      expect(capture.search?.get("days")).toBe("7");
      expect(result.data).toHaveLength(2);
    });
  });

  describe("getArticleRanking", () => {
    it("기사 랭킹 데이터를 반환해야 한다", async () => {
      const result = await analyticsService.getArticleRanking("2026-01-01", "2026-01-31");
      expect(result.data).toEqual(mockArticleRank);
      expect(result.data[0].rank).toBe(1);
      expect(result.data[0].clicks).toBe(500);
    });

    it("sort/limit 파라미터를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/analytics/article-ranking", capture, { data: mockArticleRank }));

      await analyticsService.getArticleRanking("2026-01-01", "2026-01-31", "impressions", 10);

      expect(capture.search?.get("from")).toBe("2026-01-01");
      expect(capture.search?.get("to")).toBe("2026-01-31");
      expect(capture.search?.get("sort")).toBe("impressions");
      expect(capture.search?.get("limit")).toBe("10");
    });
  });

  describe("getCategoryStats", () => {
    it("카테고리 통계를 반환해야 한다", async () => {
      const result = await analyticsService.getCategoryStats("2026-01-01", "2026-01-31");
      expect(result.data).toEqual(mockCategoryStats);
      expect(result.data[0].categoryName).toBe("IT/기술");
    });
  });
  describe("getUserRequestStats", () => {
    it("사용자 신청 통계를 반환해야 한다", async () => {
      const result = await analyticsService.getUserRequestStats();
      expect(result.pendingCount).toBe(5);
      expect(result.totalCount).toBe(28);
      expect(result.avgApprovalHours).toBe(4.5);
    });
  });

  describe("getMonthlyStats", () => {
    it("월별 통계를 반환해야 한다", async () => {
      const result = await analyticsService.getMonthlyStats("2026-01");
      expect(result).toEqual([mockStatRow]);
      expect(result[0].itemsCollected).toBe(500);
    });

    it("categoryId 필터를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/stats/monthly", capture, [mockStatRow]));

      await analyticsService.getMonthlyStats("2026-01", "cat-1");

      expect(capture.search?.get("yearMonth")).toBe("2026-01");
      expect(capture.search?.get("categoryId")).toBe("cat-1");
    });
  });

  describe("getDailyOperationalKpi", () => {
    it("일별 KPI를 반환하고 categoryId/from/to를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/stats/daily-kpi", capture, [mockDailyKpi]));

      const result = await analyticsService.getDailyOperationalKpi({
        categoryId: "cat-1",
        from: "2026-01-01",
        to: "2026-01-31"
      });

      expect(capture.search?.get("categoryId")).toBe("cat-1");
      expect(capture.search?.get("from")).toBe("2026-01-01");
      expect(capture.search?.get("to")).toBe("2026-01-31");
      expect(result).toEqual([mockDailyKpi]);
      expect(result[0].sendSuccessRate).toBe(0.976);
    });

    it("파라미터 없이 호출하면 쿼리스트링 없이 요청해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/stats/daily-kpi", capture, [mockDailyKpi]));

      const result = await analyticsService.getDailyOperationalKpi({});

      expect(capture.search?.toString()).toBe("");
      expect(result).toEqual([mockDailyKpi]);
    });
  });

  describe("getHotFeedback", () => {
    it("인기 피드백 데이터를 반환해야 한다", async () => {
      const result = await analyticsService.getHotFeedback({
        categoryId: "cat-1",
        limit: 10,
        days: 7
      });
      expect(result.totalCandidates).toBe(50);
      expect(result.items).toHaveLength(1);
      expect(result.items[0].score).toBe(0.95);
    });

    it("빈 파라미터로 호출하면 쿼리스트링 없이 요청해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/feedback/hot", capture, mockHotFeedback));

      const result = await analyticsService.getHotFeedback({});

      expect(capture.search?.toString()).toBe("");
      expect(result.totalCandidates).toBe(50);
    });
  });

  describe("getOpsQualitySummary", () => {
    it("운영 품질 요약을 반환해야 한다", async () => {
      const result = await analyticsService.getOpsQualitySummary();
      expect(result.days).toBe(30);
      expect(result.feedbackPositiveRate).toBe(0.85);
    });

    it("days 쿼리스트링으로 지정된 일수의 요약을 요청해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/ops-reports/summary", capture, mockQualitySummary));

      const result = await analyticsService.getOpsQualitySummary(7);

      expect(capture.search?.get("days")).toBe("7");
      expect(result.days).toBe(30); // mock always returns 30
    });

    it("서버 계약에 맞게 days를 7에서 90 사이로 보정해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/ops-reports/summary", capture, mockQualitySummary));

      await analyticsService.getOpsQualitySummary(2);
      expect(capture.search?.get("days")).toBe("7");

      await analyticsService.getOpsQualitySummary(120);
      expect(capture.search?.get("days")).toBe("90");
    });
  });

  describe("getLlmCostSummary", () => {
    it("LLM 비용 요약을 반환해야 한다", async () => {
      const result = await analyticsService.getLlmCostSummary();
      expect(result.totalEstimatedUsd).toBe(4.5);
      expect(result.totalRequestCount).toBe(1000);
    });

    it("days 쿼리스트링으로 지정된 일수의 비용 요약을 요청해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/costs/llm", capture, mockLlmCost));

      const result = await analyticsService.getLlmCostSummary(7);

      expect(capture.search?.get("days")).toBe("7");
      expect(result.totalEstimatedUsd).toBe(4.5);
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/analytics/dau", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );

      await expect(analyticsService.getDau()).rejects.toThrow();
    });
  });
});
