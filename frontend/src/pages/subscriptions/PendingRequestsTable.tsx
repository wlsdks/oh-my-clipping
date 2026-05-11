import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { EmptyState } from "@/components/shared/EmptyState";
import { formatRelativeDate } from "@/utils/date";
import { cn } from "@/utils/cn";
import { useSlackChannelMap } from "@/hooks/useSlackChannelMap";
import type { UserClippingRequest } from "@/types/user";
import { REQUEST_STATUS_LABEL } from "./model/constants";
import type { SubscriptionFilter, SubscriptionPanelItem } from "./model/types";

interface PendingRequestsTableProps {
  requests: UserClippingRequest[];
  filter: SubscriptionFilter;
  onSelect: (item: SubscriptionPanelItem) => void;
  /** 체크박스 선택 상태 (pending 필터에서만 사용) */
  selectedIds?: Set<string>;
  onToggleSelect?: (id: string) => void;
  onToggleSelectAll?: () => void;
}

function statusBadgeVariant(
  status: UserClippingRequest["status"],
): "default" | "secondary" | "destructive" | "outline" {
  switch (status) {
    case "PENDING":
      return "default";
    case "APPROVED":
      return "secondary";
    case "REJECTED":
      return "destructive";
    case "WITHDRAWN":
      return "outline";
  }
}

export function PendingRequestsTable({
  requests,
  filter,
  onSelect,
  selectedIds,
  onToggleSelect,
  onToggleSelectAll,
}: PendingRequestsTableProps) {
  const { formatChannel } = useSlackChannelMap();

  // 체크박스는 pending 필터에서만 표시
  const showCheckbox = filter === "pending" && !!selectedIds && !!onToggleSelect && !!onToggleSelectAll;

  if (requests.length === 0) {
    const emptyLabel =
      filter === "pending"
        ? "대기 중인 요청이 없어요"
        : filter === "rejected"
          ? "반려된 요청이 없어요"
          : "철회된 요청이 없어요";

    return <EmptyState title={emptyLabel} />;
  }

  const allSelected = showCheckbox && requests.length > 0 && requests.every((r) => selectedIds.has(r.id));
  const someSelected = showCheckbox && requests.some((r) => selectedIds.has(r.id)) && !allSelected;

  return (
    <div className="rounded-xl border bg-card overflow-hidden">
      <Table>
        <TableHeader>
          <TableRow>
            {showCheckbox && (
              <TableHead className="w-10">
                <Checkbox
                  checked={allSelected ? true : someSelected ? "indeterminate" : false}
                  onCheckedChange={() => onToggleSelectAll()}
                  aria-label="전체 선택"
                />
              </TableHead>
            )}
            <TableHead className="min-w-[160px]">요청 이름</TableHead>
            <TableHead className="min-w-[80px]">신청자</TableHead>
            <TableHead className="min-w-[120px]">소스</TableHead>
            <TableHead className="min-w-[100px]">발송 대상</TableHead>
            <TableHead className="min-w-[80px]">신청일</TableHead>
            <TableHead className="min-w-[100px] text-right">상태</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {requests.map((req) => {
            const isSelected = showCheckbox && selectedIds.has(req.id);
            return (
              <TableRow
                key={req.id}
                className={cn(
                  "cursor-pointer hover:bg-muted/50",
                  isSelected && "bg-primary/5",
                )}
                onClick={() => onSelect({ kind: "request", data: req })}
              >
                {showCheckbox && (
                  <TableCell>
                    <Checkbox
                      checked={isSelected}
                      onCheckedChange={() => onToggleSelect(req.id)}
                      onClick={(e: React.MouseEvent) => e.stopPropagation()}
                      aria-label={`${req.requestName} 선택`}
                    />
                  </TableCell>
                )}
                <TableCell className="font-medium">{req.requestName}</TableCell>
                <TableCell className="text-muted-foreground">사용자</TableCell>
                <TableCell className="text-muted-foreground truncate max-w-[200px]">
                  {req.sourceName || "---"}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {/* 사용자 요청 빈 slackChannelId 는 "DM 의도"이므로 dmIfBlank=true. */}
                  {formatChannel(req.slackChannelId, { dmIfBlank: true })}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {formatRelativeDate(req.createdAt)}
                </TableCell>
                <TableCell className="text-right whitespace-nowrap">
                  <Badge variant={statusBadgeVariant(req.status)}>
                    {REQUEST_STATUS_LABEL[req.status] ?? "알 수 없음"}
                  </Badge>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
