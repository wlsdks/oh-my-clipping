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

import { deliveryService } from "@/services/deliveryService";
import type { DeliverySummary, DeliveryLogsPage } from "@/types/delivery";

const mockSummary: DeliverySummary = {
  totalCount: 120,
  sentCount: 115,
  failedCount: 5,
  skippedCount: 0,
  successRate: 0.958
};

const mockLogsPage: DeliveryLogsPage = {
  content: [
    {
      id: "log-1",
      categoryId: "cat-1",
      channelId: "C12345",
      deliveryDate: "2026-04-11",
      deliveryHour: 9,
      status: "SENT",
      itemCount: 5,
      slackMessageTs: "1234567890.000001",
      retryAttempted: false,
      createdAt: "2026-04-11T09:00:00Z",
      updatedAt: "2026-04-11T09:00:10Z"
    }
  ],
  totalCount: 1,
  page: 0,
  size: 20
};

const handlers = [
  // getSummary
  http.get("http://localhost/api/admin/delivery/summary", () =>
    HttpResponse.json(mockSummary)
  ),

  // listLogs
  http.get("http://localhost/api/admin/delivery/logs", () =>
    HttpResponse.json(mockLogsPage)
  ),

  // retry
  http.post("http://localhost/api/admin/delivery/log-1/retry", () =>
    HttpResponse.json({ success: true, logId: "log-1" })
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("deliveryService", () => {
  describe("발송 요약 통계 조회", () => {
    it("getSummary는 발송 요약을 반환해야 한다", async () => {
      const result = await deliveryService.getSummary();
      expect(result.totalCount).toBe(120);
      expect(result.sentCount).toBe(115);
      expect(result.failedCount).toBe(5);
      expect(result.skippedCount).toBe(0);
    });

    it("getSummary는 특정 날짜를 date 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/delivery/summary", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockSummary);
        })
      );

      const result = await deliveryService.getSummary("2026-04-10");

      expect(capturedSearch?.get("date")).toBe("2026-04-10");
      expect(result.totalCount).toBe(120);
    });

    it("getSummary의 successRate가 올바르게 반환되어야 한다", async () => {
      const result = await deliveryService.getSummary();
      expect(result.successRate).toBeGreaterThan(0);
      expect(result.successRate).toBeLessThanOrEqual(1);
    });
  });

  describe("발송 이력 조회", () => {
    it("listLogs는 발송 이력 페이지네이션을 반환해야 한다", async () => {
      const result = await deliveryService.listLogs();
      expect(result.content).toHaveLength(1);
      expect(result.content[0].id).toBe("log-1");
      expect(result.content[0].status).toBe("SENT");
    });

    it("listLogs는 URLSearchParams의 필터 조건을 쿼리스트링에 포함해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/delivery/logs", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockLogsPage);
        })
      );

      const params = new URLSearchParams({ categoryId: "cat-1", status: "FAILED" });
      const result = await deliveryService.listLogs(params);

      expect(capturedSearch?.get("categoryId")).toBe("cat-1");
      expect(capturedSearch?.get("status")).toBe("FAILED");
      expect(result.content).toHaveLength(1);
    });

    it("listLogs는 within 파라미터를 쿼리스트링에 추가해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/delivery/logs", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json(mockLogsPage);
        })
      );

      await deliveryService.listLogs(undefined, "7d");

      expect(capturedSearch?.get("within")).toBe("7d");
    });
  });

  describe("실패 발송 재시도", () => {
    it("retry는 재발송 성공 결과를 반환해야 한다", async () => {
      const result = await deliveryService.retry("log-1");
      expect(result.success).toBe(true);
      expect(result.logId).toBe("log-1");
    });

    it("retry 실패 시 에러를 throw해야 한다", async () => {
      server.use(
        http.post("http://localhost/api/admin/delivery/not-found/retry", () =>
          HttpResponse.json({ message: "로그를 찾을 수 없음" }, { status: 404 })
        )
      );
      await expect(deliveryService.retry("not-found")).rejects.toThrow();
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/delivery/summary", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(deliveryService.getSummary()).rejects.toThrow();
    });
  });
});
