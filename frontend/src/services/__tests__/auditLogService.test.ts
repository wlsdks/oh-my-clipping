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

import { auditLogService } from "@/services/auditLogService";
import type { AuditLogPage, AuditLogFilters } from "@/types/auditLog";

const mockAuditLogPage: AuditLogPage = {
  content: [
    {
      id: 1,
      actorId: "admin",
      actorName: "관리자",
      action: "SOURCE_CREATED",
      targetType: "SOURCE",
      targetId: "src-1",
      targetName: "TechCrunch",
      detail: null,
      createdAt: "2026-04-11T09:00:00Z"
    }
  ],
  totalCount: 1,
  page: 0,
  size: 20
};

const mockFilters: AuditLogFilters = {
  actions: ["SOURCE_CREATED", "SOURCE_DELETED", "CATEGORY_UPDATED"],
  targetTypes: ["SOURCE", "CATEGORY", "PERSONA"]
};

const handlers = [
  // getAll
  http.get("http://localhost/api/admin/audit-log", () =>
    HttpResponse.json(mockAuditLogPage)
  ),

  // getFilters
  http.get("http://localhost/api/admin/audit-log/filters", () =>
    HttpResponse.json(mockFilters)
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("auditLogService", () => {
  describe("감사 로그 조회", () => {
    it("getAll은 감사 로그 페이지네이션을 반환해야 한다", async () => {
      const result = await auditLogService.getAll();
      expect(result.content).toHaveLength(1);
      expect(result.content[0].id).toBe(1);
      expect(result.content[0].action).toBe("SOURCE_CREATED");
      expect(result.content[0].actorName).toBe("관리자");
      expect(result.totalCount).toBe(1);
    });

    it("getAll은 URLSearchParams 없이 호출하면 쿼리스트링 없이 요청해야 한다", async () => {
      let capturedUrl: URL | undefined;
      server.use(
        http.get("http://localhost/api/admin/audit-log", ({ request }) => {
          capturedUrl = new URL(request.url);
          return HttpResponse.json(mockAuditLogPage);
        })
      );

      const result = await auditLogService.getAll();

      expect(capturedUrl?.search).toBe("");
      expect(result.content).toHaveLength(1);
    });

    it("getAll은 URLSearchParams의 필터 조건을 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/audit-log", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockAuditLogPage);
        })
      );

      const params = new URLSearchParams({ action: "SOURCE_CREATED", page: "0", size: "20" });
      const result = await auditLogService.getAll(params);

      expect(capturedSearch?.get("action")).toBe("SOURCE_CREATED");
      expect(capturedSearch?.get("page")).toBe("0");
      expect(capturedSearch?.get("size")).toBe("20");
      expect(Array.isArray(result.content)).toBe(true);
    });

    it("getAll의 페이지 정보가 올바르게 반환되어야 한다", async () => {
      const result = await auditLogService.getAll();
      expect(result.page).toBe(0);
      expect(result.size).toBe(20);
    });
  });

  describe("필터 목록 조회", () => {
    it("getFilters는 액션과 대상 유형 목록을 반환해야 한다", async () => {
      const result = await auditLogService.getFilters();
      expect(result.actions).toContain("SOURCE_CREATED");
      expect(result.actions).toContain("SOURCE_DELETED");
      expect(result.targetTypes).toContain("SOURCE");
      expect(result.targetTypes).toContain("CATEGORY");
    });

    it("getFilters의 결과가 배열 형태여야 한다", async () => {
      const result = await auditLogService.getFilters();
      expect(Array.isArray(result.actions)).toBe(true);
      expect(Array.isArray(result.targetTypes)).toBe(true);
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/audit-log", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(auditLogService.getAll()).rejects.toThrow();
    });

    it("401 응답 시 authStore.logout이 호출되어야 한다", async () => {
      const { authStore } = await import("@/store/authStore");
      const logoutMock = vi.fn();
      (authStore.getState as ReturnType<typeof vi.fn>).mockReturnValue({ logout: logoutMock });

      server.use(
        http.get("http://localhost/api/admin/audit-log/filters", () =>
          new HttpResponse(null, { status: 401 })
        )
      );

      await auditLogService.getFilters().catch(() => {});
      expect(logoutMock).toHaveBeenCalled();
    });
  });
});
