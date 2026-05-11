import * as Sentry from "@sentry/react";

interface ImportMetaEnv {
  readonly VITE_SENTRY_DSN?: string;
  readonly VITE_SENTRY_ENVIRONMENT?: string;
  readonly VITE_SENTRY_RELEASE?: string;
  readonly VITE_SENTRY_TRACES_SAMPLE_RATE?: string;
  readonly VITE_SENTRY_SAMPLE_RATE?: string;
  readonly MODE: string;
  readonly PROD: boolean;
  readonly DEV: boolean;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}

export interface SentryConfig {
  dsn?: string;
  environment?: string;
  release?: string;
  tracesSampleRate?: number;
  /** Error sampling rate 0.0~1.0. Default 1.0 — capture all errors. Lower to throttle quota usage. */
  sampleRate?: number;
}

let initialized = false;

/**
 * Browser noise patterns to suppress. These are well-known false positives that would burn
 * Sentry quota without actionable signal:
 * - `ResizeObserver loop limit exceeded` — harmless layout race, not a real bug
 * - `Network request failed` / `Load failed` / `NetworkError` — offline / nav cancel
 * - `Non-Error promise rejection` — opaque third-party reject that can't be acted on
 * - `AbortError` — intentional request cancellation
 */
const IGNORE_ERRORS: (string | RegExp)[] = [
  /ResizeObserver loop (limit exceeded|completed with undelivered notifications)/i,
  /Network request failed/i,
  /Load failed/i,
  /NetworkError when attempting to fetch resource/i,
  /Non-Error promise rejection captured/i,
  /AbortError/i,
  /The user aborted a request/i
];

export function isSentryEnabled(): boolean {
  return initialized;
}

/**
 * Reset internal state — only for tests. Do not call from production code.
 */
export function resetSentryForTest(): void {
  initialized = false;
}

export function initSentry(config: SentryConfig): void {
  if (initialized) return;
  const dsn = config.dsn?.trim();
  if (!dsn) return;

  Sentry.init({
    dsn,
    environment: config.environment,
    release: config.release,
    tracesSampleRate: config.tracesSampleRate ?? 0,
    sampleRate: clampSampleRate(config.sampleRate, 1.0),
    // 이벤트당 breadcrumb 을 줄여 payload 크기/quota 사용량을 낮춘다 (기본 100 → 50).
    maxBreadcrumbs: 50,
    // 브라우저 noise 를 무료 티어 quota 에서 제외한다.
    ignoreErrors: IGNORE_ERRORS,
    sendDefaultPii: false,
    beforeSend(event) {
      const req = event.request;
      if (req?.cookies) delete req.cookies;
      if (req?.headers) {
        delete req.headers["Authorization"];
        delete req.headers["Cookie"];
      }
      return event;
    }
  });

  initialized = true;
}

function parseSampleRate(raw: string | undefined): number | undefined {
  if (!raw) return undefined;
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 0 || n > 1) return undefined;
  return n;
}

function clampSampleRate(value: number | undefined, fallback: number): number {
  if (value === undefined || !Number.isFinite(value) || value < 0 || value > 1) return fallback;
  return value;
}

/**
 * Convenience wrapper that reads Vite env vars and forwards to initSentry.
 * Production entrypoint calls this; tests call initSentry directly with a config object.
 */
export function initSentryFromEnv(): void {
  const env = (import.meta as unknown as ImportMeta).env;
  initSentry({
    dsn: env.VITE_SENTRY_DSN,
    environment: env.VITE_SENTRY_ENVIRONMENT || env.MODE,
    release: env.VITE_SENTRY_RELEASE,
    tracesSampleRate: parseSampleRate(env.VITE_SENTRY_TRACES_SAMPLE_RATE) ?? 0,
    sampleRate: parseSampleRate(env.VITE_SENTRY_SAMPLE_RATE)
  });
}

export interface ApiErrorContext {
  status: number;
  url?: string;
  method?: string;
  traceId?: string;
}

/**
 * Report an API error to Sentry. Skips 401 (expected redirect to login) and
 * 404 (routinely used as "not found" signal). Callers should pass status + url
 * + traceId so failures can be matched to server logs.
 */
export function reportApiError(error: unknown, context: ApiErrorContext): void {
  if (!initialized) return;
  if (context.status === 401 || context.status === 404) return;

  Sentry.captureException(error, {
    level: context.status >= 500 ? "error" : "warning",
    tags: {
      api_status: String(context.status),
      api_method: context.method ?? "UNKNOWN"
    },
    contexts: {
      api: {
        status: context.status,
        url: context.url,
        method: context.method,
        traceId: context.traceId
      }
    }
  });
}

export interface BoundaryErrorContext {
  componentStack?: string;
  url?: string;
  reactErrorCode?: string;
}

export function reportBoundaryError(error: unknown, context: BoundaryErrorContext): void {
  if (!initialized) return;

  Sentry.captureException(error, {
    level: "fatal",
    tags: {
      boundary: "root",
      react_error_code: context.reactErrorCode ?? "none"
    },
    contexts: {
      react: {
        componentStack: context.componentStack,
        url: context.url
      }
    }
  });
}
