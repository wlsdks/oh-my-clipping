import { useEffect, useState } from "react";
import { Lock } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { SlackChannelItem } from "@/types/runtime";
import { REASON_MAX } from "./blockedChannelConstants";

interface Props {
  channel: SlackChannelItem | null;
  isSubmitting: boolean;
  onCancel: () => void;
  onConfirm: (reason: string | null) => void;
}

/** 채널 차단 사유 입력 다이얼로그. 사유는 선택 입력이며 REASON_MAX 초과 시 저장 불가. */
export function BlockReasonDialog({ channel, isSubmitting, onCancel, onConfirm }: Props) {
  const [reason, setReason] = useState("");

  // 다이얼로그가 새 채널로 열릴 때마다 사유 초기화
  useEffect(() => {
    if (channel) setReason("");
  }, [channel]);

  const open = channel !== null;
  const isOverLimit = reason.length > REASON_MAX;

  function handleOpenChange(next: boolean) {
    if (!next && !isSubmitting) onCancel();
  }

  function handleConfirm() {
    const trimmed = reason.trim().slice(0, REASON_MAX);
    onConfirm(trimmed || null);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>채널 차단</DialogTitle>
          <DialogDescription asChild>
            <span className="flex items-center gap-1.5">
              {channel?.isPrivate ? <Lock className="h-3.5 w-3.5" /> : <span>#</span>}
              {channel?.name ?? ""}
            </span>
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1">
          <Label htmlFor="block-reason-input">
            차단 사유 <span className="text-xs text-muted-foreground">(선택)</span>
          </Label>
          <Textarea
            id="block-reason-input"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="나중에 왜 차단했는지 알 수 있도록 짧게 메모하세요"
            className="min-h-[72px] resize-none"
            aria-label="차단 사유"
          />
          <div
            className={`text-xs text-right ${
              isOverLimit ? "text-destructive" : "text-muted-foreground"
            }`}
          >
            {reason.length} / {REASON_MAX}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" size="sm" onClick={onCancel} disabled={isSubmitting}>
            취소
          </Button>
          <Button size="sm" onClick={handleConfirm} disabled={isSubmitting || isOverLimit}>
            차단
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
