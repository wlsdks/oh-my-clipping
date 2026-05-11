import { ArrowUp, ArrowDown, ArrowUpDown } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Checkbox } from "@/components/ui/checkbox";
import { EmptyState } from "@/components/shared/EmptyState";
import { TruncatedText } from "@/components/shared/TruncatedText";
import { formatRelativeDate } from "@/utils/date";
import { cn } from "@/utils/cn";
import { useSlackChannelMap } from "@/hooks/useSlackChannelMap";
import type { Category } from "@/types/category";
import { getCategoryStatus, STATUS_STYLES } from "./model/constants";
import type { SortField, SortState } from "./model/sorting";
import type { SubscriptionPanelItem } from "./model/types";

interface ActiveSubscriptionsTableProps {
  categories: Category[];
  onSelect: (item: SubscriptionPanelItem) => void;
  sort: SortState;
  onSortChange: (sort: SortState) => void;
  selectedIds: Set<string>;
  onToggleSelect: (id: string) => void;
  onToggleSelectAll: () => void;
  focusedIndex: number;
  noRuleCategoryIds?: Set<string>;
}

/** 정렬 방향 토글 헬퍼 */
function nextSort(current: SortState, field: SortField): SortState {
  if (current.field === field) {
    return { field, direction: current.direction === "asc" ? "desc" : "asc" };
  }
  return { field, direction: "asc" };
}

/** 컬럼 헤더 정렬 아이콘 */
function SortIcon({ field, current }: { field: SortField; current: SortState }) {
  if (current.field !== field) {
    return <ArrowUpDown className="ml-1 inline h-3.5 w-3.5 text-muted-foreground/50" />;
  }
  return current.direction === "asc" ? (
    <ArrowUp className="ml-1 inline h-3.5 w-3.5" />
  ) : (
    <ArrowDown className="ml-1 inline h-3.5 w-3.5" />
  );
}

export function ActiveSubscriptionsTable({
  categories,
  onSelect,
  sort,
  onSortChange,
  selectedIds,
  onToggleSelect,
  onToggleSelectAll,
  focusedIndex,
  noRuleCategoryIds,
}: ActiveSubscriptionsTableProps) {
  const { formatChannel } = useSlackChannelMap();

  if (categories.length === 0) {
    return <EmptyState title="해당하는 구독이 없어요" />;
  }

  const allSelected =
    categories.length > 0 && categories.every((c) => selectedIds.has(c.id));
  const someSelected =
    categories.some((c) => selectedIds.has(c.id)) && !allSelected;

  function handleSort(field: SortField) {
    onSortChange(nextSort(sort, field));
  }

  const headerBtnClass =
    "inline-flex items-center gap-0.5 select-none cursor-pointer hover:text-foreground transition-colors";

  return (
    <div className="rounded-xl border bg-card overflow-hidden">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-10">
              <Checkbox
                checked={allSelected ? true : someSelected ? "indeterminate" : false}
                onCheckedChange={() => onToggleSelectAll()}
                aria-label="전체 선택"
              />
            </TableHead>
            <TableHead className="min-w-[160px]">
              <button type="button" className={headerBtnClass} onClick={() => handleSort("name")}>
                구독 이름 <SortIcon field="name" current={sort} />
              </button>
            </TableHead>
            <TableHead className="min-w-[100px]">채널</TableHead>
            <TableHead className="min-w-[100px]">
              <button type="button" className={headerBtnClass} onClick={() => handleSort("sourceCount")}>
                소스 <SortIcon field="sourceCount" current={sort} />
              </button>
            </TableHead>
            <TableHead className="min-w-[80px]">
              <button type="button" className={headerBtnClass} onClick={() => handleSort("subscriberCount")}>
                구독자 <SortIcon field="subscriberCount" current={sort} />
              </button>
            </TableHead>
            <TableHead className="min-w-[100px]">
              <button type="button" className={headerBtnClass} onClick={() => handleSort("lastDeliveryAt")}>
                마지막 발송 <SortIcon field="lastDeliveryAt" current={sort} />
              </button>
            </TableHead>
            <TableHead className="min-w-[80px] text-right">
              <button type="button" className={headerBtnClass} onClick={() => handleSort("status")}>
                상태 <SortIcon field="status" current={sort} />
              </button>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {categories.map((cat, idx) => {
            const status = getCategoryStatus(cat);
            const style = STATUS_STYLES[status.type];
            const isFocused = idx === focusedIndex;
            const isSelected = selectedIds.has(cat.id);

            return (
              <TableRow
                key={cat.id}
                data-focused={isFocused || undefined}
                className={cn(
                  "cursor-pointer hover:bg-muted/50",
                  isFocused && "bg-muted/60 outline outline-2 outline-ring -outline-offset-2",
                  isSelected && "bg-primary/5",
                )}
                onClick={() => onSelect({ kind: "category", data: cat })}
              >
                <TableCell>
                  <Checkbox
                    checked={isSelected}
                    onCheckedChange={() => onToggleSelect(cat.id)}
                    onClick={(e: React.MouseEvent) => e.stopPropagation()}
                    aria-label={`${cat.name} 선택`}
                  />
                </TableCell>
                <TableCell className="font-medium">
                  <div className="flex items-center gap-2 max-w-[280px] overflow-hidden">
                    <TruncatedText className="min-w-0 flex-1">{cat.name}</TruncatedText>
                    {noRuleCategoryIds?.has(cat.id) && (
                      <span className="shrink-0 inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]">
                        키워드 없음
                      </span>
                    )}
                  </div>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {cat.slackChannelId ? formatChannel(cat.slackChannelId) : "---"}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  <span>{cat.sourceCount}개</span>
                  {cat.errorSourceCount > 0 && (
                    <span className="ml-1 text-[var(--status-danger-text)]">
                      ({cat.errorSourceCount} 오류)
                    </span>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {cat.subscriberCount}명
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {cat.lastDeliveryAt
                    ? formatRelativeDate(cat.lastDeliveryAt)
                    : "---"}
                </TableCell>
                <TableCell className="text-right">
                  <span
                    className={cn(
                      "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium",
                      style.bg,
                      style.text,
                      style.border,
                    )}
                  >
                    <span
                      className={cn("h-1.5 w-1.5 rounded-full", style.dotColor)}
                    />
                    {status.label}
                  </span>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
