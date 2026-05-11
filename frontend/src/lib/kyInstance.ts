import ky, { HTTPError } from "ky";
import { authStore } from "@/store/authStore";
import { ApiError } from "@/shared/api/httpClient";
import { reportApiError } from "@/lib/sentry";
import type { ApiErrorShape } from "@/shared/types/common";

let redirecting = false;

export function redirectToLogin() {
  if (redirecting) return;
  // 이미 로그인 페이지에 있으면 리다이렉트하지 않는다 — 로그인 실패 토스트가 사라지는 것을 막는다
  if (typeof window !== "undefined" && window.location.pathname === "/login") {
    authStore.getState().logout();
    return;
  }
  redirecting = true;
  authStore.getState().logout();
  window.location.href = "/login";
}

/**
 * 멱등성이 필요한 상태 변경 메서드.
 * 같은 요청이 네트워크 재시도/더블클릭으로 두 번 들어와도 백엔드가 한 번만 처리하도록
 * 요청 단위로 `Idempotency-Key` UUID 를 부여한다.
 */
const IDEMPOTENT_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

/**
 * 브라우저에서 안전하게 UUID 를 생성한다. `crypto.randomUUID()` 가 없는 구버전에서도 동작하도록 폴백을 둔다.
 */
function generateIdempotencyKey(): string {
  const cryptoRef = typeof globalThis !== "undefined" ? globalThis.crypto : undefined;
  if (cryptoRef && typeof cryptoRef.randomUUID === "function") {
    return cryptoRef.randomUUID();
  }
  // RFC4122 v4 폴백: crypto.getRandomValues 기반
  if (cryptoRef && typeof cryptoRef.getRandomValues === "function") {
    const bytes = new Uint8Array(16);
    cryptoRef.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0"));
    return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-${hex.slice(8, 10).join("")}-${hex.slice(10, 16).join("")}`;
  }
  // 최후 폴백 — 테스트 환경에서만 도달한다
  return `fallback-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * ky의 HTTPError에 파싱된 에러 페이로드를 첨부한다.
 * userFriendlyMessage가 ApiError 형태로 처리할 수 있도록
 * 에러를 ApiError로 변환해 throw한다.
 */
async function parseAndThrowAsApiError(error: HTTPError): Promise<HTTPError> {
  let payload: ApiErrorShape | null = null;
  let message = `요청 실패 (${error.response.status})`;

  try {
    const raw = await error.response.clone().text();
    if (raw) {
      const parsed = JSON.parse(raw) as ApiErrorShape;
      payload = parsed;
      const traceText = parsed.traceId ? ` (추적 ID: ${parsed.traceId})` : "";
      message = (parsed.message || raw) + traceText;
    }
  } catch {
    // JSON 파싱 실패 시 기본 메시지 유지
  }

  const apiError = new ApiError(error.response.status, message, payload);

  // Sentry: 401/404 는 reportApiError 내부에서 건너뛴다. 나머지는 level 을 status 에 따라 분리.
  reportApiError(apiError, {
    status: error.response.status,
    url: error.request.url,
    method: error.request.method,
    traceId: payload?.traceId
  });

  // Sentry DSN 미설정 환경(로컬 dev, Sentry 장애 시) 을 위해 4xx/5xx 는 콘솔에도 흔적을 남긴다.
  // 401 은 로그인 리다이렉트로 처리되므로 생략.
  if (error.response.status !== 401) {
    const traceSuffix = payload?.traceId ? ` traceId=${payload.traceId}` : "";
    console.warn(`[API ${error.response.status}] ${error.request.method} ${error.request.url}${traceSuffix}`);
  }

  throw apiError;
}

export const api = ky.create({
  prefixUrl: "/api",
  credentials: "include",
  timeout: 10_000,
  headers: {
    Accept: "application/json",
    "ngrok-skip-browser-warning": "true"
  },
  hooks: {
    beforeRequest: [
      (request) => {
        // 상태 변경 메서드에는 자동으로 Idempotency-Key 를 붙여 네트워크 재시도로 인한 중복 업데이트를 막는다.
        if (IDEMPOTENT_METHODS.has(request.method.toUpperCase()) && !request.headers.has("Idempotency-Key")) {
          request.headers.set("Idempotency-Key", generateIdempotencyKey());
        }
      }
    ],
    afterResponse: [
      async (_req, _opts, res) => {
        if (res.status === 401) redirectToLogin();
      }
    ],
    beforeError: [parseAndThrowAsApiError]
  }
});
