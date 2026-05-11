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

import { systemStatusService } from "@/services/systemStatusService";
import type { SystemStatusResponse } from "@/types/systemStatus";

const mockStatus: SystemStatusResponse = {
  server: {
    uptime: "3d 12h 30m",
    javaVersion: "21.0.2",
    activeProfiles: ["production"],
    memoryUsedMb: 512,
    memoryMaxMb: 2048
  },
  database: {
    connected: true,
    poolActive: 5,
    poolIdle: 15,
    poolTotal: 20
  },
  slack: {
    botTokenConfigured: true,
    defaultChannelId: "C12345",
    healthy: true,
    lastCheckTime: "2026-04-10T09:00:00Z"
  },
  ai: {
    circuitBreakerState: "CLOSED",
    canCall: true,
    consecutiveOpenCount: 0,
    totalOpenCount: 0,
    lastOpenedAt: null
  },
  jobQueue: {
    pendingJobs: 3,
    threshold: 100
  },
  schedulers: [
    {
      name: "digestScheduler",
      schedule: "0 9 * * 1-5",
      description: "평일 오전 9시 다이제스트 발송",
      lastRunAt: "2026-04-10T09:00:00Z",
      lastResult: "success"
    },
    {
      name: "collectScheduler",
      schedule: "0 */2 * * *",
      description: "2시간마다 RSS 수집",
      lastRunAt: null,
      lastResult: null
    }
  ]
};

const handlers = [
  http.get("http://localhost/api/admin/system/status", () =>
    HttpResponse.json(mockStatus)
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("systemStatusService", () => {
  describe("getStatus", () => {
    it("시스템 전체 상태를 반환해야 한다", async () => {
      const result = await systemStatusService.getStatus();
      expect(result).toEqual(mockStatus);
    });

    it("서버 상태 정보가 포함되어야 한다", async () => {
      const result = await systemStatusService.getStatus();
      expect(result.server.uptime).toBe("3d 12h 30m");
      expect(result.server.javaVersion).toBe("21.0.2");
      expect(result.server.activeProfiles).toContain("production");
      expect(result.server.memoryUsedMb).toBe(512);
    });

    it("데이터베이스 상태 정보가 포함되어야 한다", async () => {
      const result = await systemStatusService.getStatus();
      expect(result.database.connected).toBe(true);
      expect(result.database.poolActive).toBe(5);
      expect(result.database.poolTotal).toBe(20);
    });

    it("Slack 상태 정보가 포함되어야 한다", async () => {
      const result = await systemStatusService.getStatus();
      expect(result.slack.botTokenConfigured).toBe(true);
      expect(result.slack.defaultChannelId).toBe("C12345");
    });

    it("스케줄러 목록이 포함되어야 한다", async () => {
      const result = await systemStatusService.getStatus();
      expect(result.schedulers).toHaveLength(2);
      expect(result.schedulers[0].name).toBe("digestScheduler");
      expect(result.schedulers[1].schedule).toBe("0 */2 * * *");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/system/status", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );

      await expect(systemStatusService.getStatus()).rejects.toThrow();
    });

    it("인증 실패 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/system/status", () =>
          HttpResponse.json({ message: "인증 필요" }, { status: 401 })
        )
      );

      await expect(systemStatusService.getStatus()).rejects.toThrow();
    });
  });
});
