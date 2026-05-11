// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse, type JsonBodyType } from "msw";
import type {
  KeywordTrendResponse,
  SovResponse,
  SentimentTrendResponse,
  CompetitorSentimentResponse,
  TopArticlesResponse,
  KeywordEntityResponse,
  AiQaResponse,
  CompetitorSnapshotItem,
  CompetitorTimelineItem,
  BriefingItem
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

import { newsIntelligenceService } from "@/services/newsIntelligenceService";

const mockBriefings: { briefings: BriefingItem[] } = {
  briefings: [
    {
      categoryId: "cat-1",
      categoryName: "IT/기술",
      summaryDate: "2026-01-01",
      title: "AI 트렌드 브리핑",
      overallSummary: "오늘의 AI 뉴스",
      topicKeywords: ["AI", "머신러닝"],
      totalItems: 5
    }
  ]
};

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

const mockCompetitorTimeline: { items: CompetitorTimelineItem[] } = {
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
  shares: [
    { competitorId: "c-1", name: "경쟁사A", count: 40, share: 40 }
  ]
};

const mockSentimentTrend: SentimentTrendResponse = {
  period: { from: "2026-01-01", to: "2026-01-07" },
  daily: [
    { date: "2026-01-01", positive: 10, neutral: 5, negative: 2, total: 17 }
  ],
  summary: {
    positiveRate: 0.59,
    neutralRate: 0.29,
    negativeRate: 0.12,
    dominantSentiment: "POSITIVE",
    changeFromPrevious: 0.05
  }
};

const mockCompetitorSentiment: CompetitorSentimentResponse = {
  competitors: [
    {
      competitorId: "c-1",
      competitorName: "경쟁사A",
      positive: 20,
      neutral: 10,
      negative: 5,
      total: 35,
      positiveRate: 0.57
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
      keywords: ["AI"],
      sentiment: "POSITIVE",
      eventType: null,
      createdAt: "2026-01-01T00:00:00Z"
    }
  ]
};

const mockKeywordEntities: KeywordEntityResponse = {
  items: [{ keyword: "MegaCorp", category: "ORG", count: 15 }]
};

const mockAiQa: AiQaResponse = {
  question: "AI 트렌드는?",
  answer: "AI 시장이 빠르게 성장하고 있습니다.",
  relatedArticles: [
    {
      summaryId: "sum-1",
      title: "관련 기사 1",
      sourceLink: "https://example.com/1",
      relevanceReason: "AI 키워드 포함"
    },
    {
      summaryId: "sum-2",
      title: "관련 기사 2",
      sourceLink: "https://example.com/2",
      relevanceReason: "AI 성장 논의"
    }
  ],
  contextArticleCount: 2
};

const handlers = [
  http.get("http://localhost/api/user/briefing/today", () => HttpResponse.json(mockBriefings)),
  http.get("http://localhost/api/admin/keywords/trend", () => HttpResponse.json(mockKeywordTrend)),
  http.get("http://localhost/api/admin/competitors/snapshot", () => HttpResponse.json(mockCompetitorSnapshot)),
  http.get("http://localhost/api/admin/competitors/timeline", () => HttpResponse.json(mockCompetitorTimeline)),
  http.get("http://localhost/api/admin/competitors/sov", () => HttpResponse.json(mockSov)),
  http.get("http://localhost/api/admin/competitors/sentiment", () => HttpResponse.json(mockCompetitorSentiment)),
  http.get("http://localhost/api/admin/sentiment/trend", () => HttpResponse.json(mockSentimentTrend)),
  http.get("http://localhost/api/admin/articles/top", () => HttpResponse.json(mockTopArticles)),
  http.get("http://localhost/api/admin/keyword-entities", () => HttpResponse.json(mockKeywordEntities)),
  http.post("http://localhost/api/admin/report/ai-qa", () => HttpResponse.json(mockAiQa))
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

/**
 * 요청 URL을 캡처하는 MSW 핸들러 헬퍼.
 * - 각 테스트에서 server.use()로 override하여 URL/쿼리스트링을 검증한다.
 */
function captureGet(
  url: string,
  capture: { url?: URL; search?: URLSearchParams },
  responseBody: JsonBodyType
) {
  return http.get(url, ({ request }) => {
    const u = new URL(request.url);
    capture.url = u;
    capture.search = u.searchParams;
    return HttpResponse.json(responseBody);
  });
}

function capturePost(
  url: string,
  capture: { url?: URL; body?: unknown },
  responseBody: JsonBodyType
) {
  return http.post(url, async ({ request }) => {
    const u = new URL(request.url);
    capture.url = u;
    capture.body = await request.json().catch(() => null);
    return HttpResponse.json(responseBody);
  });
}

describe("newsIntelligenceService", () => {
  describe("getBriefings", () => {
    it("오늘의 브리핑 목록을 반환해야 한다", async () => {
      const result = await newsIntelligenceService.getBriefings();
      expect(result.briefings).toHaveLength(1);
      expect(result.briefings[0].title).toBe("AI 트렌드 브리핑");
    });

    it("categoryId를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { url?: URL; search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/user/briefing/today", capture, mockBriefings));

      const result = await newsIntelligenceService.getBriefings("cat-1");

      expect(capture.search?.get("categoryId")).toBe("cat-1");
      expect(result.briefings[0].categoryId).toBe("cat-1");
    });
  });

  describe("getKeywordTrend", () => {
    it("키워드 트렌드 응답 필드를 반환해야 한다", async () => {
      const result = await newsIntelligenceService.getKeywordTrend();
      expect(result.keywords).toHaveLength(1);
      expect(result.keywords[0].keyword).toBe("AI");
      expect(result.keywords[0].totalCount).toBe(50);
      expect(result.period.from).toBe("2026-01-01");
    });

    it("days, top, categoryId 파라미터를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { url?: URL; search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/keywords/trend", capture, mockKeywordTrend));

      await newsIntelligenceService.getKeywordTrend({ days: 7, top: 10, categoryId: "cat-1" });

      expect(capture.search?.get("days")).toBe("7");
      expect(capture.search?.get("top")).toBe("10");
      expect(capture.search?.get("categoryId")).toBe("cat-1");
    });
  });

  describe("getCompetitorSnapshot", () => {
    it("경쟁사 스냅샷을 반환해야 한다", async () => {
      const result = await newsIntelligenceService.getCompetitorSnapshot();
      expect(result.items).toHaveLength(1);
      expect(result.items[0].competitorName).toBe("경쟁사A");
    });

    it("limit, days 파라미터를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { url?: URL; search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/competitors/snapshot", capture, mockCompetitorSnapshot));

      await newsIntelligenceService.getCompetitorSnapshot({ days: 30, limit: 5 });

      expect(capture.search?.get("days")).toBe("30");
      expect(capture.search?.get("limit")).toBe("5");
    });
  });

  describe("getCompetitorTimeline", () => {
    it("경쟁사 타임라인을 반환해야 한다", async () => {
      const result = await newsIntelligenceService.getCompetitorTimeline();
      expect(result.items).toHaveLength(1);
      expect(result.items[0].eventType).toBe("PRODUCT_LAUNCH");
    });

    it("days, competitorId, eventType 필터를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { url?: URL; search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/competitors/timeline", capture, mockCompetitorTimeline));

      await newsIntelligenceService.getCompetitorTimeline({
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
    it("기본 days=30으로 SOV 응답 필드를 반환해야 한다", async () => {
      const capture: { url?: URL; search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/competitors/sov", capture, mockSov));

      const result = await newsIntelligenceService.getCompetitorSov();

      expect(capture.search?.get("days")).toBe("30");
      expect(result.totalArticles).toBe(100);
      expect(result.shares[0].share).toBe(40);
    });

    it("days 파라미터를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { url?: URL; search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/competitors/sov", capture, mockSov));

      await newsIntelligenceService.getCompetitorSov(7);

      expect(capture.search?.get("days")).toBe("7");
    });
  });

  describe("getCompetitorSentiment", () => {
    it("기본 days=7로 경쟁사 논조 비교를 반환해야 한다", async () => {
      const capture: { url?: URL; search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/competitors/sentiment", capture, mockCompetitorSentiment));

      const result = await newsIntelligenceService.getCompetitorSentiment();

      expect(capture.search?.get("days")).toBe("7");
      expect(result.competitors).toHaveLength(1);
      expect(result.competitors[0].competitorName).toBe("경쟁사A");
      expect(result.competitors[0].positive).toBe(20);
    });
  });

  describe("getSentimentTrend", () => {
    it("논조 추이 데이터 포인트를 반환해야 한다", async () => {
      const result = await newsIntelligenceService.getSentimentTrend();
      expect(result.daily).toHaveLength(1);
      expect(result.daily[0].positive).toBe(10);
      expect(result.daily[0].neutral).toBe(5);
      expect(result.daily[0].negative).toBe(2);
    });

    it("days, categoryId를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { url?: URL; search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/sentiment/trend", capture, mockSentimentTrend));

      await newsIntelligenceService.getSentimentTrend({ days: 7, categoryId: "cat-1" });

      expect(capture.search?.get("days")).toBe("7");
      expect(capture.search?.get("categoryId")).toBe("cat-1");
    });
  });

  describe("getTopArticles", () => {
    it("주요 기사 응답 필드를 반환해야 한다", async () => {
      const result = await newsIntelligenceService.getTopArticles();
      expect(result.items).toHaveLength(1);
      expect(result.items[0].summaryId).toBe("sum-1");
      expect(result.items[0].sentiment).toBe("POSITIVE");
    });

    it("days, limit, categoryId, sentiment 필터를 쿼리스트링으로 전달해야 한다", async () => {
      const capture: { url?: URL; search?: URLSearchParams } = {};
      server.use(captureGet("http://localhost/api/admin/articles/top", capture, mockTopArticles));

      await newsIntelligenceService.getTopArticles({
        days: 7,
        limit: 10,
        categoryId: "cat-1",
        sentiment: "POSITIVE"
      });

      expect(capture.search?.get("days")).toBe("7");
      expect(capture.search?.get("limit")).toBe("10");
      expect(capture.search?.get("categoryId")).toBe("cat-1");
      expect(capture.search?.get("sentiment")).toBe("POSITIVE");
    });
  });

  describe("getKeywordEntities", () => {
    it("키워드 엔티티 분류 응답 필드를 반환해야 한다", async () => {
      const result = await newsIntelligenceService.getKeywordEntities();
      expect(result.items).toHaveLength(1);
      expect(result.items[0].keyword).toBe("MegaCorp");
      expect(result.items[0].category).toBe("ORG");
      expect(result.items[0].count).toBe(15);
    });
  });

  describe("askAiQuestion", () => {
    it("AI Q&A 응답 필드를 반환하고 question과 days를 body로 전송해야 한다", async () => {
      const capture: { url?: URL; body?: unknown } = {};
      server.use(capturePost("http://localhost/api/admin/report/ai-qa", capture, mockAiQa));

      const result = await newsIntelligenceService.askAiQuestion({
        question: "AI 트렌드는?",
        days: 7
      });

      expect(capture.body).toEqual({ question: "AI 트렌드는?", days: 7 });
      expect(result.question).toBe("AI 트렌드는?");
      expect(result.answer).toBe("AI 시장이 빠르게 성장하고 있습니다.");
      expect(result.relatedArticles).toHaveLength(2);
      expect(result.relatedArticles[0].summaryId).toBe("sum-1");
      expect(result.relatedArticles[1].summaryId).toBe("sum-2");
    });

    it("categoryId를 body에 포함하여 전송해야 한다", async () => {
      const capture: { url?: URL; body?: unknown } = {};
      server.use(capturePost("http://localhost/api/admin/report/ai-qa", capture, mockAiQa));

      await newsIntelligenceService.askAiQuestion({
        question: "최근 뉴스는?",
        categoryId: "cat-1"
      });

      expect(capture.body).toEqual({ question: "최근 뉴스는?", categoryId: "cat-1" });
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/keywords/trend", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(newsIntelligenceService.getKeywordTrend()).rejects.toThrow();
    });
  });
});
