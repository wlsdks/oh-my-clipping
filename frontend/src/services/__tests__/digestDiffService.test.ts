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
  digestDiffService,
  type DigestDiffListResponse,
  type DigestDiffEntry,
} from "@/services/digestDiffService";

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

/** 기본 DigestDiffEntry 픽스처 */
function makeEntry(overrides: Partial<DigestDiffEntry> = {}): DigestDiffEntry {
  return {
    id: "ddl-1",
    categoryId: "cat-abc",
    digestDate: "2026-04-20",
    legacySummary: "Legacy text",
    newSummary: "New text",
    newMode: "DUAL_SECTION",
    sectionsCount: 2,
    articlesCount: 5,
    crossMatchCount: 1,
    createdAt: "2026-04-20T02:30:00Z",
    ...overrides,
  };
}

/** 기본 DigestDiffListResponse 픽스처 */
function makeListResponse(
  overrides: Partial<DigestDiffListResponse> = {},
): DigestDiffListResponse {
  return {
    content: [makeEntry()],
    totalElements: 1,
    page: 0,
    size: 50,
    ...overrides,
  };
}

describe("digestDiffService", () => {
  describe("list", () => {
    it("categoryId + from + to 파라미터로 GET /admin/digest-diff 를 호출하고 응답을 반환한다", async () => {
      let capturedMethod: string | undefined;
      let capturedUrl: string | undefined;
      const mockResponse = makeListResponse();

      server.use(
        http.get("http://localhost/api/admin/digest-diff", ({ request }) => {
          capturedMethod = request.method;
          capturedUrl = request.url;
          return HttpResponse.json(mockResponse);
        }),
      );

      const result = await digestDiffService.list({
        categoryId: "cat-abc",
        from: "2026-04-01",
        to: "2026-04-30",
      });

      // GET 메서드 검증
      expect(capturedMethod).toBe("GET");
      // 필수 파라미터 포함 검증
      expect(capturedUrl).toContain("categoryId=cat-abc");
      expect(capturedUrl).toContain("from=2026-04-01");
      expect(capturedUrl).toContain("to=2026-04-30");
      // 응답 형태 검증
      expect(result.totalElements).toBe(1);
      expect(result.content).toHaveLength(1);
      expect(result.content[0].id).toBe("ddl-1");
      expect(result.content[0].newMode).toBe("DUAL_SECTION");
    });

    it("categoryId 에 슬래시 등 URL 예약 문자가 있으면 URLSearchParams 가 인코딩하여 전달한다", async () => {
      let capturedUrl: string | undefined;

      server.use(
        http.get("http://localhost/api/admin/digest-diff", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(makeListResponse());
        }),
      );

      await digestDiffService.list({ categoryId: "cat/1 with space" });

      // URLSearchParams 인코딩 검증 — decoded 값이 원본과 같아야 한다
      const url = new URL(capturedUrl!);
      const decoded = url.searchParams.get("categoryId");
      expect(decoded).toBe("cat/1 with space");
      // 비인코딩 raw 문자열은 URL 에 없어야 한다
      expect(capturedUrl).not.toMatch(/categoryId=cat\/1 with space/);
    });

    it("page 와 size 파라미터를 지정하면 URL 에 포함된다", async () => {
      let capturedUrl: string | undefined;

      server.use(
        http.get("http://localhost/api/admin/digest-diff", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(makeListResponse({ page: 2, size: 20 }));
        }),
      );

      const result = await digestDiffService.list({
        categoryId: "cat-xyz",
        page: 2,
        size: 20,
      });

      expect(capturedUrl).toContain("page=2");
      expect(capturedUrl).toContain("size=20");
      // 응답 페이지 메타 검증
      expect(result.page).toBe(2);
      expect(result.size).toBe(20);
    });

    it("from/to 미지정 시 URL 에 해당 파라미터가 포함되지 않는다", async () => {
      let capturedUrl: string | undefined;

      server.use(
        http.get("http://localhost/api/admin/digest-diff", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json(makeListResponse());
        }),
      );

      await digestDiffService.list({ categoryId: "cat-abc" });

      // from/to 가 없으면 백엔드가 기본 30일 범위를 적용 — 파라미터 자체를 보내지 않는다
      expect(capturedUrl).not.toContain("from=");
      expect(capturedUrl).not.toContain("to=");
    });

    it("응답의 content 배열 각 항목이 DigestDiffEntry 타입 필드를 모두 포함한다", async () => {
      const entry = makeEntry({
        id: "ddl-full",
        crossMatchCount: 3,
        legacySummary: null,
      });

      server.use(
        http.get("http://localhost/api/admin/digest-diff", () =>
          HttpResponse.json(makeListResponse({ content: [entry], totalElements: 1 })),
        ),
      );

      const result = await digestDiffService.list({ categoryId: "cat-abc" });
      const item = result.content[0];

      expect(item.id).toBe("ddl-full");
      expect(item.categoryId).toBe("cat-abc");
      expect(item.digestDate).toBe("2026-04-20");
      expect(item.legacySummary).toBeNull();
      expect(item.newSummary).toBe("New text");
      expect(item.sectionsCount).toBe(2);
      expect(item.articlesCount).toBe(5);
      expect(item.crossMatchCount).toBe(3);
      expect(item.createdAt).toBe("2026-04-20T02:30:00Z");
    });
  });
});
