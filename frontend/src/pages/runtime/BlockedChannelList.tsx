import { Lock, MessageCircle } from "lucide-react";
import { relativeTime } from "@/utils/date";
import type { BlockedSlackChannel } from "@/types/runtime";
import { formatBlockedBy } from "./blockedChannelConstants";

interface Props {
  channels: readonly BlockedSlackChannel[];
  totalCount: number;
  hasSearchResults: boolean;
  onUnblock: (channel: BlockedSlackChannel) => void;
}

/** 차단 채널 목록 렌더링 — 빈 상태/검색 결과 없음/리스트 세 가지 상태 처리. */
export function BlockedChannelList({ channels, totalCount, hasSearchResults, onUnblock }: Props) {
  if (totalCount === 0) {
    return <p className="text-xs text-muted-foreground">차단된 채널이 없어요</p>;
  }
  if (!hasSearchResults) {
    return <p className="text-xs text-muted-foreground">검색 결과가 없어요.</p>;
  }
  return (
    <div className="rounded-md border divide-y divide-border max-h-[560px] overflow-y-auto">
      {channels.map((ch) => (
        <BlockedChannelRow key={ch.id} channel={ch} onUnblock={onUnblock} />
      ))}
    </div>
  );
}

interface RowProps {
  channel: BlockedSlackChannel;
  onUnblock: (channel: BlockedSlackChannel) => void;
}

function BlockedChannelRow({ channel, onUnblock }: RowProps) {
  return (
    <div className="flex items-start justify-between gap-3 px-3 py-2.5">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5 text-sm font-medium">
          {channel.isPrivate ? (
            <Lock className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
          ) : (
            <span className="text-muted-foreground">#</span>
          )}
          <span className="truncate">{channel.channelName}</span>
        </div>
        <p className="text-xs text-muted-foreground mt-0.5">
          {channel.isPrivate ? "비공개" : "공개"} · {formatBlockedBy(channel.blockedByUserId)} ·{" "}
          {relativeTime(channel.blockedAt)}
        </p>
        {channel.reason && (
          <p className="text-xs text-muted-foreground mt-1 flex items-start gap-1">
            <MessageCircle className="h-3 w-3 shrink-0 mt-0.5" />
            <span className="break-words">{channel.reason}</span>
          </p>
        )}
      </div>
      <button
        type="button"
        className="text-xs text-muted-foreground hover:text-foreground transition-colors shrink-0"
        onClick={() => onUnblock(channel)}
        aria-label={`${channel.channelName} 채널 차단 해제`}
      >
        해제
      </button>
    </div>
  );
}
