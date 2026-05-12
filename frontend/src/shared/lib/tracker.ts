/** 유저 행동 이벤트 수집 SDK. */

interface TrackerEvent {
  eventType: string;
  eventData?: Record<string, unknown>;
  pagePath?: string;
  sessionId: string;
  timestamp: number;
}

interface EventTracker {
  init(userId: string): void;
  track(eventType: string, data?: Record<string, unknown>): void;
  pageView(path: string): void;
  flush(): Promise<void>;
  queueSize(): number;
}

const SESSION_KEY = "tracker_session_id";
const FLUSH_INTERVAL_MS = 5_000;
const FLUSH_THRESHOLD = 10;
const MAX_RETRIES = 3;

/** 현재 페이지 경로를 안전하게 반환한다 (SSR/테스트 환경 대응). */
function currentPathname(): string {
  try {
    return globalThis.location?.pathname ?? "";
  } catch {
    return "";
  }
}

/** crypto.randomUUID 지원 여부에 따라 세션 ID를 생성한다. */
function generateSessionId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // 폴백: 간단한 랜덤 문자열
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

/** Vite base URL을 안전하게 조회한다 (테스트 환경에서 import.meta.env가 없을 수 있음). */
function resolveBaseUrl(): string {
  try {
    return (import.meta.env?.BASE_URL ?? "").replace(/\/$/, "");
  } catch {
    return "";
  }
}

function createTracker(): EventTracker {
  let sessionId: string | null = null;
  const queue: TrackerEvent[] = [];
  let flushTimer: ReturnType<typeof setInterval> | null = null;
  let visibilityListenerRegistered = false;

  /** 비콘 API로 이벤트를 전송한다 (탭 닫힘 시 사용). */
  function sendBeacon(): void {
    if (queue.length === 0) return;
    const events = queue.splice(0);
    const base = resolveBaseUrl();
    try {
      const blob = new Blob([JSON.stringify({ events })], { type: "application/json" });
      navigator.sendBeacon?.(`${base}/api/user/events`, blob);
    } catch {
      // 전송 실패 시 이벤트를 버린다 — 탭 종료 시점이므로 재시도 불가
    }
  }

  /** visibilitychange 이벤트 핸들러: 탭이 숨겨지면 비콘으로 전송한다. */
  function handleVisibilityChange(): void {
    if (document.visibilityState === "hidden") {
      sendBeacon();
    }
  }

  return {
    init(userId: string): void {
      void userId;
      // userId는 백엔드가 인증 쿠키로 식별하므로 직접 전송하지 않는다.
      // 세션 ID: sessionStorage에 있으면 재사용, 없으면 생성
      const stored = sessionStorage.getItem(SESSION_KEY);
      if (stored) {
        sessionId = stored;
      } else {
        sessionId = generateSessionId();
        sessionStorage.setItem(SESSION_KEY, sessionId);
      }

      // 주기적 flush 타이머 시작
      if (flushTimer) clearInterval(flushTimer);
      flushTimer = setInterval(() => {
        void this.flush();
      }, FLUSH_INTERVAL_MS);

      // 탭 닫힘 시 비콘 전송
      if (!visibilityListenerRegistered) {
        document.addEventListener("visibilitychange", handleVisibilityChange);
        visibilityListenerRegistered = true;
      }
    },

    track(eventType: string, data?: Record<string, unknown>): void {
      // init() 호출 전이면 조용히 무시한다.
      if (!sessionId) return;

      queue.push({
        eventType,
        eventData: data,
        pagePath: currentPathname(),
        sessionId,
        timestamp: Date.now()
      });

      // 큐가 임계값에 도달하면 즉시 flush
      if (queue.length >= FLUSH_THRESHOLD) {
        void this.flush();
      }
    },

    pageView(path: string): void {
      this.track("page_view", { path });
    },

    async flush(): Promise<void> {
      if (queue.length === 0) return;

      // 큐에서 이벤트를 빼내어 전송 시도
      const events = queue.splice(0);
      const base = resolveBaseUrl();
      let attempt = 0;
      let success = false;

      while (attempt < MAX_RETRIES && !success) {
        attempt++;
        try {
          const resp = await fetch(`${base}/api/user/events`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({ events })
          });
          if (resp.ok) {
            success = true;
          }
        } catch {
          // 네트워크 오류 — 다음 시도로
        }
      }

      if (!success) {
        console.warn(`[tracker] ${events.length}건 이벤트 전송 실패 (${MAX_RETRIES}회 재시도 후 폐기)`);
      }
    },

    queueSize(): number {
      return queue.length;
    }
  };
}

export const tracker = createTracker();
