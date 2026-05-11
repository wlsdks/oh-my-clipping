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

import { sourceHealthService } from "@/services/sourceHealthService";
import type { SourceHealthResponse } from "@/types/sourceHealth";

const mockHealthy: SourceHealthResponse = {
  totalCount: 15,
  healthyCount: 15,
  unhealthy: []
};

const mockUnhealthy: SourceHealthResponse = {
  totalCount: 15,
  healthyCount: 12,
  unhealthy: [
    {
      id: "src-1",
      name: "불량 피드",
      lastSuccessAt: null,
      crawlFailCount: 3,
      reason: "Connection timeout"
    },
    {
      id: "src-2",
      name: "오류 피드",
      lastSuccessAt: null,
      crawlFailCount: 5,
      reason: "HTTP 404"
    },
    {
      id: "src-3",
      name: "파싱 오류 피드",
      lastSuccessAt: null,
      crawlFailCount: 2,
      reason: "Feed parsing failed"
    }
  ]
};

const handlers = [
  http.get("http://localhost/api/admin/sources/health", () =>
    HttpResponse.json(mockHealthy)
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("sourceHealthService", () => {
  describe("getHealth", () => {
    it("소스 헬스 현황을 반환해야 한다", async () => {
      const result = await sourceHealthService.getHealth();
      expect(result.totalCount).toBe(15);
      expect(result.healthyCount).toBe(15);
      expect(result.unhealthy).toHaveLength(0);
    });

    it("응답에 totalCount, healthyCount, unhealthy 필드가 있어야 한다", async () => {
      const result = await sourceHealthService.getHealth();
      expect(result).toHaveProperty("totalCount");
      expect(result).toHaveProperty("healthyCount");
      expect(result).toHaveProperty("unhealthy");
    });

    it("비정상 소스 목록을 포함하여 반환해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/sources/health", () =>
          HttpResponse.json(mockUnhealthy)
        )
      );
      const result = await sourceHealthService.getHealth();
      expect(result.totalCount).toBe(15);
      expect(result.healthyCount).toBe(12);
      expect(result.unhealthy).toHaveLength(3);
      expect(result.unhealthy[0].name).toBe("불량 피드");
      expect(result.unhealthy[0].crawlFailCount).toBe(3);
    });

    it("전체 소스가 정상일 때 unhealthy 배열이 비어야 한다", async () => {
      const result = await sourceHealthService.getHealth();
      expect(result.unhealthy).toHaveLength(0);
      expect(result.totalCount).toBe(result.healthyCount);
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/sources/health", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(sourceHealthService.getHealth()).rejects.toThrow();
    });
  });
});
