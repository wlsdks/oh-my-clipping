// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, vi } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse } from "msw";

vi.mock("@/store/authStore", () => ({
  authStore: {
    getState: vi.fn(() => ({ logout: vi.fn() }))
  }
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
            if (res.status === 401) {
              authStore.getState().logout();
            }
          }
        ]
      }
    })
  };
});

import { historyService } from "@/services/historyService";

const samplePersonaRevisions = [
  {
    revisionId: "rev-2",
    revisionNumber: 2,
    editorId: "admin",
    editorName: "관리자",
    changedFields: ["systemPrompt"],
    createdAt: "2026-04-17T10:00:00Z"
  },
  {
    revisionId: "rev-1",
    revisionNumber: 1,
    editorId: "admin",
    editorName: "관리자",
    changedFields: ["name"],
    createdAt: "2026-04-17T09:00:00Z"
  }
];

const server = setupServer(
  http.get("http://localhost/api/admin/personas/:id/history", ({ request, params }) => {
    const url = new URL(request.url);
    const limit = url.searchParams.get("limit");
    if (params.id === "p1" && limit === "20") {
      return HttpResponse.json(samplePersonaRevisions);
    }
    return HttpResponse.json([]);
  }),
  http.post("http://localhost/api/admin/personas/:id/restore", async ({ request, params }) => {
    const body = (await request.json()) as { revisionId: string; expectedUpdatedAt: string };
    if (params.id === "p1" && body.revisionId === "rev-1") {
      return HttpResponse.json({ id: "p1", name: "restored", updatedAt: body.expectedUpdatedAt });
    }
    return new HttpResponse(null, { status: 404 });
  }),
  http.post("http://localhost/api/admin/category-rules/:id/restore", async ({ request }) => {
    const body = (await request.json()) as { revisionId: string; expectedUpdatedAt: string };
    if (body.expectedUpdatedAt === "stale") {
      return HttpResponse.json({ message: "stale" }, { status: 409 });
    }
    return HttpResponse.json({ categoryId: "c1", updatedAt: body.expectedUpdatedAt });
  })
);

beforeAll(() => server.listen());
afterAll(() => server.close());

describe("historyService", () => {
  it("getHistory requests admin/{resource}/{id}/history with limit", async () => {
    const result = await historyService.getHistory("persona", "p1", 20);
    expect(result).toHaveLength(2);
    expect(result[0].revisionNumber).toBe(2);
    expect(result[0].changedFields).toEqual(["systemPrompt"]);
  });

  it("getHistory returns empty array when no revisions exist", async () => {
    const result = await historyService.getHistory("persona", "unknown", 20);
    expect(result).toEqual([]);
  });

  it("restore posts revisionId + expectedUpdatedAt and returns restored entity", async () => {
    const result = await historyService.restore<{ id: string; name: string }>("persona", "p1", {
      revisionId: "rev-1",
      expectedUpdatedAt: "2026-04-17T09:00:00Z"
    });
    expect(result.name).toBe("restored");
  });

  it("restore uses the correct path segment for each resource type", async () => {
    const result = await historyService.restore<{ categoryId: string }>("category_rule", "c1", {
      revisionId: "rev-x",
      expectedUpdatedAt: "2026-04-17T09:00:00Z"
    });
    expect(result.categoryId).toBe("c1");
  });

  it("restore propagates 409 conflict errors from server", async () => {
    await expect(
      historyService.restore("category_rule", "c1", {
        revisionId: "rev-x",
        expectedUpdatedAt: "stale"
      })
    ).rejects.toThrow();
  });
});
