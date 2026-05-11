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

import { competitorService } from "@/services/competitorService";
import type {
  Competitor,
  CompetitorTimelineResponse,
  SovResponse,
  KeywordPreviewResponse,
} from "@/types/competitor";

const mockCompetitor: Competitor = {
  id: "comp-1",
  name: "경쟁사 A",
  aliases: ["AI Corp", "클라우드"],
  excludeKeywords: ["채용", "후기"],
  tier: "DIRECT",
  isActive: true,
  rssFeeds: [
    { id: "feed-1", feedUrl: "https://example.com/feed.xml", label: "공식 블로그" }
  ],
  articleCount: 42,
  last24hCount: 3,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-15T00:00:00Z",
};

const mockTimeline: CompetitorTimelineResponse = {
  items: [
    {
      summaryId: "sum-1",
      competitorId: "comp-1",
      competitorName: "경쟁사 A",
      title: "신제품 출시",
      summary: "경쟁사 A가 신제품을 발표했습니다.",
      sourceLink: "https://example.com/news/1",
      importanceScore: 0.85,
      sentiment: "POSITIVE",
      eventType: "PRODUCT_LAUNCH",
      createdAt: "2026-01-10T09:00:00Z",
    }
  ]
};

const mockSov: SovResponse = {
  totalArticles: 100,
  shares: [
    { competitorId: "comp-1", competitorName: "경쟁사 A", articleCount: 40, sharePercent: 40.0 },
    { competitorId: "comp-2", competitorName: "경쟁사 B", articleCount: 60, sharePercent: 60.0 },
  ]
};

const mockPreview: KeywordPreviewResponse = {
  items: [
    { title: "AI 최신 동향", link: "https://news.example.com/1", publishedAt: "2026-01-10T08:00:00Z" },
    { title: "클라우드 시장 분석", link: "https://news.example.com/2", publishedAt: null },
  ],
  message: "키워드 미리보기 성공"
};

const handlers = [
  // list
  http.get("http://localhost/api/admin/competitors", () =>
    HttpResponse.json([mockCompetitor])
  ),

  // create
  http.post("http://localhost/api/admin/competitors", () =>
    HttpResponse.json(mockCompetitor, { status: 201 })
  ),

  // update
  http.put("http://localhost/api/admin/competitors/:id", () =>
    HttpResponse.json(mockCompetitor)
  ),

  // delete
  http.delete("http://localhost/api/admin/competitors/:id", () =>
    new HttpResponse(null, { status: 204 })
  ),

  // getTimeline
  http.get("http://localhost/api/admin/competitors/timeline", () =>
    HttpResponse.json(mockTimeline)
  ),

  // getSov
  http.get("http://localhost/api/admin/competitors/sov", () =>
    HttpResponse.json(mockSov)
  ),

  // previewKeywords
  http.post("http://localhost/api/admin/competitors/keyword-preview", () =>
    HttpResponse.json(mockPreview)
  ),
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("competitorService", () => {
  describe("list", () => {
    it("경쟁사 목록을 반환해야 한다", async () => {
      const result = await competitorService.list();
      expect(result).toEqual([mockCompetitor]);
      expect(result[0].name).toBe("경쟁사 A");
      expect(result[0].tier).toBe("DIRECT");
      expect(result[0].rssFeeds).toHaveLength(1);
    });
  });

  describe("create", () => {
    it("새 경쟁사를 생성하고 결과를 반환해야 한다", async () => {
      const result = await competitorService.create({
        name: "경쟁사 A",
        aliases: ["AI"],
        tier: "DIRECT",
      });
      expect(result).toEqual(mockCompetitor);
      expect(result.id).toBe("comp-1");
    });

    it("aliases와 rssFeeds 없이도 name 필드만으로 body를 전송해야 한다", async () => {
      let capturedBody: unknown;
      server.use(
        http.post("http://localhost/api/admin/competitors", async ({ request }) => {
          capturedBody = await request.json();
          return HttpResponse.json(mockCompetitor, { status: 201 });
        })
      );

      const result = await competitorService.create({ name: "경쟁사 B" });

      expect(capturedBody).toEqual({ name: "경쟁사 B" });
      expect(result.id).toBe("comp-1");
    });
  });

  describe("update", () => {
    it("경쟁사 정보를 수정하고 결과를 반환해야 한다", async () => {
      const result = await competitorService.update("comp-1", {
        name: "경쟁사 A (수정)",
        isActive: false,
      });
      expect(result).toEqual(mockCompetitor);
    });

    it("id에 포함된 슬래시를 URL 인코딩하여 경로에 전달해야 한다", async () => {
      let capturedPath: string | undefined;
      server.use(
        http.put("http://localhost/api/admin/competitors/:id", ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          return HttpResponse.json(mockCompetitor);
        })
      );

      const result = await competitorService.update("comp/special", { name: "테스트" });

      // encodeURIComponent가 '/'를 '%2F'로 변환하는지 검증
      expect(capturedPath).toContain("comp%2Fspecial");
      expect(result).toEqual(mockCompetitor);
    });
  });

  describe("delete", () => {
    it("경쟁사를 삭제하고 undefined를 반환해야 한다", async () => {
      const result = await competitorService.delete("comp-1");
      expect(result).toBeUndefined();
    });
  });

  describe("getTimeline", () => {
    it("타임라인 항목 목록을 반환해야 한다", async () => {
      const result = await competitorService.getTimeline({ days: 7 });
      expect(result).toEqual(mockTimeline);
      expect(result.items).toHaveLength(1);
      expect(result.items[0].title).toBe("신제품 출시");
    });

    it("파라미터 없이 호출하면 쿼리스트링 없이 요청해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/competitors/timeline", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockTimeline);
        })
      );

      const result = await competitorService.getTimeline({});

      expect(capturedSearch?.toString()).toBe("");
      expect(result).toEqual(mockTimeline);
    });

    it("days와 competitorId를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/competitors/timeline", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockTimeline);
        })
      );

      await competitorService.getTimeline({ days: 30, competitorId: "comp-1" });

      expect(capturedSearch?.get("days")).toBe("30");
      expect(capturedSearch?.get("competitorId")).toBe("comp-1");
    });
  });

  describe("getSov", () => {
    it("점유율 분석 결과를 반환해야 한다", async () => {
      const result = await competitorService.getSov(30);
      expect(result).toEqual(mockSov);
      expect(result.totalArticles).toBe(100);
      expect(result.shares).toHaveLength(2);
      expect(result.shares[0].sharePercent).toBe(40.0);
    });

    it("days 파라미터 없이 호출하면 쿼리스트링 없이 요청해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/competitors/sov", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockSov);
        })
      );

      const result = await competitorService.getSov();

      expect(capturedSearch?.toString()).toBe("");
      expect(result.totalArticles).toBe(100);
    });
  });

  describe("previewKeywords", () => {
    it("키워드 미리보기 결과를 반환해야 한다", async () => {
      const result = await competitorService.previewKeywords(["AI", "클라우드"]);
      expect(result).toEqual(mockPreview);
      expect(result.items).toHaveLength(2);
      expect(result.message).toBe("키워드 미리보기 성공");
    });

    it("publishedAt이 null인 항목을 포함할 수 있어야 한다", async () => {
      const result = await competitorService.previewKeywords(["클라우드"]);
      expect(result.items[1].publishedAt).toBeNull();
    });
  });

  describe("서비스 메서드 존재 여부", () => {
    it("모든 필수 메서드를 가지고 있어야 한다", () => {
      expect(typeof competitorService.list).toBe("function");
      expect(typeof competitorService.create).toBe("function");
      expect(typeof competitorService.update).toBe("function");
      expect(typeof competitorService.delete).toBe("function");
      expect(typeof competitorService.getTimeline).toBe("function");
      expect(typeof competitorService.getSov).toBe("function");
      expect(typeof competitorService.previewKeywords).toBe("function");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/competitors", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(competitorService.list()).rejects.toThrow();
    });

    it("404 응답 시 에러를 throw해야 한다", async () => {
      server.use(
        http.put("http://localhost/api/admin/competitors/:id", () =>
          HttpResponse.json({ message: "경쟁사를 찾을 수 없습니다" }, { status: 404 })
        )
      );
      await expect(competitorService.update("nonexistent", { name: "X" })).rejects.toThrow();
    });
  });
});
