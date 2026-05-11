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

import { categoryRuleService } from "@/services/categoryRuleService";
import type { AutoExcludedResponse, CategoryRule, RestoreFromAutoExcludeResult } from "@/types/autoExcludedItem";
import type { RuleDryRunResult } from "@/types/categoryRule";

const mockDryRunResult: RuleDryRunResult = {
  analyzedCount: 120,
  wouldAutoExclude: 8,
  wouldStayUnchanged: 112,
  samples: [
    {
      summaryId: "sum-1",
      title: "시장 전망",
      eventType: "OPINION",
      score: 0.12,
      reason: "event_type_blacklist"
    },
    {
      summaryId: "sum-2",
      title: "임원 인터뷰",
      eventType: null,
      score: 0.0,
      reason: "zero_signal"
    }
  ]
};

const mockAutoExcludedResponse: AutoExcludedResponse = {
  items: [
    {
      summaryId: "sum-10",
      title: "자동 제외된 기사 1",
      originalTitle: "Auto-excluded article 1",
      translatedTitle: "자동 제외된 기사 1",
      categoryId: "cat-1",
      categoryName: "AI/테크",
      score: 0.1,
      reason: "rule:event_type_blacklist",
      excludedAt: "2026-04-18T09:00:00Z",
      summary: "해당 기사는 블랙리스트 이벤트 타입으로 자동 제외되었습니다.",
      sourceUrl: "https://example.com/article/1",
      sourceName: "예시 뉴스",
      publishedAt: "2026-04-18T08:00:00Z",
      eventType: "OPINION",
      sentiment: "NEUTRAL"
    }
  ],
  totalCount: 3,
  reasonBreakdown: {
    "rule:event_type_blacklist": 2,
    "rule:zero_signal": 1
  }
};

const mockRestoreResult: RestoreFromAutoExcludeResult = {
  summaryId: "sum-10",
  newStatus: "REVIEW"
};

const mockCategoryRule: CategoryRule = {
  categoryId: "cat-1",
  includeKeywords: ["AI", "머신러닝"],
  excludeKeywords: ["광고"],
  riskTags: [],
  excludeEventTypes: ["OPINION", "ADVERTISEMENT"],
  includeThreshold: 0.6,
  reviewThreshold: 0.4,
  uncertainToReview: true,
  autoExcludeEnabled: true,
  revision: 5,
  updatedBy: "admin",
  updatedAt: "2026-04-01T00:00:00Z"
};

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("categoryRuleService", () => {
  describe("getById", () => {
    it("/admin/category-rules/:categoryId 에 GET 하고 파싱된 CategoryRule 을 반환한다", async () => {
      // 드로어의 룰 근거 섹션에서 excludeEventTypes 와 includeKeywords 를 렌더하려면
      // 서버가 반환한 JSON 이 CategoryRule 타입으로 손상 없이 전달돼야 한다.
      let capturedPath: string | undefined;
      let capturedMethod: string | undefined;
      server.use(
        http.get("http://localhost/api/admin/category-rules/cat-1", ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          capturedMethod = request.method;
          return HttpResponse.json(mockCategoryRule);
        })
      );
      const result = await categoryRuleService.getById("cat-1");
      expect(capturedPath).toBe("/api/admin/category-rules/cat-1");
      expect(capturedMethod).toBe("GET");
      // 드로어가 사용하는 두 필드가 실제로 응답에 들어있는지 명시적으로 단언.
      expect(result.categoryId).toBe("cat-1");
      expect(result.excludeEventTypes).toEqual(["OPINION", "ADVERTISEMENT"]);
      expect(result.includeKeywords).toEqual(["AI", "머신러닝"]);
      expect(result.reviewThreshold).toBe(0.4);
    });

    it("categoryId 에 URL 예약 문자가 있으면 퍼센트 인코딩해서 요청한다", async () => {
      // path variable 에 "/" 나 공백이 들어와도 백엔드 라우팅이 깨지지 않아야 한다.
      let capturedPath: string | undefined;
      server.use(
        http.get("http://localhost/api/admin/category-rules/:encoded", ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          return HttpResponse.json(mockCategoryRule);
        })
      );
      await categoryRuleService.getById("cat/with slash");
      expect(capturedPath).toBe("/api/admin/category-rules/cat%2Fwith%20slash");
    });

    it("서버 오류(500) 시 에러를 throw 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/category-rules/cat-1", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(categoryRuleService.getById("cat-1")).rejects.toThrow();
    });
  });

  describe("dryRun", () => {
    it("/admin/category-rules/:categoryId/dry-run 에 POST 하고 body 를 그대로 전달한다", async () => {
      // 실제 요청 URL, method, JSON body 를 모두 캡처해서 계약을 검증한다.
      let capturedUrl: URL | undefined;
      let capturedBody: unknown;
      server.use(
        http.post("http://localhost/api/admin/category-rules/cat-1/dry-run", async ({ request }) => {
          capturedUrl = new URL(request.url);
          capturedBody = await request.json();
          return HttpResponse.json(mockDryRunResult);
        })
      );
      const result = await categoryRuleService.dryRun("cat-1", {
        excludeEventTypes: ["OPINION", "ADVERTISEMENT"],
        days: 14,
        maxSamples: 10
      });
      expect(capturedUrl?.pathname).toBe("/api/admin/category-rules/cat-1/dry-run");
      expect(capturedBody).toEqual({
        excludeEventTypes: ["OPINION", "ADVERTISEMENT"],
        days: 14,
        maxSamples: 10
      });
      // 응답 JSON 이 RuleDryRunResult 형태로 타입 안정하게 반환되는지 강하게 단언
      expect(result.analyzedCount).toBe(120);
      expect(result.wouldAutoExclude).toBe(8);
      expect(result.wouldStayUnchanged).toBe(112);
      expect(result.samples).toHaveLength(2);
      expect(result.samples[0].reason).toBe("event_type_blacklist");
      expect(result.samples[1].eventType).toBeNull();
    });

    it("excludeEventTypes 빈 배열 + days/maxSamples 생략 시 body 에 undefined 키가 직렬화되지 않는다", async () => {
      // 룰 비활성 시뮬레이션 케이스 — 서버 기본값으로 넘어가도록 body 에 days/maxSamples 가 안 들어가야 한다.
      let capturedBody: Record<string, unknown> | undefined;
      server.use(
        http.post("http://localhost/api/admin/category-rules/cat-2/dry-run", async ({ request }) => {
          capturedBody = (await request.json()) as Record<string, unknown>;
          return HttpResponse.json(mockDryRunResult);
        })
      );
      await categoryRuleService.dryRun("cat-2", { excludeEventTypes: [] });
      expect(capturedBody).toEqual({ excludeEventTypes: [] });
      expect("days" in (capturedBody ?? {})).toBe(false);
      expect("maxSamples" in (capturedBody ?? {})).toBe(false);
    });

    it("categoryId 에 URL 예약 문자가 있으면 퍼센트 인코딩해서 요청한다", async () => {
      // path variable 에 "/" 나 한글이 들어와도 백엔드 라우팅을 깨지 않아야 한다.
      let capturedPath: string | undefined;
      server.use(
        http.post("http://localhost/api/admin/category-rules/:encoded/dry-run", ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          return HttpResponse.json(mockDryRunResult);
        })
      );
      await categoryRuleService.dryRun("a/b c", { excludeEventTypes: [] });
      expect(capturedPath).toBe("/api/admin/category-rules/a%2Fb%20c/dry-run");
    });

    it("서버 오류(500) 시 에러를 throw 한다", async () => {
      server.use(
        http.post("http://localhost/api/admin/category-rules/cat-1/dry-run", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(categoryRuleService.dryRun("cat-1", { excludeEventTypes: [] })).rejects.toThrow();
    });
  });

  describe("listAutoExcluded", () => {
    it("categoryId/reason/days/page/size 를 모두 searchParams 로 전달한다", async () => {
      let capturedUrl: URL | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/auto-excluded", ({ request }) => {
          capturedUrl = new URL(request.url);
          return HttpResponse.json(mockAutoExcludedResponse);
        })
      );
      const result = await categoryRuleService.listAutoExcluded({
        categoryId: "cat-1",
        reason: "rule:event_type_blacklist",
        days: 30,
        page: 2,
        size: 50
      });
      expect(capturedUrl?.pathname).toBe("/api/admin/review-items/auto-excluded");
      expect(capturedUrl?.searchParams.get("categoryId")).toBe("cat-1");
      expect(capturedUrl?.searchParams.get("reason")).toBe("rule:event_type_blacklist");
      expect(capturedUrl?.searchParams.get("days")).toBe("30");
      expect(capturedUrl?.searchParams.get("page")).toBe("2");
      expect(capturedUrl?.searchParams.get("size")).toBe("50");
      // 응답 구조도 강하게 단언 — totalCount/breakdown 이 타입 안정하게 매핑되는지 확인
      expect(result.totalCount).toBe(3);
      expect(result.items[0].reason).toBe("rule:event_type_blacklist");
      expect(result.reasonBreakdown["rule:event_type_blacklist"]).toBe(2);
      expect(result.reasonBreakdown["rule:zero_signal"]).toBe(1);
    });

    it("params 가 빈 객체면 쿼리스트링 없이 호출한다", async () => {
      // 서버 기본값 (days=7, page=0, size=20) 을 타도록 쿼리가 전혀 붙지 않아야 한다.
      let capturedSearch: string | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/auto-excluded", ({ request }) => {
          capturedSearch = new URL(request.url).search;
          return HttpResponse.json(mockAutoExcludedResponse);
        })
      );
      await categoryRuleService.listAutoExcluded({});
      expect(capturedSearch).toBe("");
    });

    it("categoryId 빈 문자열은 쿼리에서 제외한다 (falsy 처리)", async () => {
      // 드롭다운 "전체" 선택 시 빈 문자열이 들어올 수 있으므로 서버에 빈 값이 넘어가지 않아야 한다.
      let capturedUrl: URL | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/auto-excluded", ({ request }) => {
          capturedUrl = new URL(request.url);
          return HttpResponse.json(mockAutoExcludedResponse);
        })
      );
      await categoryRuleService.listAutoExcluded({ categoryId: "", days: 7 });
      expect(capturedUrl?.searchParams.get("categoryId")).toBeNull();
      expect(capturedUrl?.searchParams.get("days")).toBe("7");
    });

    it("page=0 / size=0 도 쿼리에 포함된다 (undefined 만 제외)", async () => {
      // 0 은 유효한 값. undefined 만 걸러내는 로직을 회귀 방지로 명시.
      let capturedUrl: URL | undefined;
      server.use(
        http.get("http://localhost/api/admin/review-items/auto-excluded", ({ request }) => {
          capturedUrl = new URL(request.url);
          return HttpResponse.json(mockAutoExcludedResponse);
        })
      );
      await categoryRuleService.listAutoExcluded({ page: 0, size: 0, days: 0 });
      expect(capturedUrl?.searchParams.get("page")).toBe("0");
      expect(capturedUrl?.searchParams.get("size")).toBe("0");
      expect(capturedUrl?.searchParams.get("days")).toBe("0");
    });

    it("서버 오류 시 에러를 throw 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/review-items/auto-excluded", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(categoryRuleService.listAutoExcluded({})).rejects.toThrow();
    });
  });

  describe("restoreFromAutoExclude", () => {
    it("/admin/review-items/:summaryId/restore-to-review 에 POST 한다", async () => {
      // URL path variable 로 summaryId 가 바르게 들어가는지 확인.
      let capturedPath: string | undefined;
      let capturedMethod: string | undefined;
      server.use(
        http.post("http://localhost/api/admin/review-items/sum-10/restore-to-review", ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          capturedMethod = request.method;
          return HttpResponse.json(mockRestoreResult);
        })
      );
      const result = await categoryRuleService.restoreFromAutoExclude("sum-10");
      expect(capturedPath).toBe("/api/admin/review-items/sum-10/restore-to-review");
      expect(capturedMethod).toBe("POST");
      expect(result).toEqual({ summaryId: "sum-10", newStatus: "REVIEW" });
    });

    it("summaryId 에 특수문자가 있으면 URL 인코딩해서 요청한다", async () => {
      let capturedPath: string | undefined;
      server.use(
        http.post("http://localhost/api/admin/review-items/:encoded/restore-to-review", ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          return HttpResponse.json(mockRestoreResult);
        })
      );
      await categoryRuleService.restoreFromAutoExclude("id/with space");
      expect(capturedPath).toBe("/api/admin/review-items/id%2Fwith%20space/restore-to-review");
    });

    it("정책 자동 제외가 아닌 항목이면 409 를 throw 한다", async () => {
      // 백엔드가 ConflictException 을 던지면 ky 는 HTTPError 로 전파.
      server.use(
        http.post("http://localhost/api/admin/review-items/sum-99/restore-to-review", () =>
          HttpResponse.json({ message: "Not a policy-auto EXCLUDE item" }, { status: 409 })
        )
      );
      await expect(categoryRuleService.restoreFromAutoExclude("sum-99")).rejects.toThrow();
    });

    it("존재하지 않는 summary 면 404 를 throw 한다", async () => {
      server.use(
        http.post("http://localhost/api/admin/review-items/missing/restore-to-review", () =>
          HttpResponse.json({ message: "Review item not found" }, { status: 404 })
        )
      );
      await expect(categoryRuleService.restoreFromAutoExclude("missing")).rejects.toThrow();
    });
  });
});
