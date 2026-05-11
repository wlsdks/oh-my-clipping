import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Mail, Users, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { STALE_TIMES } from "@/lib/queryConfig";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/utils/cn";
import { pipelineAnalyticsKeys } from "@/queries/pipelineAnalyticsKeys";
import { pipelineAnalyticsService } from "@/services/pipelineAnalyticsService";
import type { DeliveryMatrixUser } from "@/services/pipelineAnalyticsService";

/* ── 기간 필터 옵션 ── */

const MATRIX_PERIODS = [
  { days: 1, label: "오늘" },
  { days: 7, label: "7일" },
] as const;

/* ── 카테고리 이름 말줄임 ── */

const MAX_CATEGORY_NAME_LENGTH = 8;

function truncateName(name: string): string {
  return name.length > MAX_CATEGORY_NAME_LENGTH
    ? name.slice(0, MAX_CATEGORY_NAME_LENGTH) + "…"
    : name;
}

/* ── 스켈레톤 ── */

function MatrixSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3">
          <div className="h-5 w-20 animate-pulse rounded bg-muted" />
          {Array.from({ length: 5 }).map((_, j) => (
            <div key={j} className="h-5 w-16 animate-pulse rounded bg-muted" />
          ))}
        </div>
      ))}
    </div>
  );
}

/* ── 셀 뱃지 렌더링 ── */

interface MatrixCellProps {
  sent: number;
  skipped: number;
  failed: number;
}

function MatrixCell({ sent, skipped, failed }: MatrixCellProps) {
  if (sent === 0 && skipped === 0 && failed === 0) {
    return <span className="text-muted-foreground">-</span>;
  }

  return (
    <div className="flex flex-wrap gap-1 justify-center">
      {sent > 0 && (
        <Badge variant="success" className="text-[10px] px-1.5 py-0">
          {sent}
        </Badge>
      )}
      {failed > 0 && (
        <Badge variant="destructive" className="text-[10px] px-1.5 py-0">
          {failed}
        </Badge>
      )}
      {skipped > 0 && (
        <Badge variant="warning" className="text-[10px] px-1.5 py-0">
          {skipped}
        </Badge>
      )}
    </div>
  );
}

/* ── 모든 카테고리 이름 추출 (열 헤더용) ── */

function collectCategoryColumns(
  users: DeliveryMatrixUser[]
): { categoryId: string; categoryName: string }[] {
  const map = new Map<string, string>();
  for (const user of users) {
    for (const cat of user.categories) {
      if (!map.has(cat.categoryId)) {
        map.set(cat.categoryId, cat.categoryName);
      }
    }
  }
  return Array.from(map.entries()).map(([categoryId, categoryName]) => ({
    categoryId,
    categoryName,
  }));
}

/* ── 메인 컴포넌트 ── */

export function DeliveryMatrixSection() {
  const [days, setDays] = useState(7);

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: pipelineAnalyticsKeys.deliveryMatrix(days),
    queryFn: () => pipelineAnalyticsService.getDeliveryMatrix(days),
    staleTime: STALE_TIMES.FREQUENT,
  });

  const users = data?.users ?? [];
  const columns = collectCategoryColumns(users);

  return (
    <div className="space-y-4">
      {/* 기간 필터 */}
      <div className="flex items-center gap-2">
        <div className="flex items-center gap-1">
          {MATRIX_PERIODS.map((p) => (
            <Button
              key={p.days}
              variant={days === p.days ? "default" : "outline"}
              size="sm"
              onClick={() => setDays(p.days)}
            >
              {p.label}
            </Button>
          ))}
        </div>

        {/* 범례 */}
        <div className="ml-auto flex items-center gap-3 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <Badge variant="success" className="text-[10px] px-1.5 py-0">N</Badge>
            성공
          </span>
          <span className="flex items-center gap-1">
            <Badge variant="destructive" className="text-[10px] px-1.5 py-0">N</Badge>
            실패
          </span>
          <span className="flex items-center gap-1">
            <Badge variant="warning" className="text-[10px] px-1.5 py-0">N</Badge>
            건너뛰기
          </span>
        </div>
      </div>

      {/* 로딩 */}
      {isLoading && <MatrixSkeleton />}

      {/* 에러 */}
      {isError && (
        <div className="flex flex-col items-center gap-3 py-12">
          <p className="text-sm text-muted-foreground">
            매트릭스를 불러오는 중 문제가 발생했어요
          </p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isFetching}
          >
            <RefreshCw className={cn("h-3.5 w-3.5 mr-1", isFetching && "animate-spin")} />
            다시 시도
          </Button>
        </div>
      )}

      {/* 빈 상태 */}
      {!isLoading && !isError && users.length === 0 && (
        <div className="flex flex-col items-center gap-2 py-16 text-muted-foreground">
          <Mail className="h-8 w-8" />
          <p className="text-sm">아직 발송 기록이 없어요</p>
        </div>
      )}

      {/* 매트릭스 테이블 */}
      {!isLoading && !isError && users.length > 0 && (
        <div className="rounded-xl border bg-card overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="sticky left-0 z-10 bg-card min-w-[120px]">
                  <div className="flex items-center gap-1.5">
                    <Users className="h-3.5 w-3.5" />
                    사용자
                  </div>
                </TableHead>
                {columns.map((col) => (
                  <TableHead
                    key={col.categoryId}
                    className="text-center min-w-[80px]"
                    title={col.categoryName}
                  >
                    {truncateName(col.categoryName)}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.map((user) => {
                // 카테고리별 데이터를 빠르게 조회하기 위한 맵
                const catMap = new Map(
                  user.categories.map((c) => [c.categoryId, c])
                );
                return (
                  <TableRow key={user.userId}>
                    <TableCell
                      className={cn(
                        "sticky left-0 z-10 bg-card font-medium text-sm",
                        "border-r"
                      )}
                    >
                      {user.username}
                    </TableCell>
                    {columns.map((col) => {
                      const cat = catMap.get(col.categoryId);
                      return (
                        <TableCell key={col.categoryId} className="text-center">
                          {cat ? (
                            <MatrixCell
                              sent={cat.sent}
                              skipped={cat.skipped}
                              failed={cat.failed}
                            />
                          ) : (
                            <span className="text-muted-foreground">-</span>
                          )}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
