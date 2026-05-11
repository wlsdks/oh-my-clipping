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

import { companyService } from "@/services/companyService";
import type { CompanySearchResult } from "@/services/companyService";

const mockCompanies: CompanySearchResult[] = [
  { corpCode: "001", corpName: "MegaCorp", stockCode: "999930" },
  { corpCode: "002", corpName: "ConglomerateCo", stockCode: "066570" }
];

const handlers = [
  http.get("http://localhost/api/admin/companies", ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get("q");
    if (!q) return HttpResponse.json([], { status: 400 });
    return HttpResponse.json(mockCompanies);
  }),

  http.get("http://localhost/api/user/companies", ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get("q");
    if (!q) return HttpResponse.json([], { status: 400 });
    return HttpResponse.json(mockCompanies);
  })
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("companyService", () => {
  describe("searchAdminCompanies", () => {
    it("검색어를 전달하여 회사 목록을 반환해야 한다", async () => {
      const result = await companyService.searchAdminCompanies("MegaCorp");
      expect(result).toEqual(mockCompanies);
      expect(result).toHaveLength(2);
    });

    it("회사 결과에 corpCode, corpName, stockCode 필드가 있어야 한다", async () => {
      const result = await companyService.searchAdminCompanies("TestCorp");
      expect(result[0]).toHaveProperty("corpCode");
      expect(result[0]).toHaveProperty("corpName");
      expect(result[0]).toHaveProperty("stockCode");
    });

    it("q와 limit을 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/companies", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockCompanies);
        })
      );

      await companyService.searchAdminCompanies("전자", 5);

      expect(capturedSearch?.get("q")).toBe("전자");
      expect(capturedSearch?.get("limit")).toBe("5");
    });
  });

  describe("searchUserCompanies", () => {
    it("사용자 API에서 회사 목록을 반환해야 한다", async () => {
      const result = await companyService.searchUserCompanies("MegaCorp");
      expect(result).toEqual(mockCompanies);
      expect(result).toHaveLength(2);
    });

    it("q와 limit을 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/user/companies", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockCompanies);
        })
      );

      await companyService.searchUserCompanies("TestCorp", 10);

      expect(capturedSearch?.get("q")).toBe("TestCorp");
      expect(capturedSearch?.get("limit")).toBe("10");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/companies", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(companyService.searchAdminCompanies("MegaCorp")).rejects.toThrow();
    });
  });
});
