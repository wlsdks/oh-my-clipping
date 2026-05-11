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

import { reviewService } from "@/services/reviewService";
import type { ReviewQueueItem } from "@/types/review";

const mockReviewItem: ReviewQueueItem = {
  summaryId: "sum-1",
  categoryId: "cat-1",
  categoryName: "IT/기술",
  title: "AI 혁신 기사",
  summary: "요약 내용",
  sourceLink: "https://techcrunch.com/article",
  keywords: ["AI", "기술"],
  importanceScore: 0.85,
  suggestedStatus: "INCLUDE",
  currentStatus: "REVIEW",
  statusReason: "검토 필요",
  reviewedBy: null,
  reviewedAt: null,
  priorityScore: 0.9,
  priorityLabel: "높음",
  createdAt: "2026-01-01T09:00:00Z"
};

const handlers = [
  // listItems — no params
  http.get("http://localhost/api/admin/review-items", () =>
    HttpResponse.json([mockReviewItem])
  ),

  // approve
  http.post("http://localhost/api/admin/review-items/sum-1/approve", () =>
    HttpResponse.json({ summaryId: "sum-1", status: "APPROVED" })
  ),

  // exclude
  http.post("http://localhost/api/admin/review-items/sum-1/exclude", () =>
    HttpResponse.json({ summaryId: "sum-1", status: "EXCLUDED" })
  ),

  // markForReview
  http.post("http://localhost/api/admin/review-items/sum-1/review", () =>
    HttpResponse.json({ summaryId: "sum-1", status: "REVIEW" })
  )
];

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("reviewService", () => {
  describe("리뷰 아이템 목록 조회", () => {
    it("listItems는 리뷰 항목 목록을 반환해야 한다", async () => {
      const result = await reviewService.listItems();
      expect(result).toEqual([mockReviewItem]);
      expect(result).toHaveLength(1);
      expect(result[0].summaryId).toBe("sum-1");
    });

    it("listItems는 categoryId 필터를 지원해야 한다", async () => {
      const result = await reviewService.listItems({ categoryId: "cat-1" });
      expect(Array.isArray(result)).toBe(true);
    });

    it("listItems는 status 필터를 지원해야 한다", async () => {
      const result = await reviewService.listItems({ status: "REVIEW" });
      expect(Array.isArray(result)).toBe(true);
    });

    it("listItems는 limit 파라미터를 지원해야 한다", async () => {
      const result = await reviewService.listItems({ limit: 10 });
      expect(Array.isArray(result)).toBe(true);
    });

    it("listItems는 perCategory 파라미터를 URL 쿼리로 전달해야 한다", async () => {
      // 서버로 perCategory 쿼리가 정확히 전달되는지 확인한다.
      let capturedUrl = "";
      server.use(
        http.get("http://localhost/api/admin/review-items", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json([mockReviewItem]);
        })
      );
      await reviewService.listItems({ perCategory: 5, limit: 100 });
      expect(capturedUrl).toContain("perCategory=5");
      expect(capturedUrl).toContain("limit=100");
    });

    it("listItems는 perCategory가 undefined이면 쿼리에 포함시키지 않는다", async () => {
      // 미지정 시 서버가 기본 동작으로 유지되도록 쿼리에서 빠져야 한다.
      let capturedUrl = "";
      server.use(
        http.get("http://localhost/api/admin/review-items", ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json([mockReviewItem]);
        })
      );
      await reviewService.listItems({ limit: 50 });
      expect(capturedUrl).not.toContain("perCategory");
    });
  });

  describe("리뷰 아이템 액션", () => {
    it("approve는 summaryId와 APPROVED 상태를 반환해야 한다", async () => {
      const result = await reviewService.approve("sum-1");
      expect(result.summaryId).toBe("sum-1");
      expect(result.status).toBe("APPROVED");
    });

    it("approve는 데이터와 함께 POST 요청해야 한다", async () => {
      const result = await reviewService.approve("sum-1", { reviewedBy: "admin" });
      expect(result.summaryId).toBe("sum-1");
    });

    it("exclude는 summaryId와 EXCLUDED 상태를 반환해야 한다", async () => {
      const result = await reviewService.exclude("sum-1");
      expect(result.summaryId).toBe("sum-1");
      expect(result.status).toBe("EXCLUDED");
    });

    it("markForReview는 summaryId와 REVIEW 상태를 반환해야 한다", async () => {
      const result = await reviewService.markForReview("sum-1");
      expect(result.summaryId).toBe("sum-1");
      expect(result.status).toBe("REVIEW");
    });
  });

  describe("에러 처리", () => {
    it("서버 오류 시 에러를 throw해야 한다", async () => {
      server.use(
        http.get("http://localhost/api/admin/review-items", () =>
          HttpResponse.json({ message: "서버 오류" }, { status: 500 })
        )
      );
      await expect(reviewService.listItems()).rejects.toThrow();
    });

    it("404 응답 시 에러를 throw해야 한다", async () => {
      server.use(
        http.post("http://localhost/api/admin/review-items/not-found/approve", () =>
          HttpResponse.json({ message: "존재하지 않는 항목" }, { status: 404 })
        )
      );
      await expect(reviewService.approve("not-found")).rejects.toThrow();
    });
  });
});
