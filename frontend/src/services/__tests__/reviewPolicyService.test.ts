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

import { reviewPolicyService } from "@/services/reviewPolicyService";
import type {
  ReviewPolicyStatusResponse,
  ScoreDistribution,
} from "@/types/reviewPolicy";

const mockStatusResponse: ReviewPolicyStatusResponse = {
  categories: [
    {
      categoryId: "cat-1",
      categoryName: "AI/테크",
      autoApproveThreshold: 0.8,
      reviewThreshold: 0.5,
      pendingReviewCount: 12,
      last7DaysProcessed: 40,
      last7DaysAutoApproved: 28,
      last7DaysManuallyReviewed: 12,
      avgScore: 0.72,
      eventTypeDistribution: { RELEASE: 5, ANNOUNCE: 3, NULL: 2 },
      lastReviewedAt: "2026-04-18T10:00:00Z",
    },
  ],
  generatedAt: "2026-04-19T00:00:00Z",
};

const mockDistribution: ScoreDistribution = {
  buckets: [
    { range: "0.0-0.1", count: 0 },
    { range: "0.1-0.2", count: 1 },
    { range: "0.2-0.3", count: 3 },
    { range: "0.3-0.4", count: 4 },
    { range: "0.4-0.5", count: 6 },
    { range: "0.5-0.6", count: 8 },
    { range: "0.6-0.7", count: 10 },
    { range: "0.7-0.8", count: 7 },
    { range: "0.8-0.9", count: 5 },
    { range: "0.9-1.0", count: 2 },
  ],
  totalCount: 46,
  medianScore: 0.55,
  meanScore: 0.58,
};

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("reviewPolicyService", () => {
  describe("getPolicyStatus", () => {
    it("/admin/review-items/policy-status 에 GET 요청한다", async () => {
      // 요청 URL 의 path 를 캡처해서 실제 경로를 검증한다.
      let capturedPath: string | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/policy-status", ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          return HttpResponse.json(mockStatusResponse);
        })
      );
      await reviewPolicyService.getPolicyStatus();
      expect(capturedPath).toBe("/api/admin/review-items/policy-status");
    });

    it("응답 JSON 을 ReviewPolicyStatusResponse 타입으로 반환한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/review-items/policy-status", () =>
          HttpResponse.json(mockStatusResponse)
        )
      );
      const result = await reviewPolicyService.getPolicyStatus();
      expect(result).toEqual(mockStatusResponse);
      expect(result.categories).toHaveLength(1);
      expect(result.categories[0].categoryId).toBe("cat-1");
      expect(result.categories[0].autoApproveThreshold).toBe(0.8);
      expect(result.generatedAt).toBe("2026-04-19T00:00:00Z");
    });

    it("서버 오류 시 에러를 throw 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/review-items/policy-status", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(reviewPolicyService.getPolicyStatus()).rejects.toThrow();
    });
  });

  describe("getScoreDistribution", () => {
    it("categoryId 와 days 를 searchParams 로 전달한다", async () => {
      // ky 가 쿼리 문자열을 실제로 어떻게 인코딩했는지 URL 단위로 확인한다.
      let capturedUrl: URL | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/score-distribution", ({ request }) => {
          capturedUrl = new URL(request.url);
          return HttpResponse.json(mockDistribution);
        })
      );
      await reviewPolicyService.getScoreDistribution({ categoryId: "cat-1", days: 14 });
      expect(capturedUrl?.pathname).toBe("/api/admin/review-items/score-distribution");
      expect(capturedUrl?.searchParams.get("categoryId")).toBe("cat-1");
      expect(capturedUrl?.searchParams.get("days")).toBe("14");
    });

    it("categoryId 없이 days 만 전달해도 동작한다", async () => {
      let capturedUrl: URL | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/score-distribution", ({ request }) => {
          capturedUrl = new URL(request.url);
          return HttpResponse.json(mockDistribution);
        })
      );
      await reviewPolicyService.getScoreDistribution({ days: 7 });
      expect(capturedUrl?.searchParams.get("categoryId")).toBeNull();
      expect(capturedUrl?.searchParams.get("days")).toBe("7");
    });

    it("params 를 아예 넘기지 않으면 쿼리스트링 없이 호출한다", async () => {
      // 기본값(서버 측 days=7) 을 타도록 쿼리 파라미터가 전혀 붙지 않아야 한다.
      let capturedSearch: string | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/score-distribution", ({ request }) => {
          capturedSearch = new URL(request.url).search;
          return HttpResponse.json(mockDistribution);
        })
      );
      await reviewPolicyService.getScoreDistribution();
      expect(capturedSearch).toBe("");
    });

    it("params 가 빈 객체여도 쿼리스트링 없이 호출한다", async () => {
      let capturedSearch: string | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/score-distribution", ({ request }) => {
          capturedSearch = new URL(request.url).search;
          return HttpResponse.json(mockDistribution);
        })
      );
      await reviewPolicyService.getScoreDistribution({});
      expect(capturedSearch).toBe("");
    });

    it("days=0 도 쿼리에 포함된다 (undefined 만 제외)", async () => {
      // `days !== undefined` 규칙 검증 — 0 은 유효한 값이므로 포함되어야 한다.
      let capturedUrl: URL | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/score-distribution", ({ request }) => {
          capturedUrl = new URL(request.url);
          return HttpResponse.json(mockDistribution);
        })
      );
      await reviewPolicyService.getScoreDistribution({ days: 0 });
      expect(capturedUrl?.searchParams.get("days")).toBe("0");
    });

    it("응답 JSON 을 ScoreDistribution 타입으로 반환한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/review-items/score-distribution", () =>
          HttpResponse.json(mockDistribution)
        )
      );
      const result = await reviewPolicyService.getScoreDistribution({ days: 7 });
      expect(result).toEqual(mockDistribution);
      expect(result.buckets).toHaveLength(10);
      expect(result.totalCount).toBe(46);
      expect(result.medianScore).toBe(0.55);
      expect(result.meanScore).toBe(0.58);
    });

    it("서버 오류 시 에러를 throw 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/review-items/score-distribution", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(
        reviewPolicyService.getScoreDistribution({ days: 7 })
      ).rejects.toThrow();
    });
  });
});
