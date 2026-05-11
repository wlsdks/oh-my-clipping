import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const SLACK_MEMBER_ID_PATTERN = /^U[A-Z0-9]{7,14}$/;

type SlackConnectContext = "dm" | "private-channel";

const CONTEXT_TEXT: Record<SlackConnectContext, { title: string; description: string; button: string }> = {
  dm: {
    title: "Slack DM을 받으려면 멤버 ID가 필요해요",
    description:
      "Slack 멤버 ID를 입력하면 바로 DM 구독이 시작돼요. 한 번만 설정하면 다음부터는 바로 구독할 수 있어요.",
    button: "연결하고 구독하기"
  },
  "private-channel": {
    title: "비공개 채널을 보려면 Slack 연동이 필요해요",
    description:
      "본인이 참여한 비공개 채널만 표시하기 위해 Slack 멤버 ID가 필요해요. 한 번만 설정하면 돼요.",
    button: "연동하기"
  }
};

interface SlackConnectModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (slackMemberId: string) => Promise<void>;
  isSubmitting: boolean;
  context?: SlackConnectContext;
}

export function SlackConnectModal({
  open,
  onOpenChange,
  onSubmit,
  isSubmitting,
  context = "dm"
}: SlackConnectModalProps) {
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const text = CONTEXT_TEXT[context];

  const handleSubmit = () => {
    const trimmed = value.trim().toUpperCase();
    if (!trimmed) {
      setError("Slack 멤버 ID를 입력해 주세요");
      return;
    }
    if (!SLACK_MEMBER_ID_PATTERN.test(trimmed)) {
      setError("U로 시작하는 멤버 ID를 입력해 주세요 (예: U01AB2CD3EF)");
      return;
    }
    setError(null);
    onSubmit(trimmed);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{text.title}</DialogTitle>
          <DialogDescription>{text.description}</DialogDescription>
        </DialogHeader>

        <div className="space-y-3 py-2">
          <div>
            <Input
              placeholder="U01AB2CD3EF"
              value={value}
              onChange={(e) => {
                setValue(e.target.value);
                if (error) setError(null);
              }}
              aria-label="Slack 멤버 ID"
            />
            {error && (
              <p className="text-sm text-destructive mt-1.5" aria-live="polite">
                {error}
              </p>
            )}
          </div>

          <div className="rounded-lg bg-muted/50 p-3 space-y-1">
            <p className="text-sm font-medium text-foreground">멤버 ID 찾는 법</p>
            <ol className="text-sm text-muted-foreground list-decimal list-inside space-y-0.5">
              <li>Slack에서 본인 프로필 클릭</li>
              <li>⋯ 더보기 &rarr; &quot;멤버 ID 복사&quot;</li>
            </ol>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? "연결 중..." : text.button}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
