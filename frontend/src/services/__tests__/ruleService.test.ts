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

import { ruleService } from "@/services/ruleService";
import type { CategoryRule, RuleStatsResponse, ExcludedItemsResponse } from "@/types/category";

const mockCategoryRule: CategoryRule = {
  categoryId: "cat-1",
  includeKeywords: ["AI", "머신러닝"],
  excludeKeywords: ["광고", "홍보"],
  riskTags: ["스팸"],
  excludeEventTypes: [],
  includeThreshold: 0.6,
  reviewThreshold: 0.4,
  uncertainToReview: true,
  autoExcludeEnabled: false,
  revision: 3,
  updatedBy: "admin",
  updatedAt: "2026-01-01T00:00:00Z"
};

const mockRuleStats: RuleStatsResponse = {
  totalIncluded: 300,
  totalReview: 50,
  totalExcluded: 100,
  perCategory: [
    {
      categoryId: "cat-1",
      categoryName: "IT/기술",
      included: 300,
      review: 50,
      excluded: 100,
      hasRule: true
    }
  ]
};

const mockExcludedItems: ExcludedItemsResponse = {
  total: 3,
  items: [
    {
      title: "광고성 기사",
      reason: "제외 키워드 매치",
      matchedKeyword: "광고",
      score: 0.2,
      excludedAt: "2026-01-01T00:00:00Z"
    }
  ]
};

const handlers = [
  http.get("http://localhost/api/admin/category-rules/cat-1", () =>
    HttpResponse.json(mockCategoryRule)
  ),
  http.put("http://localhost/api/admin/category-rules/cat-1", () =>
    HttpResponse.json(mockCategoryRule)
  ),
  http.get("http://localhost/api/admin/category-rules/stats", () =>
    HttpResponse.json(mockRuleStats)
  ),
  http.get("http://localhost/api/admin/category-rules/cat-1/excluded-items", () =>
    HttpResponse.json(mockExcludedItems)
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("ruleService", () => {
  describe("getCategoryRule", () => {
    it("카테고리 규칙을 반환해야 한다", async () => {
      const result = await ruleService.getCategoryRule("cat-1");
      expect(result.categoryId).toBe("cat-1");
      expect(result.includeKeywords).toContain("AI");
      expect(result.excludeKeywords).toContain("광고");
      expect(result.includeThreshold).toBe(0.6);
    });

    it("규칙에 버전 정보가 있어야 한다", async () => {
      const result = await ruleService.getCategoryRule("cat-1");
      expect(result.revision).toBe(3);
      expect(result.updatedBy).toBe("admin");
    });
  });

  describe("updateCategoryRule", () => {
    it("업데이트 body를 PUT으로 전송하고 규칙을 반환해야 한다", async () => {
      let capturedBody: unknown;
      let capturedPath: string | undefined;
      server.use(
        http.put("http://localhost/api/admin/category-rules/:id", async ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          capturedBody = await request.json();
          return HttpResponse.json(mockCategoryRule);
        })
      );

      const result = await ruleService.updateCategoryRule("cat-1", {
        includeKeywords: ["AI", "딥러닝"],
        excludeKeywords: ["광고"],
        includeThreshold: 0.7
      });

      expect(capturedPath).toBe("/api/admin/category-rules/cat-1");
      expect(capturedBody).toEqual({
        includeKeywords: ["AI", "딥러닝"],
        excludeKeywords: ["광고"],
        includeThreshold: 0.7
      });
      expect(result.categoryId).toBe("cat-1");
    });

    it("부분 업데이트 body만 전송해도 결과를 반환해야 한다", async () => {
      let capturedBody: unknown;
      server.use(
        http.put("http://localhost/api/admin/category-rules/cat-1", async ({ request }) => {
          capturedBody = await request.json();
          return HttpResponse.json(mockCategoryRule);
        })
      );

      const result = await ruleService.updateCategoryRule("cat-1", {
        uncertainToReview: false
      });

      expect(capturedBody).toEqual({ uncertainToReview: false });
      expect(result.revision).toBe(3);
    });
  });

  describe("getRuleStats", () => {
    it("기본 7일 규칙 통계를 반환해야 한다", async () => {
      const result = await ruleService.getRuleStats();
      expect(result.totalIncluded).toBe(300);
      expect(result.totalReview).toBe(50);
      expect(result.totalExcluded).toBe(100);
    });

    it("days 파라미터를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/category-rules/stats", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockRuleStats);
        })
      );

      const result = await ruleService.getRuleStats(30);

      expect(capturedSearch?.get("days")).toBe("30");
      expect(result.totalIncluded).toBe(300);
    });

    it("카테고리별 통계가 포함되어야 한다", async () => {
      const result = await ruleService.getRuleStats();
      expect(result.perCategory).toHaveLength(1);
      expect(result.perCategory[0].categoryName).toBe("IT/기술");
    });
  });

  describe("getExcludedItems", () => {
    it("제외된 항목 목록을 반환해야 한다", async () => {
      const result = await ruleService.getExcludedItems("cat-1");
      expect(result.total).toBe(3);
      expect(result.items).toHaveLength(1);
      expect(result.items[0].matchedKeyword).toBe("광고");
    });

    it("limit 파라미터를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/category-rules/cat-1/excluded-items", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockExcludedItems);
        })
      );

      const result = await ruleService.getExcludedItems("cat-1", 10);

      expect(capturedSearch?.get("limit")).toBe("10");
      expect(result.total).toBe(3);
    });
  });

  describe("에러 처리", () => {
    it("존재하지 않는 카테고리 규칙 조회 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/category-rules/nonexistent", () =>
          HttpResponse.json({ message: "Not Found" }, { status: 404 })
        )
      );
      await expect(ruleService.getCategoryRule("nonexistent")).rejects.toThrow();
    });

    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/category-rules/stats", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(ruleService.getRuleStats()).rejects.toThrow();
    });
  });
});
