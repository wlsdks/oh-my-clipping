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

import { visualCardService } from "@/services/visualCardService";
import type { TrendSnapshot, TrendVisualCard } from "@/types/visualCard";

const mockSnapshot: TrendSnapshot = {
  id: "snap-1",
  periodType: "WEEKLY",
  snapshotFrom: "2026-01-01",
  snapshotTo: "2026-01-07",
  categoryId: "cat-1",
  categoryName: "IT/기술",
  regionType: "DOMESTIC",
  title: "주간 AI 트렌드",
  summary: "이번 주 AI 뉴스 요약",
  keySignals: ["GPT 업데이트", "반도체 시장 성장"],
  actionItems: ["AI 관련 기사 팔로업"],
  sourceCount: 15,
  itemCount: 80,
  status: "DRAFT",
  generatedBy: "admin",
  createdAt: "2026-01-08T09:00:00Z",
  updatedAt: "2026-01-08T09:00:00Z"
};

const mockPublishedSnapshot: TrendSnapshot = {
  ...mockSnapshot,
  status: "PUBLISHED",
  publishedAt: "2026-01-08T10:00:00Z"
};

const mockVisualCard: TrendVisualCard = {
  id: "card-1",
  snapshotId: "snap-1",
  cardType: "INFO_CARD",
  title: "AI 트렌드 카드",
  summary: "이번 주 AI 뉴스",
  panels: ["panel1", "panel2"],
  reviewStatus: "PENDING",
  generatedBy: "admin",
  published: false,
  createdAt: "2026-01-08T09:30:00Z",
  updatedAt: "2026-01-08T09:30:00Z"
};

const mockApprovedCard: TrendVisualCard = {
  ...mockVisualCard,
  reviewStatus: "APPROVED",
  reviewedBy: "admin",
  reviewedAt: "2026-01-08T10:00:00Z"
};

const handlers = [
  http.post("http://localhost/api/admin/trend-snapshots/run", () =>
    HttpResponse.json(mockSnapshot)
  ),
  http.get("http://localhost/api/admin/trend-snapshots", () =>
    HttpResponse.json([mockSnapshot])
  ),
  http.post("http://localhost/api/admin/trend-snapshots/snap-1/publish", () =>
    HttpResponse.json(mockPublishedSnapshot)
  ),
  http.post("http://localhost/api/admin/trend-snapshots/snap-1/generate-visual", () =>
    HttpResponse.json(mockVisualCard)
  ),
  http.get("http://localhost/api/admin/trend-snapshots/snap-1/visuals", () =>
    HttpResponse.json([mockVisualCard])
  ),
  http.get("http://localhost/api/admin/visual-cards", () =>
    HttpResponse.json([mockVisualCard])
  ),
  http.post("http://localhost/api/admin/visual-cards/card-1/review", () =>
    HttpResponse.json(mockApprovedCard)
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("visualCardService", () => {
  describe("runTrendSnapshot", () => {
    it("트렌드 스냅샷을 생성하고 반환해야 한다", async () => {
      const result = await visualCardService.runTrendSnapshot({
        periodType: "WEEKLY",
        categoryId: "cat-1",
        regionType: "DOMESTIC"
      });
      expect(result.id).toBe("snap-1");
      expect(result.periodType).toBe("WEEKLY");
      expect(result.status).toBe("DRAFT");
    });

    it("MONTHLY 타입을 body에 포함해 POST 요청을 보내야 한다", async () => {
      let capturedBody: unknown;
      server.use(
        http.post("http://localhost/api/admin/trend-snapshots/run", async ({ request }) => {
          capturedBody = await request.json();
          return HttpResponse.json({ ...mockSnapshot, periodType: "MONTHLY" });
        })
      );

      const result = await visualCardService.runTrendSnapshot({
        periodType: "MONTHLY"
      });

      expect(capturedBody).toEqual({ periodType: "MONTHLY" });
      expect(result.periodType).toBe("MONTHLY");
    });
  });

  describe("listTrendSnapshots", () => {
    it("트렌드 스냅샷 목록을 반환해야 한다", async () => {
      const result = await visualCardService.listTrendSnapshots();
      expect(result).toHaveLength(1);
      expect(result[0].title).toBe("주간 AI 트렌드");
      expect(result[0].keySignals).toContain("GPT 업데이트");
    });

    it("periodType, categoryId, status, limit을 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/trend-snapshots", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json([mockSnapshot]);
        })
      );

      await visualCardService.listTrendSnapshots({
        periodType: "WEEKLY",
        categoryId: "cat-1",
        status: "DRAFT",
        limit: 10
      });

      expect(capturedSearch?.get("periodType")).toBe("WEEKLY");
      expect(capturedSearch?.get("categoryId")).toBe("cat-1");
      expect(capturedSearch?.get("status")).toBe("DRAFT");
      expect(capturedSearch?.get("limit")).toBe("10");
    });
  });

  describe("publishTrendSnapshot", () => {
    it("스냅샷을 발행하고 publishedBy를 body로 전송하며 PUBLISHED 상태를 반환해야 한다", async () => {
      let capturedBody: unknown;
      server.use(
        http.post("http://localhost/api/admin/trend-snapshots/snap-1/publish", async ({ request }) => {
          capturedBody = await request.json();
          return HttpResponse.json(mockPublishedSnapshot);
        })
      );

      const result = await visualCardService.publishTrendSnapshot("snap-1", "admin");

      expect(capturedBody).toEqual({ publishedBy: "admin" });
      expect(result.status).toBe("PUBLISHED");
      expect(result.publishedAt).toBe("2026-01-08T10:00:00Z");
    });

    it("publishedBy 없이 호출하면 null을 body에 담아 전송해야 한다", async () => {
      let capturedBody: unknown;
      server.use(
        http.post("http://localhost/api/admin/trend-snapshots/snap-1/publish", async ({ request }) => {
          capturedBody = await request.json();
          return HttpResponse.json(mockPublishedSnapshot);
        })
      );

      const result = await visualCardService.publishTrendSnapshot("snap-1");

      expect(capturedBody).toEqual({ publishedBy: null });
      expect(result.status).toBe("PUBLISHED");
    });
  });

  describe("generateTrendVisual", () => {
    it("비주얼 카드를 생성하고 반환해야 한다", async () => {
      const result = await visualCardService.generateTrendVisual("snap-1", {
        cardType: "INFO_CARD"
      });
      expect(result.id).toBe("card-1");
      expect(result.cardType).toBe("INFO_CARD");
      expect(result.reviewStatus).toBe("PENDING");
    });
  });

  describe("listTrendVisualCards", () => {
    it("스냅샷 ID로 비주얼 카드 목록을 반환해야 한다", async () => {
      const result = await visualCardService.listTrendVisualCards({ snapshotId: "snap-1" });
      expect(result).toHaveLength(1);
      expect(result[0].snapshotId).toBe("snap-1");
    });

    it("스냅샷 ID 없이 전체 비주얼 카드 목록을 반환해야 한다", async () => {
      const result = await visualCardService.listTrendVisualCards();
      expect(result).toHaveLength(1);
    });

    it("reviewStatus 필터를 쿼리스트링으로 전달해야 한다", async () => {
      let capturedSearch: URLSearchParams | undefined;
      server.use(
        http.get("http://localhost/api/admin/visual-cards", ({ request }) => {
          capturedSearch = new URL(request.url).searchParams;
          return HttpResponse.json([mockVisualCard]);
        })
      );

      const result = await visualCardService.listTrendVisualCards({ reviewStatus: "PENDING" });

      expect(capturedSearch?.get("reviewStatus")).toBe("PENDING");
      expect(result).toHaveLength(1);
    });
  });

  describe("reviewTrendVisualCard", () => {
    it("비주얼 카드를 승인하고 결과를 반환해야 한다", async () => {
      const result = await visualCardService.reviewTrendVisualCard("card-1", {
        approved: true,
        reviewedBy: "admin"
      });
      expect(result.reviewStatus).toBe("APPROVED");
      expect(result.reviewedBy).toBe("admin");
    });

    it("비주얼 카드를 반려하고 결과를 반환해야 한다", async () => {
      server.use(
        http.post("http://localhost/api/admin/visual-cards/card-1/review", () =>
          HttpResponse.json({ ...mockVisualCard, reviewStatus: "REJECTED" })
        )
      );
      const result = await visualCardService.reviewTrendVisualCard("card-1", {
        approved: false,
        reviewNote: "품질 미달",
        reviewedBy: "admin"
      });
      expect(result.reviewStatus).toBe("REJECTED");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/trend-snapshots", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(visualCardService.listTrendSnapshots()).rejects.toThrow();
    });
  });
});
