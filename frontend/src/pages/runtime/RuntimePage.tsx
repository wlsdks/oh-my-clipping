import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CheckCircle2, AlertTriangle } from "lucide-react";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { runtimeKeys } from "@/queries/runtimeKeys";
import { runtimeService } from "@/services/runtimeService";
import type { RuntimeSettingsUpdateRequest } from "@/services/runtimeService";
import { Button } from "@/components/ui/button";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { formatKoreanDateTime } from "@/utils/date";
import { SlackConnectionCard } from "./SlackConnectionCard";
import { CollectionSettingsCard } from "./CollectionSettingsCard";
import { AutoDigestSettingsCard } from "./AutoDigestSettingsCard";
import { SlackBlockedChannelPanel } from "./SlackBlockedChannelPanel";
import { RalphSettingsCard } from "./RalphSettingsCard";
import { ReviewQueueFeatureCard } from "./ReviewQueueFeatureCard";
import { OpsNotificationSection } from "./OpsNotificationSection";
import { DataRetentionCard } from "./DataRetentionCard";

export function RuntimePage() {
  const qc = useQueryClient();
  const [resetConfirmOpen, setResetConfirmOpen] = useState(false);

  const { data: settings, isLoading } = useQuery({
    queryKey: runtimeKeys.configs(),
    queryFn: () => runtimeService.getSettings(),
  });

  const { mutate: updateSettings, isPending: isSaving } = useMutation({
    mutationFn: (data: RuntimeSettingsUpdateRequest) => runtimeService.updateSettings(data),
    onSuccess: (updated) => {
      qc.setQueryData(runtimeKeys.configs(), updated);
      toast.success("설정을 저장했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "저장하지 못했어요")),
  });

  const { mutate: resetSettings, isPending: isResetting } = useMutation({
    mutationFn: runtimeService.resetSettings,
    onSuccess: (updated) => {
      qc.setQueryData(runtimeKeys.configs(), updated);
      toast.success("기본값으로 복원했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "복원하지 못했어요")),
  });

  if (isLoading) {
    return (
      <div className="p-4 sm:p-6 space-y-5" role="status" aria-live="polite">
        <span className="sr-only">불러오는 중...</span>
        <div className="flex items-center justify-between">
          <div className="space-y-2">
            <div className="h-7 w-32 rounded bg-muted animate-pulse" />
            <div className="h-4 w-64 rounded bg-muted animate-pulse" />
          </div>
          <div className="h-9 w-24 rounded bg-muted animate-pulse" />
        </div>
        <div className="h-12 rounded-lg bg-muted animate-pulse" />
        <div className="h-48 rounded-2xl bg-muted animate-pulse" />
        <div className="h-48 rounded-2xl bg-muted animate-pulse" />
      </div>
    );
  }

  if (!settings) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-destructive">설정을 불러오지 못했어요</p>
        <Button variant="outline" size="sm" onClick={() => qc.invalidateQueries({ queryKey: runtimeKeys.configs() })}>
          다시 시도
        </Button>
      </div>
    );
  }

  const slackConnected = settings.slackBotTokenConfigured;

  return (
    <>
      <div className="p-4 sm:p-6 space-y-5">
        {/* 헤더 */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">시스템 설정</h1>
            <p className="text-sm text-muted-foreground mt-1">
              Slack 연결과 파이프라인 기본값을 관리하세요
              {settings.updatedAt && (
                <span className="ml-2 text-xs">
                  · 마지막 수정: {formatKoreanDateTime(settings.updatedAt)}
                </span>
              )}
            </p>
          </div>
          <Button
            variant="outline"
            onClick={() => setResetConfirmOpen(true)}
            disabled={isResetting || isSaving}
          >
            기본값 복원
          </Button>
        </div>

        {/* Slack 연결 상태 배너 */}
        {slackConnected ? (
          <div className="flex items-center gap-2.5 rounded-lg border border-[var(--status-success-bg)] bg-[var(--status-success-bg)] px-4 py-3 text-sm text-[var(--status-success-text)]">
            <CheckCircle2 className="h-4 w-4 shrink-0" />
            <span className="font-medium">Slack 연결됨</span>
            <span>— 봇 토큰이 설정되어 있어요</span>
          </div>
        ) : (
          <div className="flex items-center gap-2.5 rounded-lg border border-[var(--status-warning-bg)] bg-[var(--status-warning-bg)] px-4 py-3 text-sm text-[var(--status-warning-text)]">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span className="font-medium">Slack 미연결</span>
            <span>— 봇 토큰을 설정하세요</span>
          </div>
        )}

        {/* 섹션별 카드 */}
        <SlackConnectionCard settings={settings} isSaving={isSaving} onSave={updateSettings} />
        <CollectionSettingsCard settings={settings} isSaving={isSaving} onSave={updateSettings} />
        <DataRetentionCard settings={settings} isSaving={isSaving} onSave={updateSettings} />
        <AutoDigestSettingsCard
          settings={settings}
          isSaving={isSaving}
          onSave={updateSettings}
          slackConnected={slackConnected}
        />
        <SlackBlockedChannelPanel slackConnected={slackConnected} />
        <ReviewQueueFeatureCard settings={settings} isSaving={isSaving} onSave={updateSettings} />
        <RalphSettingsCard settings={settings} isSaving={isSaving} onSave={updateSettings} />
        <OpsNotificationSection settings={settings} isSaving={isSaving} onSave={updateSettings} />

      </div>

      {/* 기본값 복원 확인 모달 */}
      <ConfirmModal
        open={resetConfirmOpen}
        onOpenChange={setResetConfirmOpen}
        title="시스템 기본값을 복원할까요?"
        description="파이프라인, 자동 발송, Ralph 설정이 초기 상태로 되돌아가요. 봇 토큰과 차단 채널은 유지돼요."
        confirmLabel="복원"
        variant="destructive"
        onConfirm={() => resetSettings()}
      />
    </>
  );
}
