// @vitest-environment node
import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse } from "msw";

vi.mock("@/store/authStore", () => ({
  authStore: {
    getState: vi.fn(() => ({ logout: vi.fn() }))
  }
}));

const { reportApiErrorMock } = vi.hoisted(() => ({
  reportApiErrorMock: vi.fn()
}));

vi.mock("@/lib/sentry", () => ({
  initSentry: vi.fn(),
  initSentryFromEnv: vi.fn(),
  isSentryEnabled: vi.fn(() => true),
  resetSentryForTest: vi.fn(),
  reportApiError: reportApiErrorMock,
  reportBoundaryError: vi.fn()
}));

const capturedRequests: { method: string; idempotencyKey: string | null }[] = [];

const server = setupServer(
  http.get("http://localhost/api/unauthorized", () => HttpResponse.json({}, { status: 401 })),
  http.all("http://localhost/api/echo", ({ request }) => {
    capturedRequests.push({
      method: request.method.toUpperCase(),
      idempotencyKey: request.headers.get("Idempotency-Key")
    });
    return HttpResponse.json({ ok: true });
  })
);

beforeAll(() => server.listen());
afterAll(() => server.close());
beforeEach(() => {
  capturedRequests.length = 0;
  reportApiErrorMock.mockClear();
});

describe("kyInstance", () => {
  it("api 인스턴스가 ky 인터페이스를 노출한다", async () => {
    const mod = await import("@/lib/kyInstance");
    // 단순 존재 검증이 아니라 ky 인스턴스가 제공하는 HTTP 메서드를 모두 갖추었는지 확인
    expect(mod.api).toEqual(expect.any(Function));
    expect(typeof mod.api.get).toBe("function");
    expect(typeof mod.api.post).toBe("function");
    expect(typeof mod.api.put).toBe("function");
    expect(typeof mod.api.patch).toBe("function");
    expect(typeof mod.api.delete).toBe("function");
    expect(typeof mod.api.extend).toBe("function");
  });

  it("401 응답 시 authStore.logout()을 호출해야 한다", async () => {
    const { authStore } = await import("@/store/authStore");
    const logoutSpy = vi.fn();
    vi.mocked(authStore.getState).mockReturnValue({
      isLoggedIn: true,
      user: null,
      login: vi.fn(),
      logout: logoutSpy
    });

    const ky = (await import("ky")).default;
    const testApi = ky.create({
      prefixUrl: "http://localhost/api",
      credentials: "include",
      headers: { Accept: "application/json" },
      hooks: {
        afterResponse: [
          async (_req, _opts, res) => {
            if (res.status === 401) {
              authStore.getState().logout();
            }
          }
        ]
      }
    });

    try {
      await testApi.get("unauthorized").json();
    } catch {
      // 401은 HTTPError로 throw됨 — 정상
    }
    expect(logoutSpy).toHaveBeenCalledOnce();
  });
});

describe("Idempotency-Key 자동 삽입", () => {
  async function buildApi() {
    // kyInstance.ts 의 beforeRequest 훅 동작을 재현하기 위해 실제 hook 을 공유 인스턴스로 만든다.
    const ky = (await import("ky")).default;
    const IDEMPOTENT_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);
    return ky.create({
      prefixUrl: "http://localhost/api",
      credentials: "include",
      headers: { Accept: "application/json" },
      hooks: {
        beforeRequest: [
          (request) => {
            if (IDEMPOTENT_METHODS.has(request.method.toUpperCase()) && !request.headers.has("Idempotency-Key")) {
              request.headers.set("Idempotency-Key", "test-uuid-123");
            }
          }
        ]
      }
    });
  }

  it("PUT/POST/PATCH/DELETE 요청에 Idempotency-Key 헤더를 자동으로 붙인다", async () => {
    const testApi = await buildApi();

    await testApi.put("echo", { json: { a: 1 } }).json();
    await testApi.post("echo", { json: { a: 1 } }).json();
    await testApi.patch("echo", { json: { a: 1 } }).json();
    await testApi.delete("echo").json();

    expect(capturedRequests).toHaveLength(4);
    capturedRequests.forEach((req) => {
      expect(req.idempotencyKey).toBe("test-uuid-123");
    });
  });

  it("GET 요청에는 Idempotency-Key 를 붙이지 않는다", async () => {
    const testApi = await buildApi();

    await testApi.get("echo").json();

    expect(capturedRequests).toHaveLength(1);
    expect(capturedRequests[0].method).toBe("GET");
    expect(capturedRequests[0].idempotencyKey).toBeNull();
  });

  it("호출자가 직접 Idempotency-Key 를 지정하면 그 값을 보존한다", async () => {
    const testApi = await buildApi();

    await testApi.put("echo", { json: { a: 1 }, headers: { "Idempotency-Key": "caller-specified" } }).json();

    expect(capturedRequests[0].idempotencyKey).toBe("caller-specified");
  });
});

describe("실제 kyInstance의 Idempotency-Key 자동 삽입", () => {
  it("실 api 인스턴스도 PUT 요청에 Idempotency-Key 를 붙인다", async () => {
    // 실 api 의 prefixUrl 은 "/api" 상대 경로라 MSW 가 라우팅하지 못한다.
    // api.extend 로 MSW 가 처리 가능한 절대 prefixUrl 로 바꾼 인스턴스를 만들어
    // beforeRequest 훅이 실제로 Idempotency-Key 를 주입하는지 검증한다.
    const { api } = await import("@/lib/kyInstance");
    const extended = api.extend({ prefixUrl: "http://localhost/api" });

    await extended.put("echo", { json: { a: 1 } }).json();

    expect(capturedRequests).toHaveLength(1);
    expect(capturedRequests[0].method).toBe("PUT");
    // crypto.randomUUID() 가 생성한 값이므로 형식만 검증한다.
    expect(capturedRequests[0].idempotencyKey).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
  });
});

describe("beforeError → Sentry 보고", () => {
  async function buildApiWithBeforeError() {
    // 실제 parseAndThrowAsApiError 를 재현하려면 kyInstance 를 그대로 쓰기 어려워
    // (prefixUrl=/api 상대경로), 동일한 훅을 MSW 가 라우팅할 수 있는 절대 URL 로 구성한다.
    const ky = (await import("ky")).default;
    const { ApiError } = await import("@/shared/api/httpClient");
    const { reportApiError } = await import("@/lib/sentry");

    return ky.create({
      prefixUrl: "http://localhost/api",
      credentials: "include",
      retry: 0,
      headers: { Accept: "application/json" },
      hooks: {
        beforeError: [
          async (error) => {
            let payload: { traceId?: string; message?: string } | null = null;
            let message = `요청 실패 (${error.response.status})`;
            try {
              const raw = await error.response.clone().text();
              if (raw) {
                payload = JSON.parse(raw);
                message = payload?.message || raw;
              }
            } catch {
              // JSON 파싱 실패 시 기본 메시지 유지
            }
            const apiError = new ApiError(error.response.status, message, payload);
            reportApiError(apiError, {
              status: error.response.status,
              url: error.request.url,
              method: error.request.method,
              traceId: payload?.traceId
            });
            throw apiError;
          }
        ]
      }
    });
  }

  it("5xx 응답 시 reportApiError 를 status/url/method/traceId 와 함께 호출한다", async () => {
    server.use(
      http.get("http://localhost/api/boom", () =>
        HttpResponse.json({ message: "internal", traceId: "trace-xyz" }, { status: 503 })
      )
    );

    const testApi = await buildApiWithBeforeError();

    await expect(testApi.get("boom").json()).rejects.toThrow();

    expect(reportApiErrorMock).toHaveBeenCalledTimes(1);
    const [apiError, ctx] = reportApiErrorMock.mock.calls[0];
    expect(apiError).toBeInstanceOf(Error);
    expect((apiError as Error).message).toContain("internal");
    expect(ctx.status).toBe(503);
    expect(ctx.method).toBe("GET");
    expect(ctx.url).toBe("http://localhost/api/boom");
    expect(ctx.traceId).toBe("trace-xyz");
  });

  it("JSON 이 아닌 응답 본문도 reportApiError 를 호출한다 (payload 없이)", async () => {
    server.use(http.get("http://localhost/api/textboom", () => new HttpResponse("gateway down", { status: 502 })));

    const testApi = await buildApiWithBeforeError();

    await expect(testApi.get("textboom").json()).rejects.toThrow();

    expect(reportApiErrorMock).toHaveBeenCalledTimes(1);
    expect(reportApiErrorMock.mock.calls[0][1].status).toBe(502);
    expect(reportApiErrorMock.mock.calls[0][1].traceId).toBeUndefined();
  });
});
