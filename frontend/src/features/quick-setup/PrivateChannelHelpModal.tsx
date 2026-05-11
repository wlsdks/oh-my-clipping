import { useQueryClient } from "@tanstack/react-query";
import { runtimeKeys } from "@/queries/runtimeKeys";

interface PrivateChannelHelpModalProps {
  open: boolean;
  onClose: () => void;
}

/**
 * 비공개 채널이 목록에 없을 때 봇 추가/제거 가이드와 목록 새로고침 버튼을 제공한다.
 */
export function PrivateChannelHelpModal({ open, onClose }: PrivateChannelHelpModalProps) {
  const queryClient = useQueryClient();

  if (!open) return null;

  function handleRefresh() {
    queryClient.invalidateQueries({ queryKey: runtimeKeys.slackChannels("private_channel") });
    onClose();
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true">
      <div className="bg-background rounded-2xl p-6 w-full max-w-sm shadow-xl space-y-4">
        <h2 className="text-base font-semibold">비공개 채널이 보이지 않나요?</h2>
        <p className="text-sm text-muted-foreground">
          비공개 채널은 Clipping 봇을 채널에 먼저 추가해야 목록에 보여요.
        </p>

        <div className="space-y-3">
          <div className="p-3 rounded-lg bg-muted/50 space-y-1">
            <p className="text-xs font-medium">채널에 봇 추가하기</p>
            <ol className="text-xs text-muted-foreground list-decimal list-inside space-y-0.5">
              <li>Slack에서 비공개 채널로 이동</li>
              <li>채널 이름 클릭 → 설정 편집 → 통합 탭</li>
              <li>앱 추가 → "Clipping" 검색 후 추가</li>
            </ol>
          </div>

          <div className="p-3 rounded-lg bg-muted/50 space-y-1">
            <p className="text-xs font-medium">채널에서 봇 제거하기</p>
            <p className="text-xs text-muted-foreground">
              채널 메시지 입력창에 <code className="bg-muted px-1 rounded">/remove @Clipping</code> 입력
            </p>
          </div>
        </div>

        <div className="flex gap-2 pt-1">
          <button
            type="button"
            className="flex-1 px-4 py-2 text-sm bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
            onClick={handleRefresh}
          >
            새로고침
          </button>
          <button
            type="button"
            className="px-4 py-2 text-sm border border-border rounded-lg hover:bg-muted transition-colors"
            onClick={onClose}
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
