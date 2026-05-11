import { useEffect, useRef, useState } from "react";
import { AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";

interface ChangeDetectionStripProps {
  /** 최초 편집 모달 open 시점의 `updatedAt` (ISO). null 이면 감지를 수행하지 않는다. */
  initialUpdatedAt: string | null | undefined;
  /** 현재 서버에서 가져온 `updatedAt`. useQuery 등이 갱신해 주입한다. */
  currentUpdatedAt: string | null | undefined;
  /** "최신 불러오기" 를 눌렀을 때 실행할 콜백. 비동기 허용. */
  onReload: () => void | Promise<void>;
}

/**
 * 편집 중인 리소스가 "다른 관리자에 의해 저장됨" 을 감지했을 때 상단에 띄우는 sticky 띠.
 *
 * - `initialUpdatedAt` 과 `currentUpdatedAt` 이 달라지면 띠가 표시된다.
 * - 사용자가 "최신 불러오기" 를 누르면 `onReload` 를 호출하고, 다시 감지 기준 시각을 갱신한다.
 * - React Compiler 규칙상 `useCallback` 를 쓰지 않고 `useRef` 로 최신 `initialUpdatedAt` 을 추적한다.
 */
export function ChangeDetectionStrip({
  initialUpdatedAt,
  currentUpdatedAt,
  onReload
}: ChangeDetectionStripProps) {
  const baselineRef = useRef<string | null | undefined>(initialUpdatedAt);
  const [pending, setPending] = useState(false);

  // `initialUpdatedAt` 이 바뀌면 기준선을 갱신한다 — "최신 불러오기" 성공 후 호출자가 바꾼 경우.
  useEffect(() => {
    baselineRef.current = initialUpdatedAt;
  }, [initialUpdatedAt]);

  const baseline = baselineRef.current;
  const hasChange =
    Boolean(baseline) &&
    Boolean(currentUpdatedAt) &&
    baseline !== currentUpdatedAt;

  if (!hasChange) {
    return null;
  }

  const handleReload = async () => {
    setPending(true);
    try {
      await onReload();
      // reload 성공 후 감지 기준을 최신으로 끌어올려 띠가 다시 뜨지 않게 한다.
      baselineRef.current = currentUpdatedAt;
    } finally {
      setPending(false);
    }
  };

  return (
    <div
      role="alert"
      data-testid="change-detection-strip"
      className="sticky top-0 z-10 flex items-center justify-between gap-3 rounded-2xl border border-transparent bg-[var(--status-warning-bg)] px-4 py-2 text-sm text-[var(--status-warning-text)]"
    >
      <div className="flex items-center gap-2">
        <AlertCircle size={16} aria-hidden="true" />
        <span>방금 다른 관리자가 저장했어요.</span>
      </div>
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={pending}
        onClick={() => {
          void handleReload();
        }}
      >
        {pending ? "불러오는 중..." : "최신 불러오기"}
      </Button>
    </div>
  );
}
