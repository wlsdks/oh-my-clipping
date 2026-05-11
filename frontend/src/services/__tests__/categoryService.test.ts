// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, vi } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse } from "msw";

// authStore mock (ky afterResponse 훅에서 사용)
vi.mock("@/store/authStore", () => ({
  authStore: {
    getState: vi.fn(() => ({ logout: vi.fn() }))
  }
}));

// Node 환경에서 ky는 prefixUrl이 절대 URL이어야 한다.
// kyInstance.ts의 /api → http://localhost/api 로 대체한다.
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
            if (res.status === 401) {
              authStore.getState().logout();
            }
          }
        ]
      }
    })
  };
});

import { categoryService } from "@/services/categoryService";

const mockCategories = [{ id: "1", name: "경제", keyword: "주식" }];
const mockPageResponse = {
  content: mockCategories,
  totalCount: 1,
  page: 0,
  size: 9999
};

const server = setupServer(
  http.get("http://localhost/api/admin/categories", () => HttpResponse.json(mockPageResponse)),
  http.post("http://localhost/api/admin/categories", () =>
    HttpResponse.json({ id: "2", name: "정치", keyword: "국회" }, { status: 201 })
  ),
  http.delete("http://localhost/api/admin/categories/:id", () => new HttpResponse(null, { status: 204 })),
  http.get("http://localhost/api/admin/categories/999", () =>
    HttpResponse.json({ message: "존재하지 않습니다" }, { status: 404 })
  )
);

beforeAll(() => server.listen());
afterAll(() => server.close());

describe("categoryService", () => {
  it("getAll은 카테고리 목록을 반환해야 한다", async () => {
    const result = await categoryService.getAll();
    expect(result).toEqual(mockCategories);
  });

  it("getPage는 페이지네이션 응답을 반환해야 한다", async () => {
    const result = await categoryService.getPage();
    expect(result).toEqual(mockPageResponse);
    expect(result.content).toEqual(mockCategories);
    expect(result.totalCount).toBe(1);
  });

  it("create는 새 카테고리를 반환해야 한다", async () => {
    const result = await categoryService.create({ name: "정치" });
    expect(result.id).toBe("2");
  });

  it("getById 404 시 에러를 throw해야 한다", async () => {
    await expect(categoryService.getById("999")).rejects.toThrow();
  });
});
