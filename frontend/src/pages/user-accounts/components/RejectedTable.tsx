import { RotateCcw } from "lucide-react";
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

/**
 * 반려(REJECTED) 회원 목록을 표시하는 테이블이다.
 *
 * - 반려 사유와 처리자를 함께 노출한다.
 * - 행별 '재승인' 버튼으로 승인 상태로 복귀시킬 수 있다.
 * - 접근성: 테이블에 `aria-label` 을 부여하고, 각 재승인 버튼에는
 *   신청자 식별 정보를 포함한 `aria-label` 을 제공한다.
 */
interface RejectedTableProps {
  accounts: UserAccountApproval[];
  onReapprove: (item: UserAccountApproval) => void;
  isWorking: boolean;
}

export function RejectedTable({
  accounts,
  onReapprove,
  isWorking,
}: RejectedTableProps) {
  return (
    <div className="rounded-md border bg-card overflow-x-auto">
      <Table aria-label="반려된 가입 신청 목록">
        <TableHeader>
          <TableRow>
            <TableHead scope="col">신청자</TableHead>
            <TableHead scope="col">부서</TableHead>
            <TableHead scope="col">반려 사유</TableHead>
            <TableHead scope="col">처리자</TableHead>
            <TableHead scope="col">반려일</TableHead>
            <TableHead scope="col">처리</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {accounts.map((item) => {
            const displayLabel = item.displayName ?? item.username;
            return (
              <TableRow
                key={item.id}
                className="hover:bg-muted/50 transition-colors"
              >
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
                <TableCell className="text-sm text-destructive/80 max-w-[200px] truncate">
                  {item.approvalNote ?? "\u2014"}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {item.approvedByUsername ?? "\u2014"}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatRelativeDate(item.approvedAt)}
                </TableCell>
                <TableCell>
                  <Button
                    size="sm"
                    variant="default"
                    onClick={() => onReapprove(item)}
                    disabled={isWorking}
                    aria-label={`${displayLabel} 재승인`}
                  >
                    <RotateCcw className="h-3.5 w-3.5 mr-1" aria-hidden="true" />
                    재승인
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
