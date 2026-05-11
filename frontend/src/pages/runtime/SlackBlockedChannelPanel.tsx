import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { runtimeService } from "@/services/runtimeService";
import { runtimeKeys } from "@/queries/runtimeKeys";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { relativeTime } from "@/utils/date";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import type { SlackChannelItem, BlockedSlackChannel } from "@/types/runtime";
import {
  applyBlockedChannelFilters,
  isOldBlock,
  type BlockedChannelTypeFilter,
  type BlockedChannelSort,
} from "./model/blockedChannelFilters";
import { BlockedChannelAddMenu } from "./BlockedChannelAddMenu";
import { BlockedChannelFilterBar } from "./BlockedChannelFilterBar";
import { BlockedChannelList } from "./BlockedChannelList";
import { BlockReasonDialog } from "./BlockReasonDialog";

interface Props {
  slackConnected: boolean;
}

/** Slack 차단 채널 관리 컨테이너.
 * 쿼리/뮤테이션만 담당하고 UI는 자식 컴포넌트로 위임한다. */
export function SlackBlockedChannelPanel({ slackConnected }: Props) {
  const queryClient = useQueryClient();

  const [pendingAdd, setPendingAdd] = useState<SlackChannelItem | null>(null);
  const [unblockTarget, setUnblockTarget] = useState<BlockedSlackChannel | null>(null);

  const [listSearch, setListSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<BlockedChannelTypeFilter>("all");
  const [sort, setSort] = useState<BlockedChannelSort>("recent");

  const { data: blockedChannels = [] } = useQuery({
    queryKey: runtimeKeys.blockedChannels(),
    queryFn: () => runtimeService.listBlockedChannels(),
  });

  const blockedChannelIds = new Set(blockedChannels.map((b) => b.channelId));

  // block/unblock 성공 시 차단 목록과 양쪽 채널 리스트 캐시를 모두 invalidate한다
  function invalidateChannelCaches() {
    queryClient.invalidateQueries({ queryKey: runtimeKeys.blockedChannels() });
    queryClient.invalidateQueries({ queryKey: runtimeKeys.slackChannels("public_channel") });
    queryClient.invalidateQueries({ queryKey: runtimeKeys.slackChannels("private_channel") });
  }

  const blockMutation = useMutation({
    mutationFn: (args: { channel: SlackChannelItem; reason: string | null }) =>
      runtimeService.blockChannel(args.channel.id, args.channel.name, args.channel.isPrivate, args.reason),
    onSuccess: () => {
      invalidateChannelCaches();
      toast.success("채널이 차단됐어요");
      setPendingAdd(null);
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "채널을 차단하지 못했어요")),
  });

  const unblockMutation = useMutation({
    mutationFn: (channelId: string) => runtimeService.unblockChannel(channelId),
    onSuccess: () => {
      invalidateChannelCaches();
      toast.success("채널 차단이 해제됐어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "차단을 해제하지 못했어요")),
  });

  const listFiltered = applyBlockedChannelFilters(blockedChannels, {
    search: listSearch,
    typeFilter,
    sort,
  });

  // 7일 이상 된 차단은 확인 다이얼로그 경유
  function handleUnblockClick(ch: BlockedSlackChannel) {
    if (isOldBlock(ch.blockedAt)) {
      setUnblockTarget(ch);
    } else {
      unblockMutation.mutate(ch.channelId);
    }
  }

  return (
    <section
      className={`rounded-lg border bg-card p-5 space-y-4 ${
        !slackConnected ? "opacity-50 pointer-events-none" : ""
      }`}
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="font-semibold">차단 채널 관리</h3>
          <p className="text-sm text-muted-foreground mt-1">
            사용자 채널 선택에서 숨길 채널을 관리합니다.
          </p>
        </div>
        <BlockedChannelAddMenu
          blockedChannelIds={blockedChannelIds}
          onSelect={setPendingAdd}
        />
      </div>

      {!slackConnected && (
        <p className="text-sm text-[var(--status-warning-text)]">
          Slack이 연결되어 있지 않아 채널을 관리할 수 없어요.
        </p>
      )}

      {blockedChannels.length > 0 && (
        <BlockedChannelFilterBar
          search={listSearch}
          typeFilter={typeFilter}
          sort={sort}
          onSearchChange={setListSearch}
          onTypeFilterChange={setTypeFilter}
          onSortChange={setSort}
        />
      )}

      <BlockedChannelList
        channels={listFiltered}
        totalCount={blockedChannels.length}
        hasSearchResults={listFiltered.length > 0}
        onUnblock={handleUnblockClick}
      />

      <p className="text-xs text-muted-foreground">
        총 {blockedChannels.length}개 차단 중
        {listFiltered.length !== blockedChannels.length && ` · ${listFiltered.length}개 표시`}
      </p>

      <BlockReasonDialog
        channel={pendingAdd}
        isSubmitting={blockMutation.isPending}
        onCancel={() => setPendingAdd(null)}
        onConfirm={(reason) => {
          if (!pendingAdd) return;
          blockMutation.mutate({ channel: pendingAdd, reason });
        }}
      />

      <ConfirmModal
        open={unblockTarget !== null}
        onOpenChange={(open) => !open && setUnblockTarget(null)}
        title="차단을 해제할까요?"
        description={
          unblockTarget
            ? `"${unblockTarget.channelName}" 채널은 ${relativeTime(unblockTarget.blockedAt)} 차단됐어요. 해제하면 사용자 채널 선택에 다시 나타나요.`
            : undefined
        }
        confirmLabel="해제"
        variant="destructive"
        onConfirm={() => {
          if (unblockTarget) {
            unblockMutation.mutate(unblockTarget.channelId);
            setUnblockTarget(null);
          }
        }}
      />
    </section>
  );
}

