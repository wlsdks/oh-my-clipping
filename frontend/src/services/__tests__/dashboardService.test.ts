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

import { dashboardService } from "@/services/dashboardService";
import type { ClippingSetting, PipelineRunResult } from "@/types/dashboard";
import type { OpsSummary } from "@/types/ops";

const mockSettings: ClippingSetting[] = [
  {
    categoryId: "cat-1",
    categoryName: "IT/기술",
    categoryUpdatedAt: "2026-01-01T00:00:00Z",
    isActive: true,
    slackChannelId: "C123",
    maxItems: 10,
    retentionKeepDays: 30,
    retentionEnabled: true,
    retentionSource: "CATEGORY"
  }
];

const mockPipelineResult: PipelineRunResult = {
  collect: {
    totalCollected: 50,
    newItems: 40,
    duplicateSkipped: 10,
    categories: [{ categoryId: "cat-1", categoryName: "IT/기술", collected: 50, newItems: 40 }]
  },
  summarize: {
    totalSummarized: 35,
    categories: [{ categoryId: "cat-1", categoryName: "IT/기술", summarized: 35 }]
  },
  digest: {
    categoryId: "cat-1",
    categoryName: "IT/기술",
    unsentOnly: true,
    totalCandidates: 35,
    selectedCount: 10,
    postedToSlack: true,
    slackChannelId: "C123",
    slackMessageTs: "1234567890.123",
    markedSentCount: 10,
    digestText: "오늘의 뉴스 요약",
    items: []
  }
};

const mockOpsSummary: OpsSummary = {
  delivery: { total: 46, sent: 42, failed: 3 },
  pipeline: { total: 13, success: 10, failed: 2 },
};

const handlers = [
  http.get("http://localhost/api/admin/clipping/settings", () =>
    HttpResponse.json(mockSettings)
  ),
  http.post("http://localhost/api/admin/clipping/cat-1/pipeline", () =>
    HttpResponse.json(mockPipelineResult)
  ),
  http.get("http://localhost/api/admin/dashboard/ops-summary", () =>
    HttpResponse.json(mockOpsSummary)
  ),
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("dashboardService", () => {
  describe("listClippingSettings", () => {
    it("클리핑 설정 목록을 반환해야 한다", async () => {
      const result = await dashboardService.listClippingSettings();
      expect(result).toEqual(mockSettings);
      expect(result).toHaveLength(1);
      expect(result[0].categoryName).toBe("IT/기술");
    });

    it("설정에 isActive, maxItems, retentionKeepDays 필드가 있어야 한다", async () => {
      const result = await dashboardService.listClippingSettings();
      expect(result[0]).toHaveProperty("isActive");
      expect(result[0]).toHaveProperty("maxItems");
      expect(result[0]).toHaveProperty("retentionKeepDays");
    });
  });

  describe("runPipeline", () => {
    it("파이프라인 실행 결과를 반환해야 한다", async () => {
      const result = await dashboardService.runPipeline("cat-1", {
        hoursBack: 24,
        maxItems: 10,
        unsentOnly: true,
        sendToSlack: true
      });
      expect(result.collect.totalCollected).toBe(50);
      expect(result.summarize.totalSummarized).toBe(35);
      expect(result.digest.postedToSlack).toBe(true);
    });

    it("빈 body로도 파이프라인을 실행하고 categoryId를 경로에 포함해야 한다", async () => {
      let capturedBody: unknown;
      let capturedPath: string | undefined;
      server.use(
        http.post("http://localhost/api/admin/clipping/:id/pipeline", async ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          capturedBody = await request.json();
          return HttpResponse.json(mockPipelineResult);
        })
      );

      const result = await dashboardService.runPipeline("cat-1", {});

      expect(capturedPath).toBe("/api/admin/clipping/cat-1/pipeline");
      expect(capturedBody).toEqual({});
      expect(result.collect.totalCollected).toBe(50);
    });

    it("slackChannelId와 sendToSlack을 body에 담아 전송해야 한다", async () => {
      let capturedBody: unknown;
      server.use(
        http.post("http://localhost/api/admin/clipping/cat-1/pipeline", async ({ request }) => {
          capturedBody = await request.json();
          return HttpResponse.json(mockPipelineResult);
        })
      );

      await dashboardService.runPipeline("cat-1", {
        slackChannelId: "C456",
        sendToSlack: true
      });

      expect(capturedBody).toEqual({
        slackChannelId: "C456",
        sendToSlack: true
      });
    });
  });

  describe("getOpsSummary", () => {
    it("delivery와 pipeline 운영 요약을 반환해야 한다", async () => {
      const result = await dashboardService.getOpsSummary();
      expect(result.delivery.total).toBe(46);
      expect(result.delivery.sent).toBe(42);
      expect(result.delivery.failed).toBe(3);
      expect(result.pipeline.total).toBe(13);
      expect(result.pipeline.success).toBe(10);
      expect(result.pipeline.failed).toBe(2);
    });

    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/dashboard/ops-summary", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(dashboardService.getOpsSummary()).rejects.toThrow();
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/clipping/settings", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(dashboardService.listClippingSettings()).rejects.toThrow();
    });

    it("파이프라인 실행 실패 시 에러를 throw해야 한다", async () => {
      server.use(
        http.post("http://localhost/api/admin/clipping/cat-1/pipeline", () =>
          HttpResponse.json({ message: "파이프라인 오류" }, { status: 500 })
        )
      );
      await expect(dashboardService.runPipeline("cat-1", {})).rejects.toThrow();
    });
  });
});
