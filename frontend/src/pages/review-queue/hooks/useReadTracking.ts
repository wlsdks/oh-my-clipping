import { useState, useEffect, useRef } from "react";

/**
 * KST(Asia/Seoul) 기준 YYYY-MM-DD 문자열을 반환한다.
 * UTC 기반이면 매일 오전 9시(KST)에 키가 바뀌어 읽음 표시가 초기화된다.
 * `sv-SE` 로케일은 ISO 포맷(YYYY-MM-DD)을 동일하게 출력하므로 비교/정렬이 안전하다.
 */
function formatKstDateKey(date: Date = new Date()): string {
  return date.toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" });
}

function getTodayKey(): string {
  return `reviewed-items-${formatKstDateKey()}`;
}

function loadReadIds(): Set<string> {
  try {
    const stored = localStorage.getItem(getTodayKey());
    return stored ? new Set(JSON.parse(stored) as string[]) : new Set();
  } catch {
    return new Set();
  }
}

function cleanupOldKeys(): void {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - 7);
  const cutoffStr = formatKstDateKey(cutoff);
  for (let i = localStorage.length - 1; i >= 0; i--) {
    const key = localStorage.key(i);
    if (key?.startsWith("reviewed-items-")) {
      const dateStr = key.replace("reviewed-items-", "");
      if (dateStr < cutoffStr) localStorage.removeItem(key);
    }
  }
}

/** localStorage write debounce 지연값 (ms). 빠른 연속 클릭 시 JSON.stringify 비용 억제. */
const PERSIST_DEBOUNCE_MS = 200;

export function useReadTracking() {
  const [readIds, setReadIds] = useState<Set<string>>(loadReadIds);
  // debounce 타이머와 최신 상태 스냅샷 참조
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const latestRef = useRef<Set<string>>(readIds);
  latestRef.current = readIds;

  useEffect(() => {
    cleanupOldKeys();
  }, []);

  // 언마운트/페이지 숨김 시 pending 상태를 즉시 flush — 데이터 유실 방지
  useEffect(() => {
    function flush() {
      if (persistTimerRef.current) {
        clearTimeout(persistTimerRef.current);
        persistTimerRef.current = null;
      }
      try {
        localStorage.setItem(getTodayKey(), JSON.stringify([...latestRef.current]));
      } catch {
        // storage quota 등 — 무시 (읽음 표시는 UX 보조)
      }
    }
    function handleVisibility() {
      if (document.visibilityState === "hidden") flush();
    }
    window.addEventListener("pagehide", flush);
    document.addEventListener("visibilitychange", handleVisibility);
    return () => {
      flush();
      window.removeEventListener("pagehide", flush);
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, []);

  function schedulePersist() {
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
    persistTimerRef.current = setTimeout(() => {
      try {
        localStorage.setItem(getTodayKey(), JSON.stringify([...latestRef.current]));
      } catch {
        // storage quota 등 — 무시
      }
      persistTimerRef.current = null;
    }, PERSIST_DEBOUNCE_MS);
  }

  function markAsRead(id: string) {
    setReadIds((prev) => {
      if (prev.has(id)) return prev;
      const next = new Set(prev);
      next.add(id);
      return next;
    });
    schedulePersist();
  }

  function markManyAsRead(ids: string[]) {
    setReadIds((prev) => {
      let changed = false;
      const next = new Set(prev);
      for (const id of ids) {
        if (!next.has(id)) {
          next.add(id);
          changed = true;
        }
      }
      return changed ? next : prev;
    });
    schedulePersist();
  }

  function isRead(id: string): boolean {
    return readIds.has(id);
  }

  return { isRead, markAsRead, markManyAsRead };
}
