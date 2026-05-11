import type { Persona } from "@/types/persona";
import { formatPersonaDisplayName } from "@/shared/lib/personaLabels";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

interface PersonaDeleteDialogProps {
  /** 삭제 대상 persona. null 이면 dialog 가 닫힌 상태 */
  persona: Persona | null;
  /** 실제 삭제 API 호출 중 여부 */
  deleting: boolean;
  /** dialog close 콜백 */
  onClose: () => void;
  /** 삭제 확정 콜백 */
  onConfirm: () => void;
}

/**
 * 저장된 내 스타일 삭제 전 확인 dialog.
 * 409 Conflict (구독에서 사용 중) 에러 안내는 호출측 toast 에서 처리한다.
 */
export function PersonaDeleteDialog({
  persona,
  deleting,
  onClose,
  onConfirm
}: PersonaDeleteDialogProps) {
  return (
    <Dialog
      open={Boolean(persona)}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>내 스타일을 삭제할까요?</DialogTitle>
          <DialogDescription className="sr-only">저장된 요약 스타일을 삭제합니다</DialogDescription>
        </DialogHeader>
        {persona && (
          <p className="text-sm text-muted-foreground">
            &quot;{formatPersonaDisplayName(persona)}&quot; 스타일을 삭제하면 되돌릴 수 없어요.
          </p>
        )}
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button variant="destructive" onClick={onConfirm} disabled={deleting}>
            {deleting ? "삭제 중..." : "삭제"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
