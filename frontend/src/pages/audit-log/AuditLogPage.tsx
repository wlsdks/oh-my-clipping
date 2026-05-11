import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronLeft,
  ChevronRight,
  FileText,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/utils/cn";
import type { PeriodKey } from "@/utils/periodUtils";
import { getPeriodRange } from "@/utils/periodUtils";
import { auditLogKeys } from "@/queries/auditLogKeys";
import { auditLogService } from "@/services/auditLogService";
import type { AuditLogEntry } from "@/types/auditLog";
import { actionLabel, targetTypeLabel } from "./model/auditLogLabels";

/* ── 상수 ── */

const PAGE_SIZE = 30;
const ALL_VALUE = "__all__";

const PERIODS: { key: PeriodKey; label: string }[] = [
  { key: "this-week", label: "이번 주" },
  { key: "last-week", label: "지난 주" },
  { key: "this-month", label: "이번 달" },
  { key: "last-month", label: "지난 달" },
];

/** 액션별 뱃지 색상 매핑 */
const ACTION_BADGE_STYLES: Record<string, string> = {
  CREATE: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]",
  UPDATE: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]",
  DELETE: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]",
  APPROVE: "bg-primary/10 text-primary",
  REJECT: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]",
};

const DEFAULT_BADGE_STYLE =
  "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]";

/* ── 헬퍼 ── */

/** ISO datetime → 한국어 짧은 표시 (MM/DD HH:mm) */
function formatDateTime(iso: string): string {
  const d = new Date(iso);
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  const hour = String(d.getHours()).padStart(2, "0");
  const min = String(d.getMinutes()).padStart(2, "0");
  return `${month}/${day} ${hour}:${min}`;
}

/* ── 스켈레톤 로우 ── */

function SkeletonRows() {
  return (
    <>
      {Array.from({ length: 6 }).map((_, i) => (
        <TableRow key={i}>
          {Array.from({ length: 6 }).map((_, j) => (
            <TableCell key={j}>
              <div className="h-4 w-full animate-pulse rounded bg-muted" />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  );
}

/* ── 행 컴포넌트 ── */

function AuditLogRow({ entry }: { entry: AuditLogEntry }) {
  const [expanded, setExpanded] = useState(false);
  const badgeStyle = ACTION_BADGE_STYLES[entry.action] ?? DEFAULT_BADGE_STYLE;
  const hasDetail = !!entry.detail;

  return (
    <TableRow>
      <TableCell className="text-sm tabular-nums whitespace-nowrap">
        {formatDateTime(entry.createdAt)}
      </TableCell>
      <TableCell className="text-sm">{entry.actorName}</TableCell>
      <TableCell>
        <span
          className={cn(
            "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium",
            badgeStyle
          )}
        >
          {actionLabel(entry.action)}
        </span>
      </TableCell>
      <TableCell className="text-sm text-muted-foreground">
        {targetTypeLabel(entry.targetType)}
      </TableCell>
      <TableCell className="text-sm truncate max-w-[160px]">
        {entry.targetName ?? entry.targetId ?? "-"}
      </TableCell>
      <TableCell className="text-sm max-w-[240px]">
        {hasDetail ? (
          <button
            type="button"
            onClick={() => setExpanded((prev) => !prev)}
            className="flex items-start gap-1 text-left text-muted-foreground hover:text-foreground transition-colors"
          >
            <span className={cn(!expanded && "truncate block max-w-[200px]")}>
              {entry.detail}
            </span>
            {expanded ? (
              <ChevronUp className="h-3.5 w-3.5 mt-0.5 shrink-0" />
            ) : (
              <ChevronDown className="h-3.5 w-3.5 mt-0.5 shrink-0" />
            )}
          </button>
        ) : (
          <span className="text-muted-foreground">-</span>
        )}
      </TableCell>
    </TableRow>
  );
}

/* ── 메인 페이지 ── */

export function AuditLogPage() {
  // 필터 상태
  const [actionFilter, setActionFilter] = useState<string | undefined>();
  const [targetTypeFilter, setTargetTypeFilter] = useState<string | undefined>();
  const [period, setPeriod] = useState<PeriodKey>("this-week");
  const [page, setPage] = useState(0);

  const { from, to } = getPeriodRange(period);

  // 필터 옵션 조회
  const { data: filters } = useQuery({
    queryKey: auditLogKeys.filters(),
    queryFn: () => auditLogService.getFilters(),
  });

  // 감사 로그 목록 조회
  const queryParams = new URLSearchParams();
  queryParams.set("page", String(page));
  queryParams.set("size", String(PAGE_SIZE));
  queryParams.set("from", from);
  queryParams.set("to", to);
  if (actionFilter) queryParams.set("action", actionFilter);
  if (targetTypeFilter) queryParams.set("targetType", targetTypeFilter);

  const {
    data: logsData,
    isLoading,
    isError,
  } = useQuery({
    queryKey: auditLogKeys.list({
      action: actionFilter,
      targetType: targetTypeFilter,
      from,
      to,
      page,
    }),
    queryFn: () => auditLogService.getAll(queryParams),
  });

  const logs = logsData?.content ?? [];
  const totalCount = logsData?.totalCount ?? 0;
  const totalPages = Math.ceil(totalCount / PAGE_SIZE);

  // 핸들러
  const handleActionChange = (value: string) => {
    setActionFilter(value === ALL_VALUE ? undefined : value);
    setPage(0);
  };

  const handleTargetTypeChange = (value: string) => {
    setTargetTypeFilter(value === ALL_VALUE ? undefined : value);
    setPage(0);
  };

  const handlePeriodChange = (key: PeriodKey) => {
    setPeriod(key);
    setPage(0);
  };

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div>
        <h1 className="text-2xl font-bold">감사 로그</h1>
        <p className="text-sm text-muted-foreground mt-1">
          관리자 활동 이력을 확인합니다
        </p>
      </div>

      {/* 필터 영역 */}
      <div className="flex items-center gap-3 flex-wrap">
        {/* 액션 필터 */}
        <Select
          value={actionFilter ?? ALL_VALUE}
          onValueChange={handleActionChange}
        >
          <SelectTrigger className="w-40">
            <SelectValue placeholder="전체 액션" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_VALUE}>전체 액션</SelectItem>
            {filters?.actions.map((action) => (
              <SelectItem key={action} value={action}>
                {actionLabel(action)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* 대상 유형 필터 */}
        <Select
          value={targetTypeFilter ?? ALL_VALUE}
          onValueChange={handleTargetTypeChange}
        >
          <SelectTrigger className="w-40">
            <SelectValue placeholder="전체 대상" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_VALUE}>전체 대상</SelectItem>
            {filters?.targetTypes.map((tt) => (
              <SelectItem key={tt} value={tt}>
                {targetTypeLabel(tt)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* 기간 필터 */}
        <div className="flex items-center gap-1">
          {PERIODS.map((p) => (
            <Button
              key={p.key}
              variant={period === p.key ? "default" : "outline"}
              size="sm"
              onClick={() => handlePeriodChange(p.key)}
            >
              {p.label}
            </Button>
          ))}
        </div>
      </div>

      {/* 테이블 */}
      <div className="rounded-xl border bg-card overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>시각</TableHead>
              <TableHead>관리자</TableHead>
              <TableHead>액션</TableHead>
              <TableHead>대상 유형</TableHead>
              <TableHead>대상명</TableHead>
              <TableHead>상세</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {/* 로딩 */}
            {isLoading && <SkeletonRows />}

            {/* 에러 */}
            {isError && (
              <TableRow>
                <TableCell
                  colSpan={6}
                  className="text-center py-12 text-sm text-muted-foreground"
                >
                  감사 로그를 불러오는 중 문제가 발생했어요. 잠시 후 다시 시도해
                  주세요.
                </TableCell>
              </TableRow>
            )}

            {/* 빈 상태 */}
            {!isLoading && !isError && logs.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="text-center py-16">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <FileText className="h-8 w-8" />
                    <p className="text-sm">감사 기록이 없어요</p>
                  </div>
                </TableCell>
              </TableRow>
            )}

            {/* 데이터 행 */}
            {!isLoading &&
              !isError &&
              logs.map((entry) => (
                <AuditLogRow key={entry.id} entry={entry} />
              ))}
          </TableBody>
        </Table>
      </div>

      {/* 페이징 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <span>
            전체 {totalCount}건 중 {page * PAGE_SIZE + 1}~
            {Math.min((page + 1) * PAGE_SIZE, totalCount)}건
          </span>
          <div className="flex items-center gap-1">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft className="h-4 w-4" />
              이전
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              다음
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
