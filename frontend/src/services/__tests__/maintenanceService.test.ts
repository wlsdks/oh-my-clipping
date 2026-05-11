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

import { maintenanceService } from "@/services/maintenanceService";

const handlers = [
  http.get("http://localhost/api/public/maintenance", () =>
    HttpResponse.json({ active: false, message: "" })
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("maintenanceService", () => {
  describe("getStatus", () => {
    it("점검 비활성 상태를 반환해야 한다", async () => {
      const result = await maintenanceService.getStatus();
      expect(result.active).toBe(false);
      expect(result.message).toBe("");
    });

    it("점검 활성 상태와 메시지를 반환해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/public/maintenance", () =>
          HttpResponse.json({ active: true, message: "시스템 점검 중입니다. 잠시 후 다시 시도해 주세요." })
        )
      );
      const result = await maintenanceService.getStatus();
      expect(result.active).toBe(true);
      expect(result.message).toBe("시스템 점검 중입니다. 잠시 후 다시 시도해 주세요.");
    });

    it("응답에 active, message 필드가 있어야 한다", async () => {
      const result = await maintenanceService.getStatus();
      expect(result).toHaveProperty("active");
      expect(result).toHaveProperty("message");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/public/maintenance", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(maintenanceService.getStatus()).rejects.toThrow();
    });
  });
});
