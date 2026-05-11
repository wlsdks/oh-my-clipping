import { Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatRelativeDate } from "@/utils/date";
import type { UserAccountApproval } from "@/types/user";
import type { ReviewMode } from "./ReviewDialog";

/**
 * 승인 대기(PENDING) 회원 목록을 표시하는 테이블이다.
 *
 * - 체크박스로 개별/페이지 전체 선택을 지원한다.
 * - 각 행에서 승인/반려 버튼을 제공한다.
 * - 접근성: 테이블에 `aria-label` 을 부여하고, 체크박스와 액션 버튼에는
 *   신청자 식별 정보를 포함한 `aria-label` 로 스크린리더 문맥을 제공한다.
 */
interface PendingTableProps {
  accounts: UserAccountApproval[];
  selectedIds: Set<string>;
  onToggleSelect: (id: string) => void;
  onToggleSelectAll: () => void;
  onReview: (mode: ReviewMode, item: UserAccountApproval) => void;
  isWorking: boolean;
}

export function PendingTable({
  accounts,
  selectedIds,
  onToggleSelect,
  onToggleSelectAll,
  onReview,
  isWorking,
}: PendingTableProps) {
  // 페이지 전체 선택 여부 계산 (헤더 체크박스 상태)
  const allPageSelected =
    accounts.length > 0 && accounts.every((a) => selectedIds.has(a.id));
  // 일부만 선택됐을 때 indeterminate 표시용 계산
  const somePageSelected =
    !allPageSelected && accounts.some((a) => selectedIds.has(a.id));

  return (
    <div className="rounded-md border bg-card overflow-x-auto">
      <Table aria-label="가입 승인 대기 회원 목록">
        <TableHeader>
          <TableRow>
            <TableHead scope="col" className="w-10">
              <input
                type="checkbox"
                checked={allPageSelected}
                ref={(node) => {
                  // indeterminate 는 prop 으로 직접 못 넣어서 ref 로 설정
                  if (node) node.indeterminate = somePageSelected;
                }}
                onChange={onToggleSelectAll}
                aria-label={
                  allPageSelected
                    ? "이 페이지 선택 해제"
                    : "이 페이지 전체 선택"
                }
                className="h-4 w-4 rounded border-gray-300 accent-primary"
              />
            </TableHead>
            <TableHead scope="col">신청자</TableHead>
            <TableHead scope="col">부서</TableHead>
            <TableHead scope="col">메모</TableHead>
            <TableHead scope="col">신청일</TableHead>
            <TableHead scope="col">처리</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {accounts.map((item) => {
            // 스크린리더용 식별 이름: displayName 우선, 없으면 username
            const displayLabel = item.displayName ?? item.username;
            const isChecked = selectedIds.has(item.id);
            return (
              <TableRow
                key={item.id}
                className="hover:bg-muted/50 transition-colors"
              >
                <TableCell>
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => onToggleSelect(item.id)}
                    aria-label={`${displayLabel} 선택`}
                    className="h-4 w-4 rounded border-gray-300 accent-primary"
                  />
                </TableCell>
                <TableCell>
                  {/* 회원명은 말줄임 금지 (AGENTS.md §8.3) — 전체 이름 노출 */}
                  <div className="max-w-[180px] break-keep">
                    <div className="font-medium">
                      {displayLabel}
                    </div>
                    <div className="text-xs text-muted-foreground break-all">
                      {item.username}
                    </div>
                  </div>
                </TableCell>
                <TableCell className="text-sm text-muted-foreground break-keep">
                  {item.department ?? "\u2014"}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground max-w-[200px] truncate">
                  {item.approvalNote ?? "\u2014"}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatRelativeDate(item.createdAt)}
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <Button
                      size="sm"
                      variant="default"
                      onClick={() => onReview("approve", item)}
                      disabled={isWorking}
                      aria-label={`${displayLabel} 승인`}
                    >
                      <Check className="h-4 w-4 mr-1" aria-hidden="true" />
                      승인
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => onReview("reject", item)}
                      disabled={isWorking}
                      aria-label={`${displayLabel} 반려`}
                    >
                      <X className="h-4 w-4 mr-1" aria-hidden="true" />
                      반려
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
