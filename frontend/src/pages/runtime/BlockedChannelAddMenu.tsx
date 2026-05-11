import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Lock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { runtimeService } from "@/services/runtimeService";
import { runtimeKeys } from "@/queries/runtimeKeys";
import { matchesKoreanSearch } from "@/utils/search";
import type { SlackChannelItem } from "@/types/runtime";
import type { ChannelType } from "./blockedChannelConstants";

interface Props {
  blockedChannelIds: ReadonlySet<string>;
  onSelect: (channel: SlackChannelItem) => void;
}

/** 차단 목록에 새 채널을 추가할 때 사용하는 드롭다운.
 * Radix Popover로 외부 클릭/ESC 닫힘을 처리한다. */
export function BlockedChannelAddMenu({ blockedChannelIds, onSelect }: Props) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [channelType, setChannelType] = useState<ChannelType>("public_channel");

  const { data: channelResponse, isFetching } = useQuery({
    queryKey: runtimeKeys.slackChannels(channelType),
    queryFn: () => runtimeService.listAdminSlackChannels(channelType),
    enabled: open,
    staleTime: Infinity,
  });

  const availableChannels = channelResponse?.channels ?? [];
  const filtered = availableChannels.filter(
    (ch) => !blockedChannelIds.has(ch.id) && matchesKoreanSearch(ch.name, search),
  );

  // Popover가 열릴 때마다 검색어 초기화
  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (next) setSearch("");
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm">+ 채널 추가 ▾</Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-72 p-0">
        <div className="flex gap-2 p-2 border-b border-border">
          <ChannelTypeChip
            active={channelType === "public_channel"}
            onClick={() => setChannelType("public_channel")}
            label="공개 채널"
          />
          <ChannelTypeChip
            active={channelType === "private_channel"}
            onClick={() => setChannelType("private_channel")}
            label="비공개 채널"
            icon={<Lock className="h-3 w-3" />}
          />
        </div>
        <div className="p-2">
          <Input
            type="text"
            className="h-8 text-sm"
            placeholder="채널 검색..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="차단할 채널 검색"
          />
        </div>
        <div className="max-h-48 overflow-y-auto divide-y divide-border">
          {isFetching ? (
            <p className="text-xs text-muted-foreground px-3 py-2">불러오는 중...</p>
          ) : filtered.length === 0 ? (
            <p className="text-xs text-muted-foreground px-3 py-2">
              {search ? "검색 결과가 없어요." : "추가할 채널이 없어요."}
            </p>
          ) : (
            filtered.map((ch) => (
              <button
                key={ch.id}
                type="button"
                className="w-full text-left px-3 py-2 text-sm hover:bg-muted transition-colors flex items-center gap-1.5"
                onClick={() => {
                  onSelect(ch);
                  setOpen(false);
                }}
              >
                {ch.isPrivate ? (
                  <Lock className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                ) : (
                  <span className="text-muted-foreground shrink-0">#</span>
                )}
                {ch.name}
              </button>
            ))
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}

interface ChannelTypeChipProps {
  active: boolean;
  label: string;
  icon?: React.ReactNode;
  onClick: () => void;
}

/** 공개/비공개 채널 타입 전환 칩 */
function ChannelTypeChip({ active, label, icon, onClick }: ChannelTypeChipProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-1 px-3 py-1 rounded-full text-xs font-medium transition-colors ${
        active
          ? "bg-primary text-primary-foreground"
          : "bg-muted text-muted-foreground hover:bg-muted/80"
      }`}
    >
      {icon}
      {label}
    </button>
  );
}
