import { useSearchParams } from "react-router-dom";

/**
 * URL 쿼리 파라미터(?tab=)와 동기화된 탭 상태를 관리한다.
 * 브라우저 뒤로가기 및 딥 링크를 지원한다.
 * 다른 쿼리 파라미터(category, period 등)는 그대로 보존한다.
 */
export function useTabSync<T extends string>(
  defaultTab: T,
  validTabs: readonly T[]
): [T, (tab: T) => void] {
  const [searchParams, setSearchParams] = useSearchParams();
  const raw = searchParams.get("tab");
  const tab = (validTabs as readonly string[]).includes(raw ?? "")
    ? (raw as T)
    : defaultTab;

  const setTab = (t: T) => {
    const next = new URLSearchParams(searchParams);
    next.set("tab", t);
    setSearchParams(next, { replace: true });
  };

  return [tab, setTab];
}
