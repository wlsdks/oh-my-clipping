import { useState, useRef, useEffect } from "react";
import { AlertCircle, Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { formatRelativeDate } from "@/utils/date";
import type { StaleEditInfo } from "@/shared/types/common";

interface StaleEditResolveModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  staleEditInfo: StaleEditInfo | null;
  /** "최신 불러오기" 클릭 시 실행할 refetch 콜백. */
  onReload: () => void | Promise<void>;
}

const MAX_VISIBLE_FIELDS = 3;

/** 변경 필드명을 사람이 읽기 좋은 한 줄 요약으로 만든다. */
function summarizeChangedFields(fields: string[]): string {
  if (!fields || fields.length === 0) return "설정 일부";
  if (fields.length <= MAX_VISIBLE_FIELDS) return fields.join(", ");
  const visible = fields.slice(0, MAX_VISIBLE_FIELDS).join(", ");
  const remaining = fields.length - MAX_VISIBLE_FIELDS;
  return `${visible} 외 ${remaining}개`;
}

/**
 * Stale-edit(낙관적 잠금 충돌, 409 STALE_EDIT) 전용 해결 모달.
 *
 * - "최신 불러오기" (primary): 서버의 최신 값을 다시 불러오고 모달을 닫는다.
 * - "취소": 모달만 닫는다. 사용자의 폼 수정 내용은 화면에 그대로 남는다.
 *
 * force-overwrite(내 수정 유지)는 의도적으로 제공하지 않는다.
 * 모던 미니멀 원칙에 따라 초기 포커스는 비파괴 액션인 "취소"에 둔다.
 */
export function StaleEditResolveModal({
  open,
  onOpenChange,
  staleEditInfo,
  onReload
}: StaleEditResolveModalProps) {
  const [isReloading, setIsReloading] = useState(false);
  const cancelRef = useRef<HTMLButtonElement>(null);

  // 모달이 닫힐 때 로딩 상태를 초기화해 재진입 시 잔상이 남지 않게 한다.
  useEffect(() => {
    if (!open) setIsReloading(false);
  }, [open]);

  async function handleReload() {
    if (isReloading) return;
    setIsReloading(true);
    try {
      await onReload();
    } finally {
      setIsReloading(false);
    }
  }

  const editorName = staleEditInfo?.latestEditorName || "관리자";
  const relative = staleEditInfo ? formatRelativeDate(staleEditInfo.latestUpdatedAt) : "";
  const fieldSummary = summarizeChangedFields(staleEditInfo?.changedFieldNames ?? []);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-[420px]"
        // 초기 포커스를 비파괴 액션(취소)으로 옮긴다.
        onOpenAutoFocus={(e) => {
          e.preventDefault();
          cancelRef.current?.focus();
        }}
      >
        <DialogHeader>
          <div className="flex items-center gap-2 text-[var(--status-warning-text)]">
            <AlertCircle className="h-5 w-5 shrink-0" aria-hidden />
            <DialogTitle>편집하는 동안 누가 먼저 저장했어요</DialogTitle>
          </div>
          <DialogDescription className="sr-only">
            최신 내용을 불러오거나 취소할 수 있어요
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 text-sm text-foreground">
          <p>
            {relative && (
              <span className="text-muted-foreground">{relative}에 </span>
            )}
            <span className="font-medium">{editorName}</span>
            <span className="text-muted-foreground">님이 </span>
            <span className="font-medium">{fieldSummary}</span>
            <span className="text-muted-foreground">을(를) 바꿨어요.</span>
          </p>
          <p className="text-xs text-muted-foreground">
            내가 입력한 내용은 화면에 그대로 남아 있어요. 최신 내용을 먼저 확인한 뒤에 다시 저장해 주세요.
          </p>
        </div>

        <DialogFooter className="mt-2 flex-row justify-end gap-2">
          <Button
            ref={cancelRef}
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isReloading}
          >
            취소
          </Button>
          <Button onClick={handleReload} disabled={isReloading}>
            {isReloading ? (
              <>
                <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                불러오는 중...
              </>
            ) : (
              "최신 불러오기"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

