import { useRef } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

interface SourceDeactivateConfirmDialogProps {
  open: boolean;
  /**
   * 일시중지 대상 소스.
   * sourceId 가 null 인 수동 URL 행은 호출측에서 가드해야 한다 — 이 컴포넌트는 non-null 을 가정한다.
   */
  source: { sourceId: string; sourceName: string };
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * 소스 수집 일시중지 확인 다이얼로그.
 *
 * crawl_approved 토글과 혼동되지 않도록 "일시중지" 용어로 통일한다.
 * 이미 수집된 기사는 유지되며, 재활성화 시 실패 카운트는 초기화된다.
 *
 * 접근성:
 * - Radix Dialog 가 focus trap + ESC 키 + overlay 클릭 처리
 * - destructive 다이얼로그 관례에 따라 initial focus 는 취소 버튼
 * - `aria-describedby` 는 Radix 가 DialogDescription 을 자동 연결
 */
export function SourceDeactivateConfirmDialog({
  open,
  source,
  onConfirm,
  onCancel,
}: SourceDeactivateConfirmDialogProps) {
  // destructive 다이얼로그 기본 포커스 = 취소 (실수 확정 방지)
  const cancelRef = useRef<HTMLButtonElement>(null);

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent
        className="sm:max-w-[420px]"
        data-testid="source-deactivate-dialog"
        onOpenAutoFocus={(event) => {
          // Radix 기본 포커스를 취소 버튼으로 리다이렉트
          event.preventDefault();
          cancelRef.current?.focus();
        }}
      >
        <DialogHeader>
          <DialogTitle>수집 일시중지</DialogTitle>
          <DialogDescription asChild>
            <div className="space-y-2 text-sm text-muted-foreground">
              <p>
                <span className="font-semibold text-foreground">{source.sourceName}</span>
                {" "}에서 더 이상 기사를 수집하지 않습니다. 이미 수집된 기사는 유지됩니다.
              </p>
              <p>
                언제든 다시 켤 수 있고, 재활성화 시 실패 카운트가 초기화됩니다.
              </p>
            </div>
          </DialogDescription>
        </DialogHeader>

        <DialogFooter>
          <Button ref={cancelRef} variant="outline" onClick={onCancel}>
            취소
          </Button>
          <Button
            variant="destructive"
            onClick={onConfirm}
            data-testid="source-deactivate-confirm"
          >
            수집 일시중지
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
