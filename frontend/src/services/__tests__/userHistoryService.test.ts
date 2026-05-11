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

import { userHistoryService } from "@/services/userHistoryService";
import type {
  ArticleHistoryPage,
  ArticleDetail,
  BookmarkToggleResult,
  UserMonthlyStatRow,
  UndeliveredDigest
} from "@/types/insight";

const mockHistoryPage: ArticleHistoryPage = {
  items: [
    {
      id: "sum-1",
      title: "AI 뉴스",
      summary: "AI 관련 최신 뉴스",
      keywords: ["AI"],
      importanceScore: 0.85,
      sourceLink: "https://techcrunch.com",
      categoryId: "cat-1",
      categoryName: "IT/기술",
      isBookmarked: false,
      createdAt: "2026-01-01T00:00:00Z"
    }
  ],
  totalCount: 1,
  totalPages: 1,
  page: 0,
  size: 20
};

const mockArticleDetail: ArticleDetail = {
  id: "sum-1",
  title: "AI 뉴스 상세",
  summary: "AI 관련 최신 뉴스 요약",
  insights: null,
  originalContent: null,
  keywords: ["AI", "머신러닝"],
  importanceScore: 0.85,
  sourceLink: "https://techcrunch.com/ai-news",
  categoryId: "cat-1",
  categoryName: "IT/기술",
  isBookmarked: true,
  createdAt: "2026-01-01T00:00:00Z",
  relatedArticles: []
};

const mockBookmarkResult: BookmarkToggleResult = {
  summaryId: "sum-1",
  isBookmarked: true
};

const mockMonthlyStats: UserMonthlyStatRow[] = [
  {
    id: "stat-1",
    categoryId: "cat-1",
    categoryName: "IT/기술",
    statDate: "2026-01-31",
    itemsCollected: 120,
    itemsSummarized: 100,
    itemsSent: 85,
    topKeywords: ["AI"],
    avgImportanceScore: 0.75
  }
];

const mockUndeliveredDigests: UndeliveredDigest[] = [
  {
    deliveryLogId: "log-1",
    categoryName: "IT/기술",
    deliveryDate: "2026-01-01",
    deliveryTimeLabel: "오전 9시",
    status: "FAILED",
    retryCount: 1,
    maxRetries: 3,
    articleCount: 5,
    articles: []
  }
];

const handlers = [
  http.get("http://localhost/api/user/history/articles", () =>
    HttpResponse.json(mockHistoryPage)
  ),
  http.get("http://localhost/api/user/history/articles/sum-1", () =>
    HttpResponse.json(mockArticleDetail)
  ),
  http.post("http://localhost/api/user/history/articles/sum-1/bookmark", () =>
    HttpResponse.json(mockBookmarkResult)
  ),
  http.get("http://localhost/api/user/stats/monthly", () =>
    HttpResponse.json(mockMonthlyStats)
  ),
  http.get("http://localhost/api/user/history/undelivered-digests", () =>
    HttpResponse.json(mockUndeliveredDigests)
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("userHistoryService", () => {
  describe("searchArticleHistory", () => {
    it("기사 히스토리를 반환해야 한다", async () => {
      const result = await userHistoryService.searchArticleHistory({});
      expect(result.items).toHaveLength(1);
      expect(result.items[0].title).toBe("AI 뉴스");
      expect(result.totalCount).toBe(1);
    });

    it("키워드를 keyword 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/user/history/articles", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockHistoryPage);
        })
      );

      await userHistoryService.searchArticleHistory({ keyword: "AI" });

      expect(capturedSearch?.get("keyword")).toBe("AI");
    });

    it("dateFrom/dateTo를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/user/history/articles", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockHistoryPage);
        })
      );

      await userHistoryService.searchArticleHistory({
        dateFrom: "2026-01-01",
        dateTo: "2026-01-31"
      });

      expect(capturedSearch?.get("dateFrom")).toBe("2026-01-01");
      expect(capturedSearch?.get("dateTo")).toBe("2026-01-31");
    });

    it("bookmarkedOnly=true를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/user/history/articles", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockHistoryPage);
        })
      );

      await userHistoryService.searchArticleHistory({ bookmarkedOnly: true });

      expect(capturedSearch?.get("bookmarkedOnly")).toBe("true");
    });

    it("page/size를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/user/history/articles", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockHistoryPage);
        })
      );

      await userHistoryService.searchArticleHistory({ page: 0, size: 10 });

      expect(capturedSearch?.get("page")).toBe("0");
      expect(capturedSearch?.get("size")).toBe("10");
    });
  });

  describe("getArticleDetail", () => {
    it("기사 상세 정보를 반환해야 한다", async () => {
      const result = await userHistoryService.getArticleDetail("sum-1");
      expect(result.id).toBe("sum-1");
      expect(result.title).toBe("AI 뉴스 상세");
      expect(result.keywords).toContain("AI");
      expect(result.isBookmarked).toBe(true);
    });

    it("상세 정보에 sourceLink와 whyImportant가 있어야 한다", async () => {
      const result = await userHistoryService.getArticleDetail("sum-1");
      expect(result).toHaveProperty("sourceLink");
      expect(result).toHaveProperty("insights");
    });
  });

  describe("toggleArticleBookmark", () => {
    it("북마크 토글 결과를 반환해야 한다", async () => {
      const result = await userHistoryService.toggleArticleBookmark("sum-1");
      expect(result.summaryId).toBe("sum-1");
      expect(result.isBookmarked).toBe(true);
    });
  });

  describe("getUserMonthlyStats", () => {
    it("월별 통계를 반환해야 한다", async () => {
      const result = await userHistoryService.getUserMonthlyStats("2026-01");
      expect(result).toHaveLength(1);
      expect(result[0].statDate).toBe("2026-01-31");
      expect(result[0].itemsCollected).toBe(120);
      expect(result[0].itemsSent).toBe(85);
    });
  });

  describe("getUndeliveredDigests", () => {
    it("미전송 다이제스트 목록을 반환해야 한다", async () => {
      const result = await userHistoryService.getUndeliveredDigests();
      expect(result).toHaveLength(1);
      expect(result[0].categoryName).toBe("IT/기술");
      expect(result[0].articleCount).toBe(5);
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/user/history/articles", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(userHistoryService.searchArticleHistory({})).rejects.toThrow();
    });

    it("존재하지 않는 기사 조회 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/user/history/articles/not-found", () =>
          HttpResponse.json({ message: "Not Found" }, { status: 404 })
        )
      );
      await expect(userHistoryService.getArticleDetail("not-found")).rejects.toThrow();
    });
  });
});
