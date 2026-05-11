// @vitest-environment node
import { describe, it, expect, beforeEach, vi } from "vitest";

const { sentryInitMock, sentryCaptureExceptionMock } = vi.hoisted(() => ({
  sentryInitMock: vi.fn(),
  sentryCaptureExceptionMock: vi.fn()
}));

vi.mock("@sentry/react", () => ({
  init: sentryInitMock,
  captureException: sentryCaptureExceptionMock
}));

import { initSentry, isSentryEnabled, reportApiError, reportBoundaryError, resetSentryForTest } from "@/lib/sentry";

beforeEach(() => {
  sentryInitMock.mockClear();
  sentryCaptureExceptionMock.mockClear();
  resetSentryForTest();
});

describe("initSentry", () => {
  it("DSN 이 없으면 Sentry.init 을 호출하지 않는다", () => {
    initSentry({ dsn: "" });
    expect(sentryInitMock).not.toHaveBeenCalled();
    expect(isSentryEnabled()).toBe(false);
  });

  it("공백만 있는 DSN 도 비활성으로 취급한다", () => {
    initSentry({ dsn: "   " });
    expect(sentryInitMock).not.toHaveBeenCalled();
    expect(isSentryEnabled()).toBe(false);
  });

  it("유효한 DSN 이면 환경 설정과 함께 Sentry.init 을 한 번만 호출한다", () => {
    initSentry({
      dsn: "https://fakekey@o0.ingest.sentry.io/0",
      environment: "staging",
      tracesSampleRate: 0.2
    });
    initSentry({ dsn: "https://fakekey@o0.ingest.sentry.io/0" });

    expect(sentryInitMock).toHaveBeenCalledTimes(1);
    const call = sentryInitMock.mock.calls[0][0];
    expect(call.dsn).toBe("https://fakekey@o0.ingest.sentry.io/0");
    expect(call.environment).toBe("staging");
    expect(call.tracesSampleRate).toBe(0.2);
    expect(call.sendDefaultPii).toBe(false);
    expect(isSentryEnabled()).toBe(true);
  });

  it("tracesSampleRate 미지정 시 0 으로 폴백한다", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });
    expect(sentryInitMock.mock.calls[0][0].tracesSampleRate).toBe(0);
  });

  it("sampleRate 미지정 시 1.0 (전부 캡처) 으로 폴백한다", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });
    expect(sentryInitMock.mock.calls[0][0].sampleRate).toBe(1.0);
  });

  it("sampleRate 가 [0,1] 범위 밖이면 1.0 으로 클램프된다 (quota 보호 오버라이드)", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0", sampleRate: 2.5 });
    expect(sentryInitMock.mock.calls[0][0].sampleRate).toBe(1.0);
  });

  it("sampleRate 가 0.5 면 절반만 전송한다 (quota 절약 모드)", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0", sampleRate: 0.5 });
    expect(sentryInitMock.mock.calls[0][0].sampleRate).toBe(0.5);
  });

  it("maxBreadcrumbs 를 50 으로 제한한다 (quota/payload 절약)", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });
    expect(sentryInitMock.mock.calls[0][0].maxBreadcrumbs).toBe(50);
  });

  it("ignoreErrors 목록에 브라우저 noise 패턴을 포함한다", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });
    const ignoreErrors = sentryInitMock.mock.calls[0][0].ignoreErrors as (string | RegExp)[];

    expect(ignoreErrors.some((p) => p instanceof RegExp && p.test("ResizeObserver loop limit exceeded"))).toBe(true);
    expect(ignoreErrors.some((p) => p instanceof RegExp && p.test("Network request failed"))).toBe(true);
    expect(ignoreErrors.some((p) => p instanceof RegExp && p.test("AbortError: The user aborted a request"))).toBe(
      true
    );
  });

  it("beforeSend 훅이 쿠키/Authorization 헤더를 제거한다", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });

    const beforeSend = sentryInitMock.mock.calls[0][0].beforeSend;
    const event = {
      request: {
        cookies: "session=abc",
        headers: {
          Authorization: "Bearer secret",
          Cookie: "c=1",
          "X-Safe": "ok"
        }
      }
    };
    const cleaned = beforeSend(event);

    expect(cleaned.request.cookies).toBeUndefined();
    expect(cleaned.request.headers.Authorization).toBeUndefined();
    expect(cleaned.request.headers.Cookie).toBeUndefined();
    expect(cleaned.request.headers["X-Safe"]).toBe("ok");
  });
});

describe("reportApiError", () => {
  it("Sentry 가 초기화되지 않았으면 captureException 을 호출하지 않는다", () => {
    reportApiError(new Error("boom"), { status: 500, url: "/api/x", method: "GET" });
    expect(sentryCaptureExceptionMock).not.toHaveBeenCalled();
  });

  it("401 은 캡처하지 않는다 (로그인 리디렉션 경로)", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });
    reportApiError(new Error("unauth"), { status: 401, url: "/api/x", method: "GET" });
    expect(sentryCaptureExceptionMock).not.toHaveBeenCalled();
  });

  it("404 도 캡처하지 않는다 (not-found 신호)", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });
    reportApiError(new Error("nf"), { status: 404, url: "/api/x", method: "GET" });
    expect(sentryCaptureExceptionMock).not.toHaveBeenCalled();
  });

  it("5xx 에러는 level=error 로 캡처한다", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });

    const err = new Error("server boom");
    reportApiError(err, {
      status: 503,
      url: "/api/x",
      method: "POST",
      traceId: "abc-123"
    });

    expect(sentryCaptureExceptionMock).toHaveBeenCalledTimes(1);
    const [capturedErr, ctx] = sentryCaptureExceptionMock.mock.calls[0];
    expect(capturedErr).toBe(err);
    expect(ctx.level).toBe("error");
    expect(ctx.tags.api_status).toBe("503");
    expect(ctx.tags.api_method).toBe("POST");
    expect(ctx.contexts.api.traceId).toBe("abc-123");
  });

  it("4xx(400/403 등 401·404 외) 은 level=warning 으로 캡처한다", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });

    reportApiError(new Error("bad"), { status: 400, url: "/api/x", method: "POST" });

    expect(sentryCaptureExceptionMock).toHaveBeenCalledTimes(1);
    const ctx = sentryCaptureExceptionMock.mock.calls[0][1];
    expect(ctx.level).toBe("warning");
  });

  it("method 누락 시 태그에 UNKNOWN 으로 기록한다", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });
    reportApiError(new Error("x"), { status: 500 });
    expect(sentryCaptureExceptionMock.mock.calls[0][1].tags.api_method).toBe("UNKNOWN");
  });
});

describe("reportBoundaryError", () => {
  it("Sentry 가 초기화되지 않았으면 조용히 넘어간다", () => {
    reportBoundaryError(new Error("x"), { url: "/a" });
    expect(sentryCaptureExceptionMock).not.toHaveBeenCalled();
  });

  it("초기화 후 level=fatal 로 componentStack 을 포함해 캡처한다", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });

    const err = new Error("boundary");
    reportBoundaryError(err, {
      componentStack: "\n  at App\n  at Root",
      url: "/dashboard",
      reactErrorCode: "185"
    });

    expect(sentryCaptureExceptionMock).toHaveBeenCalledTimes(1);
    const [capturedErr, ctx] = sentryCaptureExceptionMock.mock.calls[0];
    expect(capturedErr).toBe(err);
    expect(ctx.level).toBe("fatal");
    expect(ctx.tags.react_error_code).toBe("185");
    expect(ctx.contexts.react.componentStack).toContain("at App");
  });

  it("reactErrorCode 미지정 시 태그에 none 으로 기록한다", () => {
    initSentry({ dsn: "https://x@o0.ingest.sentry.io/0" });
    reportBoundaryError(new Error("x"), { url: "/a" });
    expect(sentryCaptureExceptionMock.mock.calls[0][1].tags.react_error_code).toBe("none");
  });
});
