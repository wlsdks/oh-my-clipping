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

import {
  categoryRuleBundleService,
  type RuleBundleRequest,
} from "@/services/categoryRuleBundleService";

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

/** 기본 RuleBundleRequest — 개별 테스트에서 필요한 필드만 override 한다. */
function makeBundleRequest(overrides: Partial<RuleBundleRequest> = {}): RuleBundleRequest {
  return {
    excludeEventTypes: [],
    includeKeywords: [],
    organizationIds: [],
    accountBasedDigestEnabled: false,
    shadowModeEnabled: false,
    ...overrides,
  };
}

describe("categoryRuleBundleService", () => {
  describe("update", () => {
    it("/admin/categories/:id/rule-bundle 에 PUT 하고 200 응답 시 void 로 resolve 된다", async () => {
      // 실제 요청 method + pathname 을 캡처해서 PUT 계약을 검증한다.
      let capturedMethod: string | undefined;
      let capturedPath: string | undefined;
      server.use(
        http.put(
          "http://localhost/api/admin/categories/cat-1/rule-bundle",
          ({ request }) => {
            capturedMethod = request.method;
            capturedPath = new URL(request.url).pathname;
            return new HttpResponse(null, { status: 200 });
          },
        ),
      );

      const result = await categoryRuleBundleService.update(
        "cat-1",
        makeBundleRequest({ includeKeywords: ["AI"] }),
      );

      expect(capturedMethod).toBe("PUT");
      expect(capturedPath).toBe("/api/admin/categories/cat-1/rule-bundle");
      // 서비스 시그니처가 void 로 resolve 하는지 명시적으로 확인 (200 empty body)
      expect(result).toBeUndefined();
    });

    it("categoryId 에 URL 예약 문자 (슬래시, 공백) 가 있으면 퍼센트 인코딩해서 요청한다", async () => {
      // path variable 에 "/", " " 같은 예약 문자가 들어와도 백엔드 라우팅을 깨지 않아야 한다.
      let capturedPath: string | undefined;
      server.use(
        http.put(
          "http://localhost/api/admin/categories/:encoded/rule-bundle",
          ({ request }) => {
            capturedPath = new URL(request.url).pathname;
            return new HttpResponse(null, { status: 200 });
          },
        ),
      );

      await categoryRuleBundleService.update("cat/1 with space", makeBundleRequest());

      // encodeURIComponent 의 결과 — "/" → %2F, " " → %20
      expect(capturedPath).toBe(
        "/api/admin/categories/cat%2F1%20with%20space/rule-bundle",
      );
      expect(capturedPath).toContain("cat%2F1%20with%20space");
    });

    it("요청 body 가 RuleBundleRequest 5개 필드 모두 정확히 일치한다", async () => {
      // 백엔드 RuleBundleRequest 계약 (ADR-032) 과 일대일 매칭을 고정한다 — 필드명/타입 누락 방지.
      let capturedBody: unknown;
      server.use(
        http.put(
          "http://localhost/api/admin/categories/cat-2/rule-bundle",
          async ({ request }) => {
            capturedBody = await request.json();
            return new HttpResponse(null, { status: 200 });
          },
        ),
      );

      const body: RuleBundleRequest = {
        excludeEventTypes: ["EARNINGS", "OTHER"],
        includeKeywords: ["AI", "반도체"],
        organizationIds: ["org-1", "org-2", "org-3"],
        accountBasedDigestEnabled: true,
        shadowModeEnabled: true,
      };

      await categoryRuleBundleService.update("cat-2", body);

      // JSON 직렬화 후 서버가 받은 payload 가 보낸 값과 deep-equal 이어야 한다.
      expect(capturedBody).toEqual({
        excludeEventTypes: ["EARNINGS", "OTHER"],
        includeKeywords: ["AI", "반도체"],
        organizationIds: ["org-1", "org-2", "org-3"],
        accountBasedDigestEnabled: true,
        shadowModeEnabled: true,
      });
      // 5개 필드 외 추가 키가 직렬화되지 않았는지 확인 — 계약 초과 방지.
      expect(Object.keys(capturedBody as Record<string, unknown>).sort()).toEqual([
        "accountBasedDigestEnabled",
        "excludeEventTypes",
        "includeKeywords",
        "organizationIds",
        "shadowModeEnabled",
      ]);
    });
  });
});
