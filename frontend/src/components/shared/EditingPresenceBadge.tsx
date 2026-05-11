import { UserRound } from "lucide-react";
import { relativeTime } from "@/utils/date";
import type { EditingSession } from "@/types/editingPresence";

interface EditingPresenceBadgeProps {
  /** useEditingPresence().otherEditors — 본인은 이미 제외된 상태로 전달된다. */
  editors: EditingSession[];
}

/**
 * "다른 관리자 N명이 편집 중이에요" 배지.
 *
 * 편집 모달 상단에 배치해, 충돌 저장 이전에 사전 경고하는 용도다.
 * editors 가 비어 있으면 아무것도 렌더링하지 않는다.
 */
export function EditingPresenceBadge({ editors }: EditingPresenceBadgeProps) {
  if (editors.length === 0) {
    return null;
  }

  const primary = editors[0];
  const extra = editors.length - 1;
  const message =
    extra > 0
      ? `${primary.displayName}님 외 ${extra}명이 ${relativeTime(primary.startedAt)}부터 편집 중이에요`
      : `${primary.displayName}님이 ${relativeTime(primary.startedAt)}부터 편집 중이에요`;

  return (
    <div
      role="status"
      aria-live="polite"
      data-testid="editing-presence-badge"
      className="flex items-center gap-2 rounded-full border border-transparent bg-[var(--status-warning-bg)] px-3 py-1.5 text-sm text-[var(--status-warning-text)]"
    >
      <UserRound size={14} aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}
