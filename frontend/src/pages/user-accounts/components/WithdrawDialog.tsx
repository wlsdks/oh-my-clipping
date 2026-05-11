import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import type { UserAccountApproval } from "@/types/user";

interface WithdrawDialogState {
  item: UserAccountApproval;
  note: string;
  slackCleanupConfirmed: boolean;
}

interface WithdrawDialogProps {
  state: WithdrawDialogState | null;
  onClose: () => void;
  onNoteChange: (note: string) => void;
  onSlackCheckChange: (checked: boolean) => void;
  onSubmit: () => void;
  isWorking: boolean;
}

export function WithdrawDialog({ state, onClose, onNoteChange, onSlackCheckChange, onSubmit, isWorking }: WithdrawDialogProps) {
  if (!state) return null;

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>탈퇴 처리</DialogTitle>
          <DialogDescription className="sr-only">사용자를 탈퇴 처리합니다</DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <p className="text-sm text-muted-foreground">
            이 사용자를 탈퇴 처리하시겠어요? 탈퇴 시 계정이 비활성화되고,
            {state.item.subscriptionCount != null && state.item.subscriptionCount > 0
              ? ` 구독 ${state.item.subscriptionCount}건이 함께 해제돼요`
              : state.item.subscriptionCount === 0
                ? " 활성 구독이 없어요."
                : " 활성 구독이 모두 해제돼요"}
          </p>
          <div className="rounded-lg bg-muted/50 p-3 text-sm space-y-1">
            <p><span className="font-medium">아이디:</span> {state.item.username}</p>
            <p><span className="font-medium">표시 이름:</span> {state.item.displayName ?? "-"}</p>
            <p><span className="font-medium">부서:</span> {state.item.department ?? "-"}</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="withdraw-note">메모 (선택)</Label>
            <Textarea id="withdraw-note" rows={3} value={state.note} onChange={(e) => onNoteChange(e.target.value)} placeholder="예: 퇴사로 인한 탈퇴 처리" />
          </div>
          <label className="flex items-start gap-3 cursor-pointer select-none">
            <input type="checkbox" checked={state.slackCleanupConfirmed} onChange={(e) => onSlackCheckChange(e.target.checked)} className="mt-0.5 h-4 w-4 rounded border-gray-300 accent-primary" />
            <span className="text-sm">Slack 채널에서 해당 사용자 관련 설정을 정리했어요</span>
          </label>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isWorking}>취소</Button>
          <Button variant="destructive" onClick={onSubmit} disabled={isWorking || !state.slackCleanupConfirmed}>
            {isWorking ? "처리 중…" : "탈퇴 처리 확정"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export type { WithdrawDialogState };
