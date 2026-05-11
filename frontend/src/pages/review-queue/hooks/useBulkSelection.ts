import { useState, useRef, useEffect } from "react";

/**
 * 목록 내 항목들의 체크박스 일괄 선택 상태를 관리한다.
 *
 * 주요 설계 결정:
 * - `itemIds`의 **내용 기반** 변경만 반응한다. 매 렌더마다 새 array 참조로 들어와도
 *   내용이 같으면 기존 선택을 유지한다 (쿼리 리패치/필터 동일 조건 재적용 시 선택 사라짐 방지).
 * - 목록에서 빠진 id는 자동으로 체크 해제하여 stale 선택을 방지한다.
 * - Shift+Click 범위 선택은 같은 목록 내에서만 유효. 목록이 바뀌면 anchor를 리셋한다.
 */
export function useBulkSelection(itemIds: string[]) {
  const [checkedIds, setCheckedIds] = useState<Set<string>>(new Set());
  const lastCheckedRef = useRef<string | null>(null);
  const prevIdsKeyRef = useRef<string>("");

  useEffect(() => {
    // 내용 기반 비교: 순서가 포함된 직렬화 key.
    // 참조 동일성에 의존하지 않으므로 react-query가 새 array를 반환해도 재실행되지 않는다.
    const nextKey = itemIds.join("\u0001");
    if (nextKey === prevIdsKeyRef.current) return;
    prevIdsKeyRef.current = nextKey;

    // 목록이 실제로 바뀌었을 때만 anchor 초기화 + 사라진 id 정리
    lastCheckedRef.current = null;
    const newSet = new Set(itemIds);
    setCheckedIds((prev) => {
      // 변경 없는 경우 동일 참조 유지로 불필요한 렌더 최소화
      let changed = false;
      const next = new Set<string>();
      for (const id of prev) {
        if (newSet.has(id)) next.add(id);
        else changed = true;
      }
      if (!changed && next.size === prev.size) return prev;
      return next;
    });
  }, [itemIds]);

  function toggle(id: string) {
    lastCheckedRef.current = id;
    setCheckedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleRange(id: string) {
    const anchor = lastCheckedRef.current;
    if (!anchor) {
      toggle(id);
      return;
    }
    const startIdx = itemIds.indexOf(anchor);
    const endIdx = itemIds.indexOf(id);
    if (startIdx === -1 || endIdx === -1) {
      toggle(id);
      return;
    }
    const from = Math.min(startIdx, endIdx);
    const to = Math.max(startIdx, endIdx);
    const rangeIds = itemIds.slice(from, to + 1);
    setCheckedIds((prev) => {
      const next = new Set(prev);
      for (const rid of rangeIds) next.add(rid);
      return next;
    });
    // 마지막 클릭한 id를 새 anchor로
    lastCheckedRef.current = id;
  }

  function selectAll() {
    setCheckedIds(new Set(itemIds));
  }

  function clearAll() {
    setCheckedIds(new Set());
    lastCheckedRef.current = null;
  }

  return { checkedIds, toggle, toggleRange, selectAll, clearAll };
}
