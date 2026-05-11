import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Check, Loader2, X } from "lucide-react";

type ReviewMode = "approve" | "reject";

interface ReviewDialogState {
  mode: ReviewMode;
  note: string;
  error: string | null;
}

interface ReviewDialogProps {
  state: ReviewDialogState | null;
  onClose: () => void;
  onNoteChange: (note: string) => void;
  onSubmit: () => void;
  isWorking: boolean;
  bulkCount?: number;
}

export function ReviewDialog({ state, onClose, onNoteChange, onSubmit, isWorking, bulkCount }: ReviewDialogProps) {
  if (!state) return null;

  const isApprove = state.mode === "approve";
  const targetLabel = bulkCount ? `${bulkCount}건` : "";

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {isApprove ? "회원가입 승인" : "회원가입 반려"}
            {targetLabel && ` (${targetLabel})`}
          </DialogTitle>
          <DialogDescription className="sr-only">
            {isApprove ? "가입 요청을 승인합니다" : "가입 요청을 반려합니다"}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <p className="text-sm text-muted-foreground">
            {isApprove
              ? "승인 즉시 사용자가 로그인할 수 있어요."
              : "반려 사유는 로그인 화면 안내 문구로 전달돼요"}
          </p>
          {bulkCount && bulkCount > 50 && (
            <p className="text-sm text-[var(--status-warning-text)]">
              {bulkCount}건을 일괄 처리해요. 건수가 많으면 시간이 걸릴 수 있어요
            </p>
          )}
          <div className="space-y-2">
            <Label htmlFor="review-note">
              {isApprove ? "승인 메모 (선택)" : "반려 사유 (필수)"}
            </Label>
            <Textarea
              id="review-note"
              rows={4}
              value={state.note}
              onChange={(e) => onNoteChange(e.target.value)}
              placeholder={isApprove ? "예: 부서/소속 확인 완료" : "예: 부서 정보가 불명확하여 확인이 필요합니다."}
            />
            {state.error && <p className="text-sm text-destructive">{state.error}</p>}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isWorking}>취소</Button>
          <Button variant={isApprove ? "default" : "destructive"} onClick={onSubmit} disabled={isWorking}>
            {isWorking
              ? <Loader2 className="h-4 w-4 animate-spin mr-1" />
              : isApprove ? <Check className="h-4 w-4 mr-1" /> : <X className="h-4 w-4 mr-1" />
            }
            {isWorking ? "처리 중…" : isApprove ? "승인 확정" : "반려 확정"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export type { ReviewDialogState, ReviewMode };
