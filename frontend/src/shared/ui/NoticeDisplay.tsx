import type { Notice } from "../types/common";
import { Banner } from "./Banner";
import { Toast } from "./Toast";

interface ToastAction {
  label: string;
  onClick: () => void;
}

interface NoticeDisplayProps {
  notice: Notice | null;
  onDismiss: () => void;
  toastAction?: ToastAction;
}

/** 성공 메시지는 상단 중앙 Toast로, 나머지(info/warning/error)는 상단 Banner로 표시 */
export function NoticeDisplay({ notice, onDismiss, toastAction }: NoticeDisplayProps) {
  if (!notice) return null;
  if (notice.type === "success") {
    return <Toast text={notice.text} action={toastAction} onDismiss={onDismiss} />;
  }
  return <Banner type={notice.type}>{notice.text}</Banner>;
}
