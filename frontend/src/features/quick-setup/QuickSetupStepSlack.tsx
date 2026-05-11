import { useState, useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle, Hash, MessageCircle, Lock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { runtimeService } from "@/services/runtimeService";
import { runtimeKeys } from "@/queries/runtimeKeys";
import { userService } from "@/services/userService";
import { categoryService } from "@/services/categoryService";
import { categoryKeys } from "@/queries/categoryKeys";
import { matchesKoreanSearch } from "@/utils/search";
import { useAuthStore } from "@/store/authStore";
import { SlackConnectModal } from "@/components/shared/SlackConnectModal";
import { PrivateChannelHelpModal } from "./PrivateChannelHelpModal";
import type { QuickSetupForm, SlackChannelType } from "./model/quickSetupTypes";
import type { SlackChannelItem } from "@/types/runtime";
import { cn } from "@/utils/cn";

interface QuickSetupStepSlackProps {
  form: QuickSetupForm;
  onChange: (updates: Partial<QuickSetupForm>) => void;
  disabled: boolean;
  isEditMode?: boolean;
  isUserMode?: boolean;
}

export function QuickSetupStepSlack({
  form,
  onChange,
  disabled,
  isEditMode,
  isUserMode
}: QuickSetupStepSlackProps) {
  const queryClient = useQueryClient();
  const isChannel = form.slackDeliveryMode === "channel";
  const channelType: SlackChannelType = form.slackChannelType ?? "public_channel";
  const [searchQuery, setSearchQuery] = useState("");
  const [helpModalOpen, setHelpModalOpen] = useState(false);
  const [connectModalOpen, setConnectModalOpen] = useState(false);
  const [connectSubmitting, setConnectSubmitting] = useState(false);
  const [guideOpen, setGuideOpen] = useState(false);
  const [refreshCooldown, setRefreshCooldown] = useState(false);

  // Slack DM 연동 상태를 반응적으로 구독
  const hasSlackDm = useAuthStore((s) => s.user?.hasSlackDm ?? false);

  // 채널 목록 — staleTime: Infinity로 마운트 시 한 번만 fetch
  const { data: channelResponse, isFetching: channelsFetching } = useQuery({
    queryKey: runtimeKeys.slackChannels(channelType),
    queryFn: () => runtimeService.listUserSetupSlackChannels(channelType),
    enabled: isChannel,
    staleTime: Infinity
  });

  // 유저 모드: 이미 승인된 구독의 채널 ID 목록 조회
  const { data: userRequests } = useQuery({
    queryKey: ["user-requests-for-channel-check"],
    queryFn: () => userService.listClippingRequests(),
    enabled: isChannel && (isUserMode ?? false),
    staleTime: 60_000
  });

  // 어드민 모드: 카테고리의 채널 ID 목록 조회
  const { data: allCategories } = useQuery({
    queryKey: categoryKeys.lists(),
    queryFn: () => categoryService.getAll(),
    enabled: isChannel && !(isUserMode ?? false),
    staleTime: 60_000
  });

  // 현재 수정 중인 채널을 제외한 점유 채널 ID 세트 계산
  const occupiedChannelIds: Set<string> = (() => {
    const editingChannelId = form.editCurrentSlackChannel ?? "";
    if (isUserMode) {
      const ids = (userRequests ?? [])
        .filter((r) => r.status === "APPROVED" && r.slackChannelId && r.slackChannelId !== editingChannelId)
        .map((r) => r.slackChannelId);
      return new Set(ids);
    }
    const ids = (allCategories ?? [])
      .filter((c) => c.slackChannelId && c.slackChannelId !== editingChannelId)
      .map((c) => c.slackChannelId as string);
    return new Set(ids);
  })();

  const channels = channelResponse?.channels ?? [];

  // 수정 모드: 기존 channelId로 channelType 자동 세팅 (아직 세팅 안 된 경우만)
  const { data: channelInfoData } = useQuery({
    queryKey: runtimeKeys.slackChannelInfo(form.slackChannelId),
    queryFn: () => runtimeService.getUserSetupSlackChannelInfo(form.slackChannelId),
    enabled: Boolean(isEditMode && isChannel && form.slackChannelId && !form.slackChannelType),
    staleTime: Infinity,
    retry: false
  });

  // channelInfoData가 로드되면 channelType 자동 세팅
  useEffect(() => {
    if (!channelInfoData || form.slackChannelType) return;
    onChange({ slackChannelType: channelInfoData.isPrivate ? "private_channel" : "public_channel" });
  }, [channelInfoData, form.slackChannelType, onChange]);

  // 비공개 채널 탭 + Slack 미연동 시 연동 프롬프트 표시
  const showConnectPrompt =
    channelType === "private_channel" &&
    isChannel &&
    (!hasSlackDm || channelResponse?.slackConnectRequired === true);

  const filteredChannels = channels.filter((ch: SlackChannelItem) =>
    matchesKoreanSearch(ch.name, searchQuery)
  );

  function handleModeChange(mode: "channel" | "dm") {
    onChange({
      slackDeliveryMode: mode,
      slackChannelId: "",
      slackChannelType: "public_channel",
      slackChannelConfirmed: mode === "dm"
    });
    setSearchQuery("");
  }

  function handleChannelTypeChange(type: SlackChannelType) {
    onChange({ slackChannelType: type, slackChannelId: "", slackChannelConfirmed: false });
    setSearchQuery("");
  }

  function handleSelectChannel(channel: SlackChannelItem) {
    onChange({ slackChannelId: channel.id, slackChannelConfirmed: true });
    setSearchQuery("");
  }

  function handleDeselect() {
    onChange({ slackChannelId: "", slackChannelConfirmed: false });
    setSearchQuery("");
  }

  // 공개 채널 목록 새로고침 (10초 쿨다운)
  async function handleRefreshChannels() {
    if (refreshCooldown) return;
    setRefreshCooldown(true);
    await queryClient.fetchQuery({
      queryKey: [...runtimeKeys.slackChannels(channelType), "refresh"],
      queryFn: () => runtimeService.listUserSetupSlackChannels(channelType, true),
    });
    await queryClient.invalidateQueries({ queryKey: runtimeKeys.slackChannels(channelType) });
    setTimeout(() => setRefreshCooldown(false), 10_000);
  }

  // Slack 멤버 ID 연동 처리
  async function handleSlackConnect(slackMemberId: string) {
    setConnectSubmitting(true);
    try {
      await userService.updateSlackMemberId(slackMemberId);
      const currentUser = useAuthStore.getState().user;
      if (currentUser) {
        useAuthStore.getState().login({ ...currentUser, hasSlackDm: true });
      }
      await queryClient.invalidateQueries({ queryKey: runtimeKeys.slackChannels("private_channel") });
      setConnectModalOpen(false);
    } finally {
      setConnectSubmitting(false);
    }
  }

  // 빈 상태 메시지 분기
  function getEmptyMessage(): string {
    if (searchQuery) return "검색 결과가 없어요.";
    if (channelType === "private_channel") {
      return "참여 중인 비공개 채널이 없거나 봇이 추가되지 않았어요.";
    }
    return "채널이 없어요.";
  }

  return (
    <div className="space-y-4 py-2">
      <h3 className="text-sm font-semibold">뉴스를 어디로 받을까요?</h3>

      {/* DM / 채널 모드 선택 */}
      <div className="flex gap-1">
        <button
          type="button"
          className={cn(
            "flex-1 py-1.5 px-3 text-xs rounded-md border transition-colors",
            isChannel
              ? "bg-primary text-primary-foreground border-primary"
              : "bg-background border-border hover:bg-muted"
          )}
          aria-pressed={isChannel}
          onClick={() => handleModeChange("channel")}
          disabled={disabled}
        >
          <Hash className="inline h-3.5 w-3.5" /> Slack 채널
        </button>
        <button
          type="button"
          className={cn(
            "flex-1 py-1.5 px-3 text-xs rounded-md border transition-colors",
            !isChannel
              ? "bg-primary text-primary-foreground border-primary"
              : "bg-background border-border hover:bg-muted"
          )}
          aria-pressed={!isChannel}
          onClick={() => handleModeChange("dm")}
          disabled={disabled}
        >
          <MessageCircle className="inline h-3.5 w-3.5" /> 나에게 DM
        </button>
      </div>

      {isChannel && (
        <div className="space-y-3">
          {/* 확정된 채널 표시 */}
          {form.slackChannelConfirmed && form.slackChannelId ? (
            <div className="flex items-center gap-2 p-3 rounded-lg bg-[var(--status-success-bg)] border border-[var(--status-success-bg)]">
              <CheckCircle className="h-4 w-4 text-[var(--status-success-text)] shrink-0" />
              <p className="flex-1 text-sm flex items-center gap-1">
                {channelType === "private_channel" ? <Lock className="h-3.5 w-3.5" /> : "# "}
                {channels.find((c: SlackChannelItem) => c.id === form.slackChannelId)?.name ?? form.slackChannelId}
              </p>
              <button
                type="button"
                className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                onClick={handleDeselect}
                disabled={disabled}
              >
                변경
              </button>
            </div>
          ) : (
            <>
              {/* 공개/비공개 채널 타입 선택 */}
              <div className="flex gap-1">
                <button
                  type="button"
                  className={cn(
                    "flex-1 py-1 px-3 text-xs rounded-md border transition-colors",
                    channelType === "public_channel"
                      ? "bg-primary text-primary-foreground border-primary"
                      : "bg-background border-border hover:bg-muted"
                  )}
                  onClick={() => handleChannelTypeChange("public_channel")}
                  disabled={disabled}
                >
                  공개 채널
                </button>
                <button
                  type="button"
                  className={cn(
                    "flex-1 py-1 px-3 text-xs rounded-md border transition-colors",
                    channelType === "private_channel"
                      ? "bg-primary text-primary-foreground border-primary"
                      : "bg-background border-border hover:bg-muted"
                  )}
                  onClick={() => handleChannelTypeChange("private_channel")}
                  disabled={disabled}
                >
                  <Lock className="inline h-3.5 w-3.5" /> 비공개 채널
                </button>
              </div>

              {/* 비공개 채널 연동 프롬프트 */}
              {showConnectPrompt ? (
                <div className="rounded-lg border border-border bg-muted/30 p-4 space-y-2 text-center">
                  <p className="text-sm text-muted-foreground">
                    비공개 채널 목록을 보려면 Slack 연동이 필요해요
                  </p>
                  <button
                    type="button"
                    className="px-4 py-1.5 text-sm font-medium rounded-full bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
                    onClick={() => setConnectModalOpen(true)}
                  >
                    Slack 연동하기
                  </button>
                </div>
              ) : (
                <>
                  {/* 채널 검색 + 목록 */}
                  <div className="space-y-1.5">
                    {/* 공개 채널 가이드 배너 */}
                    {channelType === "public_channel" && (
                      <div className="rounded-lg border border-border bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
                        <div className="flex items-center justify-between">
                          <span>ℹ️ 봇이 초대된 채널만 표시됩니다.</span>
                          <button
                            type="button"
                            className="text-primary underline underline-offset-2 hover:text-primary/80 transition-colors"
                            onClick={() => setGuideOpen(!guideOpen)}
                          >
                            {guideOpen ? "닫기" : "채널 추가 방법"}
                          </button>
                        </div>
                        {guideOpen && (
                          <div className="mt-2 space-y-1.5 border-t border-border pt-2">
                            <ol className="list-decimal list-inside space-y-1">
                              <li>Slack에서 새 채널을 만드세요</li>
                              <li><code className="text-[11px] bg-muted px-1 py-0.5 rounded">/invite @클리핑봇</code> 으로 봇을 초대하세요</li>
                              <li>아래 버튼을 눌러 목록을 새로고침하세요</li>
                            </ol>
                            <p className="text-[11px] text-muted-foreground/70">
                              초대가 안 되면 Slack 워크스페이스 관리자에게 문의하세요.
                            </p>
                            <button
                              type="button"
                              className="flex items-center gap-1 px-2.5 py-1 text-xs rounded-md border border-border bg-background hover:bg-muted transition-colors disabled:opacity-50"
                              onClick={handleRefreshChannels}
                              disabled={refreshCooldown || channelsFetching}
                            >
                              🔄 {refreshCooldown ? "잠시 후 다시 시도하세요" : "새로고침"}
                            </button>
                          </div>
                        )}
                      </div>
                    )}
                    <input
                      type="text"
                      className="w-full px-3 py-2 text-sm border border-border rounded-md bg-background focus:outline-none focus:ring-1 focus:ring-ring"
                      placeholder="채널 이름 검색 (초성 검색 가능)"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      disabled={disabled || channelsFetching}
                      aria-label="채널 검색"
                    />

                    {channelsFetching ? (
                      <p className="text-xs text-muted-foreground px-1">채널 목록 불러오는 중...</p>
                    ) : (
                      <div className="max-h-48 overflow-y-auto border border-border rounded-md divide-y divide-border">
                        {filteredChannels.length === 0 ? (
                          <p className="text-xs text-muted-foreground px-3 py-2">
                            {getEmptyMessage()}
                          </p>
                        ) : (
                          filteredChannels.map((ch: SlackChannelItem) => {
                            const isOccupied = occupiedChannelIds.has(ch.id);
                            return (
                              <button
                                key={ch.id}
                                type="button"
                                className={cn(
                                  "w-full text-left px-3 py-2 text-sm transition-colors flex items-center justify-between gap-2",
                                  isOccupied
                                    ? "opacity-50 cursor-not-allowed bg-muted/30"
                                    : "hover:bg-muted"
                                )}
                                onClick={() => !isOccupied && handleSelectChannel(ch)}
                                disabled={disabled || isOccupied}
                                title={isOccupied ? "이미 다른 구독에서 사용 중인 채널이에요" : undefined}
                                aria-disabled={isOccupied}
                              >
                                <span>
                                  {ch.isPrivate ? <><Lock className="inline h-3.5 w-3.5 mr-0.5" /></> : "# "}{ch.name}
                                </span>
                                {isOccupied && (
                                  <span className="shrink-0 text-xs px-1.5 py-0.5 rounded-full bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]">
                                    사용 중
                                  </span>
                                )}
                              </button>
                            );
                          })
                        )}
                      </div>
                    )}
                  </div>

                  {/* 비공개 채널 안내 */}
                  {channelType === "private_channel" && (
                    <button
                      type="button"
                      className="text-xs text-primary underline underline-offset-2"
                      onClick={() => setHelpModalOpen(true)}
                    >
                      채널이 보이지 않나요?
                    </button>
                  )}
                </>
              )}
            </>
          )}
        </div>
      )}

      {!isChannel && (
        <div className="space-y-2">
          {!hasSlackDm ? (
            <>
              <p className="text-xs text-muted-foreground">
                DM으로 받으려면 Slack 멤버 ID 연동이 필요해요.
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setConnectModalOpen(true)}
                disabled={disabled}
              >
                Slack 연동하기
              </Button>
            </>
          ) : (
            <>
              <p className="text-xs text-muted-foreground">
                Slack DM으로 뉴스를 보내드려요.
              </p>
              {form.slackChannelConfirmed && (
                <div className="flex items-center gap-2 p-3 rounded-lg bg-[var(--status-success-bg)] border border-[var(--status-success-bg)]">
                  <CheckCircle className="h-4 w-4 text-[var(--status-success-text)] shrink-0" />
                  <p className="text-sm">DM으로 확정됨</p>
                </div>
              )}
            </>
          )}
        </div>
      )}

      <PrivateChannelHelpModal
        open={helpModalOpen}
        onClose={() => setHelpModalOpen(false)}
      />

      <SlackConnectModal
        open={connectModalOpen}
        onOpenChange={setConnectModalOpen}
        onSubmit={handleSlackConnect}
        isSubmitting={connectSubmitting}
        context={isChannel ? "private-channel" : "dm"}
      />
    </div>
  );
}
