import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { BlockedChannelSort, BlockedChannelTypeFilter } from "./model/blockedChannelFilters";

interface Props {
  search: string;
  typeFilter: BlockedChannelTypeFilter;
  sort: BlockedChannelSort;
  onSearchChange: (value: string) => void;
  onTypeFilterChange: (value: BlockedChannelTypeFilter) => void;
  onSortChange: (value: BlockedChannelSort) => void;
}

const TYPE_FILTERS: { value: BlockedChannelTypeFilter; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "public", label: "공개" },
  { value: "private", label: "비공개" },
];

/** 차단 채널 목록 상단의 검색/타입 필터/정렬 바. */
export function BlockedChannelFilterBar({
  search,
  typeFilter,
  sort,
  onSearchChange,
  onTypeFilterChange,
  onSortChange,
}: Props) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Input
        type="text"
        className="flex-1 min-w-[180px] h-8 text-sm"
        placeholder="채널명이나 사유 검색..."
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        aria-label="차단 목록 검색"
      />
      <div className="flex gap-1.5">
        {TYPE_FILTERS.map((t) => (
          <button
            key={t.value}
            type="button"
            onClick={() => onTypeFilterChange(t.value)}
            className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
              typeFilter === t.value
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:bg-muted/80"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      <Select value={sort} onValueChange={(v) => onSortChange(v as BlockedChannelSort)}>
        <SelectTrigger className="w-28 h-8 text-xs" aria-label="정렬">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="recent">최신순</SelectItem>
          <SelectItem value="oldest">오래된순</SelectItem>
        </SelectContent>
      </Select>
    </div>
  );
}
