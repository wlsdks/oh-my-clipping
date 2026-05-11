// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse } from "msw";

vi.mock("@/store/authStore", () => ({
  authStore: { getState: vi.fn(() => ({ logout: vi.fn() })) },
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
          },
        ],
      },
    }),
  };
});

import {
  backfillService,
  type BackfillPreviewResponse,
  type BackfillApplyResponse,
} from "@/services/backfillService";

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

/** 기본 BackfillPreviewResponse 픽스처 */
function makePreviewResponse(
  overrides: Partial<BackfillPreviewResponse> = {},
): BackfillPreviewResponse {
  return {
    candidates: [
      {
        sourceId: "src-1",
        sourceUrl: "https://example.com/feed",
        sourceName: "Example Feed",
        categoryId: "cat-1",
        categoryName: "기술",
        matchedCompanyName: "MegaCorp",
        stockCode: "999930",
        confidence: "high",
        precision: 0.95,
      },
    ],
    total: 1,
    byConfidence: { high: 1, medium: 0, low: 0 },
    ...overrides,
  };
}

/** 기본 BackfillApplyResponse 픽스처 */
function makeApplyResponse(
  overrides: Partial<BackfillApplyResponse> = {},
): BackfillApplyResponse {
  return {
    total: 1,
    succeeded: 1,
    failed: 0,
    errors: [],
    affectedCategoryIds: ["cat-1"],
    ...overrides,
  };
}

describe("backfillService", () => {
  describe("preview", () => {
    it("confidence=high 기본 필터로 GET /admin/organizations/backfill/preview 를 호출하고 응답을 반환한다", async () => {
      // 실제 요청 method + pathname 을 캡처해서 GET 계약을 검증한다.
      let capturedMethod: string | undefined;
      let capturedUrl: string | undefined;
      const mockResponse = makePreviewResponse();

      server.use(
        http.get("http://localhost/api/admin/organizations/backfill/preview", ({ request }) => {
          capturedMethod = request.method;
          capturedUrl = request.url;
          return HttpResponse.json(mockResponse);
        }),
      );

      const result = await backfillService.preview({ confidence: "high" });

      expect(capturedMethod).toBe("GET");
      // confidence 파라미터가 포함돼야 한다
      expect(capturedUrl).toContain("confidence=high");
      // 응답 형태 검증
      expect(result.total).toBe(1);
      expect(result.candidates).toHaveLength(1);
      expect(result.candidates[0].sourceId).toBe("src-1");
      expect(result.byConfidence).toEqual({ high: 1, medium: 0, low: 0 });
    });

    it("includeMedium=true 이면 URL 에 includeMedium=true 파라미터가 추가된다", async () => {
      let capturedUrl: string | undefined;

      server.use(
        http.get("http://localhost/api/admin/organizations/backfill/preview", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(makePreviewResponse());
        }),
      );

      await backfillService.preview({ confidence: "high", includeMedium: true });

      // includeMedium 파라미터가 URL 에 포함되어야 한다
      expect(capturedUrl).toContain("includeMedium=true");
    });

    it("includeMedium=false 또는 미설정 시 URL 에 includeMedium 파라미터를 붙이지 않는다", async () => {
      let capturedUrl: string | undefined;

      server.use(
        http.get("http://localhost/api/admin/organizations/backfill/preview", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(makePreviewResponse());
        }),
      );

      await backfillService.preview({ confidence: "high", includeMedium: false });

      // includeMedium 이 false 이면 파라미터를 생략한다
      expect(capturedUrl).not.toContain("includeMedium");
    });

    it("categoryId 에 URL 예약 문자 (슬래시, 공백) 가 있으면 URLSearchParams 가 인코딩해서 전달한다", async () => {
      let capturedUrl: string | undefined;

      server.use(
        http.get("http://localhost/api/admin/organizations/backfill/preview", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(makePreviewResponse());
        }),
      );

      await backfillService.preview({ confidence: "high", categoryId: "cat/1 with space" });

      // URLSearchParams 의 인코딩 — "/" → %2F, " " → "+" 또는 "%20"
      // 두 인코딩 형식 중 하나를 수용한다
      const urlObj = new URL(capturedUrl!);
      const decoded = urlObj.searchParams.get("categoryId");
      expect(decoded).toBe("cat/1 with space");
      // raw URL 에는 반드시 인코딩 형태가 포함돼야 한다 (공백은 그대로 오면 안 됨)
      expect(capturedUrl).toContain("cat");
      expect(capturedUrl).not.toMatch(/categoryId=cat\/1 with space/); // 비인코딩 금지
    });

    it("빈 categoryId 는 파라미터로 전달하지 않는다", async () => {
      let capturedUrl: string | undefined;

      server.use(
        http.get("http://localhost/api/admin/organizations/backfill/preview", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(makePreviewResponse());
        }),
      );

      await backfillService.preview({ confidence: "medium", categoryId: "" });

      // 빈 문자열이면 categoryId 파라미터 자체를 생략한다
      expect(capturedUrl).not.toContain("categoryId");
    });
  });

  describe("apply", () => {
    it("POST /admin/organizations/backfill/apply 에 candidateIds 배열을 body 로 전송한다", async () => {
      let capturedMethod: string | undefined;
      let capturedPath: string | undefined;
      let capturedBody: unknown;

      server.use(
        http.post("http://localhost/api/admin/organizations/backfill/apply", async ({ request }) => {
          capturedMethod = request.method;
          capturedPath = new URL(request.url).pathname;
          capturedBody = await request.json();
          return HttpResponse.json(makeApplyResponse());
        }),
      );

      const result = await backfillService.apply({ candidateIds: ["src-1", "src-2"] });

      expect(capturedMethod).toBe("POST");
      expect(capturedPath).toBe("/api/admin/organizations/backfill/apply");
      // body 에 candidateIds 배열이 정확히 전달돼야 한다
      expect(capturedBody).toEqual({ candidateIds: ["src-1", "src-2"] });
      // 응답 형태 검증
      expect(result.total).toBe(1);
      expect(result.succeeded).toBe(1);
      expect(result.failed).toBe(0);
      expect(result.errors).toEqual([]);
      expect(result.affectedCategoryIds).toEqual(["cat-1"]);
    });

    it("apply 응답에 errors 가 있으면 그대로 반환된다", async () => {
      server.use(
        http.post("http://localhost/api/admin/organizations/backfill/apply", async () => {
          return HttpResponse.json(
            makeApplyResponse({
              total: 2,
              succeeded: 1,
              failed: 1,
              errors: [{ candidateId: "src-bad", reason: "중복 매칭" }],
            }),
          );
        }),
      );

      const result = await backfillService.apply({ candidateIds: ["src-1", "src-bad"] });

      expect(result.failed).toBe(1);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0]).toEqual({ candidateId: "src-bad", reason: "중복 매칭" });
    });
  });
});
