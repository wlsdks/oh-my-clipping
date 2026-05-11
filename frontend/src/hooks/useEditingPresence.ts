import { useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { editingPresenceService } from "@/services/editingPresenceService";
import { editingPresenceKeys } from "@/queries/editingPresenceKeys";
import type {
  EditingResourceType,
  EditingSession
} from "@/types/editingPresence";

/** heartbeat 주기 — 백엔드 TTL(60s)의 절반이라 1회 누락돼도 presence 유지. */
const HEARTBEAT_INTERVAL_MS = 30_000;

/** 다른 관리자 presence 조회 주기. heartbeat 과 동일한 30초. */
const POLL_INTERVAL_MS = 30_000;

export interface UseEditingPresenceArgs {
  resourceType: EditingResourceType;
  resourceId: string | null | undefined;
  /** 편집 모달이 열려 있는 동안만 true. false 면 heartbeat/poll 을 멈춘다. */
  enabled: boolean;
}

export interface UseEditingPresenceResult {
  /** 본인을 제외한 현재 편집 중인 관리자 목록. */
  otherEditors: EditingSession[];
}

/**
 * 편집 presence 트래킹 훅.
 *
 * - mount/enabled=true 가 되면 즉시 heartbeat 1회 + 30초 interval 시작 + listActive 폴링 시작
 * - enabled=false 또는 unmount 시 interval 제거 + release 호출
 * - `resourceId` 가 null/undefined 면 비활성 상태로 간주 (신규 생성 모달 대응)
 *
 * React Compiler 규칙 준수: `useMemo`/`useCallback` 를 쓰지 않고 `useRef` 로 interval 관리.
 */
export function useEditingPresence(
  args: UseEditingPresenceArgs
): UseEditingPresenceResult {
  const { resourceType, resourceId, enabled } = args;
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const activeQuery = useQuery({
    queryKey: editingPresenceKeys.active(
      resourceType,
      resourceId ?? "__none__"
    ),
    queryFn: () => editingPresenceService.listActive(resourceType, resourceId!),
    enabled: enabled && Boolean(resourceId),
    refetchInterval: enabled && resourceId ? POLL_INTERVAL_MS : false,
    refetchIntervalInBackground: false,
    retry: false,
    staleTime: 5_000
  });

  useEffect(() => {
    // 비활성 또는 리소스 식별자 누락 — 어떤 타이머도 돌리지 않는다.
    if (!enabled || !resourceId) {
      return;
    }

    let cancelled = false;

    // 즉시 1회 heartbeat 를 보내 모달이 열리자마자 presence 가 뜬 것으로 보이게 한다.
    void editingPresenceService
      .heartbeat(resourceType, resourceId)
      .catch(() => {
        // presence 는 UX 보조 기능이므로 실패해도 편집 흐름에 영향을 주지 않는다.
      });

    timerRef.current = setInterval(() => {
      if (cancelled) return;
      void editingPresenceService
        .heartbeat(resourceType, resourceId)
        .catch(() => {
          // fail-silent — 서버가 일시적으로 안 되더라도 다음 tick 에 재시도된다.
        });
    }, HEARTBEAT_INTERVAL_MS);

    return () => {
      cancelled = true;
      if (timerRef.current !== null) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
      // 언마운트/enabled=false 시 즉시 presence 를 비워 다른 편집자의 대기시간을 줄인다.
      void editingPresenceService
        .release(resourceType, resourceId)
        .catch(() => {
          // TTL 로도 자연스럽게 정리되므로 실패해도 무시한다.
        });
    };
  }, [enabled, resourceType, resourceId]);

  return {
    otherEditors: activeQuery.data ?? []
  };
}
