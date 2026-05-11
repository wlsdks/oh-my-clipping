// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse, type JsonBodyType } from "msw";
import type {
  KeywordTrendResponse,
  SovResponse,
  TopArticlesResponse,
  SentimentTrendResponse,
  CompetitorSnapshotItem,
  CompetitorTimelineItem
} from "@/types/newsReport";

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

import { newsReportService } from "@/services/newsReportService";
import type { ReportSettings } from "@/types/newsReport";
import type { ReportReleaseItem } from "@/types/visualCard";

const mockKeywordTrend: KeywordTrendResponse = {
  period: { from: "2026-01-01", to: "2026-01-07" },
  keywords: [
    {
      keyword: "AI",
      dailyCounts: [{ date: "2026-01-01", count: 10 }],
      totalCount: 50,
      changeRate: 0.2
    }
  ]
};

const mockCompetitorSnapshot: { items: CompetitorSnapshotItem[] } = {
  items: [
    {
      summaryId: "s-1",
      competitorName: "경쟁사A",
      title: "경쟁사 기사",
      sourceLink: "https://example.com",
      importanceScore: 0.8,
      createdAt: "2026-01-01T00:00:00Z"
    }
  ]
};

const mockTopArticles: TopArticlesResponse = {
  items: [
    {
      summaryId: "sum-1",
      title: "주요 기사",
      sourceLink: "https://example.com",
      importanceScore: 0.9,
      keywords: [],
      sentiment: null,
      eventType: null,
      createdAt: "2026-01-01T00:00:00Z"
    }
  ]
};

const mockTimeline: { items: CompetitorTimelineItem[] } = {
  items: [
    {
      summaryId: "t-1",
      competitorId: "c-1",
      competitorName: "경쟁사A",
      title: "신제품 출시",
      summary: "신제품 출시",
      sourceLink: "https://example.com",
      importanceScore: 0.8,
      eventType: "PRODUCT_LAUNCH",
      sentiment: null,
      createdAt: "2026-01-05T00:00:00Z"
    }
  ]
};

const mockSov: SovResponse = {
  period: { from: "2026-01-01", to: "2026-01-30" },
  totalArticles: 100,
  shares: []
};

const mockReportSettings: ReportSettings = {
  weeklyEnabled: true,
  weeklyDay: "MONDAY",
  weeklyHour: 9,
  weeklySlackChannelId: "C123",
  weeklyIncludeKeywordTrend: true,
  weeklyIncludeCompetitor: true,
  weeklyIncludeTopArticles: true,
  weeklyIncludeSentiment: false,
  monthlyEnabled: false,
  monthlyHour: 10,
  monthlySlackChannelId: null
};

const mockReleases: ReportReleaseItem[] = [
  {
    summaryId: "sum-1",
    categoryId: "cat-1",
    title: "긴급 릴리즈 기사",
    sourceLink: "https://example.com",
    importanceScore: 0.92,
    releaseType: "BREAKING",
    detectionReason: "중요 키워드 감지",
    createdAt: "2026-01-01T00:00:00Z"
  }
];

const mockSentimentTrend: SentimentTrendResponse = {
  period: { from: "2026-01-01", to: "2026-01-07" },
  daily: [{ date: "2026-01-01", positive: 10, neutral: 5, negative: 2, total: 17 }],
  summary: {
    positiveRate: 0.59,
    neutralRate: 0.29,
    negativeRate: 0.12,
    dominantSentiment: "POSITIVE",
    changeFromPrevious: 0.05
  }
};

const handlers = [
  http.get("http://localhost/api/admin/keywords/trend", () => HttpResponse.json(mockKeywordTrend)),
  http.get("http://localhost/api/admin/competitors/snapshot", () => HttpResponse.json(mockCompetitorSnapshot)),
  http.get("http://localhost/api/admin/articles/top", () => HttpResponse.json(mockTopArticles)),
  http.get("http://localhost/api/admin/competitors/timeline", () => HttpResponse.json(mockTimeline)),
  http.get("http://localhost/api/admin/competitors/sov", () => HttpResponse.json(mockSov)),
  http.get("http://localhost/api/admin/report-settings", () => HttpResponse.json(mockReportSettings)),
  http.put("http://localhost/api/admin/report-settings", () => HttpResponse.json(mockReportSettings)),
  http.get("http://localhost/api/admin/ops-reports/releases", () => HttpResponse.json(mockReleases)),
  http.get("http://localhost/api/admin/sentiment/trend", () => HttpResponse.json(mockSentimentTrend))
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

function capturePut(
  url: string,
  capture: { body?: unknown },
  responseBody: JsonBodyType
) {
  return http.put(url, async ({ request }) => {
    capture.body = await request.json().catch(() => null);
    return HttpResponse.json(responseBody);
  });
}

describe("newsReportService", () => {
  describe("getKeywordTrend", () => {
    it("키워드 트렌드 응답 필드를 반환해야 한다", async () => {
      const result = await newsReportService.getKeywordTrend();
      expect(result.keywords).toHaveLength(1);
      expect(result.keywords[0].keyword).toBe("AI");
      expect(result.keywords[0].totalCount).toBe(50);
    });

    it("days/top/categoryId를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/keywords/trend", capture, mockKeywordTrend));

      await newsReportService.getKeywordTrend({ days: 7, top: 10, categoryId: "cat-1" });

      expect(capture.search?.get("days")).toBe("7");
      expect(capture.search?.get("top")).toBe("10");
      expect(capture.search?.get("categoryId")).toBe("cat-1");
    });
  });

  describe("getCompetitorSnapshot", () => {
    it("경쟁사 스냅샷을 반환해야 한다", async () => {
      const result = await newsReportService.getCompetitorSnapshot();
      expect(result.items).toHaveLength(1);
      expect(result.items[0].competitorName).toBe("경쟁사A");
    });
  });

  describe("getTopArticles", () => {
    it("주요 기사 응답 필드를 반환해야 한다", async () => {
      const result = await newsReportService.getTopArticles();
      expect(result.items).toHaveLength(1);
      expect(result.items[0].summaryId).toBe("sum-1");
    });

    it("days/limit/categoryId를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/articles/top", capture, mockTopArticles));

      await newsReportService.getTopArticles({ days: 7, limit: 5, categoryId: "cat-1" });

      expect(capture.search?.get("days")).toBe("7");
      expect(capture.search?.get("limit")).toBe("5");
      expect(capture.search?.get("categoryId")).toBe("cat-1");
    });
  });

  describe("getCompetitorTimeline", () => {
    it("경쟁사 타임라인을 반환해야 한다", async () => {
      const result = await newsReportService.getCompetitorTimeline();
      expect(result.items).toHaveLength(1);
    });

    it("days/competitorId/eventType을 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/competitors/timeline", capture, mockTimeline));

      await newsReportService.getCompetitorTimeline({
        days: 7,
        competitorId: "c-1",
        eventType: "PRODUCT_LAUNCH"
      });

      expect(capture.search?.get("days")).toBe("7");
      expect(capture.search?.get("competitorId")).toBe("c-1");
      expect(capture.search?.get("eventType")).toBe("PRODUCT_LAUNCH");
    });
  });

  describe("getCompetitorSov", () => {
    it("기본 days=30을 쿼리스트링으로 전송하고 SOV 응답을 반환해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/competitors/sov", capture, mockSov));

      const result = await newsReportService.getCompetitorSov();

      expect(capture.search?.get("days")).toBe("30");
      expect(result.totalArticles).toBe(100);
    });

    it("days 파라미터를 명시적으로 지정할 수 있어야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/competitors/sov", capture, mockSov));

      await newsReportService.getCompetitorSov(7);

      expect(capture.search?.get("days")).toBe("7");
    });
  });

  describe("getReportSettings", () => {
    it("리포트 설정을 반환해야 한다", async () => {
      const result = await newsReportService.getReportSettings();
      expect(result.weeklyEnabled).toBe(true);
      expect(result.weeklyDay).toBe("MONDAY");
      expect(result.weeklyHour).toBe(9);
      expect(result.monthlyEnabled).toBe(false);
    });
  });

  describe("updateReportSettings", () => {
    it("업데이트 body를 PUT으로 전송하고 결과를 반환해야 한다", async () => {
      const capture: { body?: unknown } = {};
      server.use(capturePut("http://localhost/api/admin/report-settings", capture, mockReportSettings));

      const result = await newsReportService.updateReportSettings({
        weeklyEnabled: false
      });

      expect(capture.body).toEqual({ weeklyEnabled: false });
      expect(result.weeklyEnabled).toBe(true); // server mock returns current settings
      expect(result.weeklyDay).toBe("MONDAY");
    });
  });

  describe("listReportReleases", () => {
    it("리포트 릴리즈 목록을 반환해야 한다", async () => {
      const result = await newsReportService.listReportReleases();
      expect(result).toHaveLength(1);
      expect(result[0].releaseType).toBe("BREAKING");
      expect(result[0].importanceScore).toBe(0.92);
    });

    it("days/categoryId/limit을 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/ops-reports/releases", capture, mockReleases));

      await newsReportService.listReportReleases({ days: 7, categoryId: "cat-1", limit: 5 });

      expect(capture.search?.get("days")).toBe("7");
      expect(capture.search?.get("categoryId")).toBe("cat-1");
      expect(capture.search?.get("limit")).toBe("5");
    });
  });

  describe("getSentimentTrend", () => {
    it("감성 트렌드 데이터 포인트를 반환해야 한다", async () => {
      const result = await newsReportService.getSentimentTrend();
      expect(result.daily).toHaveLength(1);
      expect(result.daily[0].positive).toBe(10);
      expect(result.daily[0].negative).toBe(2);
    });

    it("days/categoryId를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/sentiment/trend", capture, mockSentimentTrend));

      await newsReportService.getSentimentTrend({ days: 14, categoryId: "cat-1" });

      expect(capture.search?.get("days")).toBe("14");
      expect(capture.search?.get("categoryId")).toBe("cat-1");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/report-settings", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(newsReportService.getReportSettings()).rejects.toThrow();
    });
  });
});
