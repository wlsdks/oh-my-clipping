// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

vi.mock("@/store/authStore", () => ({
  authStore: { getState: vi.fn(() => ({ logout: vi.fn() })) },
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
          },
        ],
      },
    }),
  };
});

import { userService } from "../userService";
import type { SubmitWithEntriesRequest } from "@/types/adminDto";

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("userService.createRequestWithEntries", () => {
  it("POST /api/user/requests/with-entries 로 body 전송", async () => {
    let receivedBody: unknown;
    server.use(
      http.post("http://localhost/api/user/requests/with-entries", async ({ request }) => {
        receivedBody = await request.json();
        return HttpResponse.json(
          { requestId: "req-1", status: "submitted", errors: [] },
          { status: 201 }
        );
      })
    );
    const req: SubmitWithEntriesRequest = {
      categoryName: "cat",
      entries: [{ value: "MegaCorp", type: "company", stockCode: "999930" }],
    };
    const res = await userService.createRequestWithEntries(req);
    expect((receivedBody as { entries: Array<{ stockCode?: string }> }).entries[0].stockCode).toBe("999930");
    expect(res.status).toBe("submitted");
    expect(res.requestId).toBe("req-1");
  });

  it("partial 응답도 throw 하지 않고 반환", async () => {
    server.use(
      http.post("http://localhost/api/user/requests/with-entries", () =>
        HttpResponse.json(
          {
            requestId: "req-1",
            status: "partial",
            errors: [{ index: 0, value: "x", reason: "DUPLICATE_IN_REQUEST" }],
          },
          { status: 200 }
        )
      )
    );
    const res = await userService.createRequestWithEntries({
      categoryName: "c",
      entries: [],
    });
    expect(res.status).toBe("partial");
    expect(res.errors).toHaveLength(1);
  });

  it("rejected 응답도 throw 하지 않고 반환", async () => {
    server.use(
      http.post("http://localhost/api/user/requests/with-entries", () =>
        HttpResponse.json(
          {
            requestId: "",
            status: "rejected",
            errors: [{ index: 0, value: "x", reason: "COMPETITOR_WATCHLIST_CONFLICT" }],
          },
          { status: 200 }
        )
      )
    );
    const res = await userService.createRequestWithEntries({
      categoryName: "c",
      entries: [],
    });
    expect(res.status).toBe("rejected");
  });
});
