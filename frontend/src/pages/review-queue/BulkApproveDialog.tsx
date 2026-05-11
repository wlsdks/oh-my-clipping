import { useEffect, useRef, useState } from "react";
import { AlertTriangle } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { HIGH_CONFIDENCE_SCORE } from "./model/constants";
import type { ReviewQueueItem } from "@/types/review";

interface BulkApproveDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  items: ReviewQueueItem[];
  action: "approve" | "exclude";
  isPending: boolean;
  onConfirm: () => void;
}

const ACTION_COPY: Record<"approve" | "exclude", { title: string; description: string; confirm: string }> = {
  approve: {
    title: "선택한 뉴스를 보내기로 일괄 처리할까요?",
    description: "다음 예정 발송에 포함돼요. 즉시 발송은 아니에요.",
    confirm: "보내기로 처리",
  },
  exclude: {
    title: "선택한 뉴스를 건너뛰기로 일괄 처리할까요?",
    description: "발송 대상에서 제외돼요. 나중에 검토 상태로 되돌릴 수 있어요.",
    confirm: "건너뛰기로 처리",
  },
};

/**
 * 간단한 결정적 샘플 추출 — 각 렌더마다 동일 순서(요소 가운데·양끝)를 보여줘 예측 가능하게 한다.
 * 정렬/필터 결과 순서를 그대로 신뢰하며, 3개 미만이면 전부 노출한다.
 */
function pickSamples(items: ReviewQueueItem[]): ReviewQueueItem[] {
  if (items.length <= 3) return items;
  const first = items[0];
  const mid = items[Math.floor(items.length / 2)];
  const last = items[items.length - 1];
  return [first, mid, last];
}

const SUGGESTED_LABEL: Record<string, string> = {
  INCLUDE: "AI 보내기 추천",
  EXCLUDE: "AI 건너뛰기 추천",
  REVIEW: "AI 추가 검토 필요",
};

const SUGGESTED_STYLE: Record<string, string> = {
  INCLUDE: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]",
  EXCLUDE: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]",
  REVIEW: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]",
};

export function BulkApproveDialog({
  open,
  onOpenChange,
  items,
  action,
  isPending,
  onConfirm,
}: BulkApproveDialogProps) {
  // 다이얼로그가 열릴 때마다 확인 체크박스 상태 초기화 (rubber-stamping 방지 핵심 기믹)
  const [acknowledged, setAcknowledged] = useState(false);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      setAcknowledged(false);
      // 파괴적이지 않은 "취소" 버튼에 초기 포커스 (엣지 4-5)
      const timer = setTimeout(() => cancelRef.current?.focus(), 0);
      return () => clearTimeout(timer);
    }
  }, [open]);

  const copy = ACTION_COPY[action];
  const samples = pickSamples(items);
  const lowConfidenceItems = items.filter((i) => i.importanceScore < HIGH_CONFIDENCE_SCORE);
  const hasLowConfidence = lowConfidenceItems.length > 0;

  const canConfirm = acknowledged && !isPending && items.length > 0;

  return (
    <Dialog open={open} onOpenChange={isPending ? undefined : onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>
            선택한 {items.length}건을 일괄 처리합니다. {copy.description}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* 저신뢰 경고 배너 */}
          {hasLowConfidence && (
            <div
              className="flex items-start gap-2 rounded-lg border border-[var(--status-warning-bg)] bg-[var(--status-warning-bg)] px-3 py-2 text-xs text-[var(--status-warning-text)]"
              role="alert"
            >
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="font-medium">검토 필요 항목이 {lowConfidenceItems.length}건 섞여 있어요</p>
                <p className="mt-0.5 opacity-80">
                  중요도 점수가 {HIGH_CONFIDENCE_SCORE.toFixed(1)} 미만이에요. 개별 확인을 권장해요.
                </p>
              </div>
            </div>
          )}

          {/* 샘플 확인 블록 */}
          <div className="rounded-xl border bg-muted/30 p-3 space-y-2">
            <p className="text-xs font-medium text-foreground">샘플 {samples.length}건 확인</p>
            <ul className="space-y-2">
              {samples.map((item) => (
                <li key={item.summaryId} className="rounded-lg bg-card p-3 text-sm">
                  <div className="flex items-start justify-between gap-3">
                    <p className="font-medium text-foreground line-clamp-2 flex-1">{item.title}</p>
                    <span
                      className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] ${
                        SUGGESTED_STYLE[item.suggestedStatus] ?? SUGGESTED_STYLE.REVIEW
                      }`}
                    >
                      {SUGGESTED_LABEL[item.suggestedStatus] ?? "AI 검토"}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {item.categoryName} · 중요도 {item.importanceScore.toFixed(2)}
                  </p>
                </li>
              ))}
            </ul>
            {items.length > samples.length && (
              <p className="text-xs text-muted-foreground">
                나머지 {items.length - samples.length}건도 이와 유사해요.
              </p>
            )}
          </div>

          {/* 확인 체크박스 */}
          <label className="flex items-start gap-3 rounded-lg border bg-card px-3 py-2.5 cursor-pointer select-none">
            <Checkbox
              checked={acknowledged}
              onCheckedChange={(checked) => setAcknowledged(checked === true)}
              disabled={isPending}
              aria-label="샘플을 확인했어요"
            />
            <span className="text-sm text-foreground leading-tight">
              샘플을 확인했어요. 위 {items.length}건에 일괄 적용해도 괜찮아요.
            </span>
          </label>
        </div>

        <DialogFooter>
          <Button
            ref={cancelRef}
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isPending}
          >
            취소
          </Button>
          <Button onClick={onConfirm} disabled={!canConfirm}>
            {isPending ? "처리 중..." : copy.confirm}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
