import type { LucideIcon } from "lucide-react";
import { CheckCircle, AlertTriangle, XCircle, Pause, PauseCircle } from "lucide-react";
import type { Category } from "@/types/category";
import type { SubscriptionFilter } from "./types";

// ── Category status helpers (copied from categories/model) ──

export type CategoryStatusType = "success" | "warning" | "danger" | "neutral";

export interface CategoryStatusInfo {
  label: string;
  type: CategoryStatusType;
  icon: LucideIcon;
  desc: string;
}

export function getCategoryStatus(c: Category): CategoryStatusInfo {
  // status 기반 일시정지 체크 (최우선)
  if (c.status === "PAUSED") {
    return { label: "일시정지", type: "warning", icon: PauseCircle, desc: "일시정지된 상태" };
  }
  if (!c.isActive) {
    return { label: "비활성", type: "neutral", icon: Pause, desc: "수집이 중단된 상태" };
  }
  if (c.sourceCount > 0 && c.errorSourceCount === c.sourceCount) {
    return { label: "오류", type: "danger", icon: XCircle, desc: "전체 소스 수집 실패" };
  }
  if (c.sourceCount === 0 || c.subscriberCount === 0 || c.errorSourceCount > 0) {
    return { label: "주의", type: "warning", icon: AlertTriangle, desc: statusWarningDesc(c) };
  }
  return { label: "정상", type: "success", icon: CheckCircle, desc: "정상 운영 중" };
}

function statusWarningDesc(c: Category): string {
  if (c.sourceCount === 0) return "연결된 소스가 없어요";
  if (c.subscriberCount === 0) return "구독자가 없어요";
  return `소스 ${c.errorSourceCount}개 수집 오류`;
}

export const STATUS_STYLES: Record<
  CategoryStatusType,
  { bg: string; text: string; border: string; dotColor: string }
> = {
  success: {
    bg: "bg-[var(--status-success-bg)]",
    text: "text-[var(--status-success-text)]",
    border: "border-[var(--border-default)]",
    dotColor: "bg-[var(--status-success-text)]",
  },
  warning: {
    bg: "bg-[var(--status-warning-bg)]",
    text: "text-[var(--status-warning-text)]",
    border: "border-[var(--border-default)]",
    dotColor: "bg-[var(--status-warning-text)]",
  },
  danger: {
    bg: "bg-[var(--status-danger-bg)]",
    text: "text-[var(--status-danger-text)]",
    border: "border-[var(--border-default)]",
    dotColor: "bg-[var(--status-danger-text)]",
  },
  neutral: {
    bg: "bg-[var(--status-neutral-bg)]",
    text: "text-[var(--status-neutral-text)]",
    border: "border-[var(--border-default)]",
    dotColor: "bg-[var(--status-neutral-text)]",
  },
};

export const MAX_ITEMS_PRESETS = [3, 5, 10, 15, 20] as const;

// ── Chip filter definitions ──

export interface ChipFilterDef {
  value: SubscriptionFilter;
  label: string;
  dimmed?: boolean;
}

export const CHIP_FILTERS: ChipFilterDef[] = [
  { value: "pending", label: "대기" },
  { value: "active", label: "운영중" },
  { value: "warning", label: "주의" },
  { value: "danger", label: "오류" },
  { value: "inactive", label: "비활성" },
  { value: "public", label: "공개" },
  { value: "private", label: "비공개" },
  { value: "rejected", label: "반려", dimmed: true },
  { value: "withdrawn", label: "철회", dimmed: true },
];

export const REQUEST_STATUS_LABEL: Record<string, string> = {
  PENDING: "검토 대기",
  APPROVED: "승인",
  REJECTED: "반려",
  WITHDRAWN: "철회",
};
