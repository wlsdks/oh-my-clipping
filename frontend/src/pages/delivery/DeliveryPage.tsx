import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Send,
  CheckCircle2,
  XCircle,
  Percent,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  Mail,
} from "lucide-react";
import { KpiCard as SharedKpiCard } from "@/components/shared/KpiCard";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/utils/cn";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import type { PeriodKey } from "@/utils/periodUtils";
import { getPeriodRange } from "@/utils/periodUtils";
import { categoryKeys } from "@/queries/categoryKeys";
import { deliveryKeys } from "@/queries/deliveryKeys";
import { categoryService } from "@/services/categoryService";
import { deliveryService } from "@/services/deliveryService";
import { useSlackChannelMap } from "@/hooks/useSlackChannelMap";
import type { DeliveryLogRecord } from "@/types/delivery";
import type { Category } from "@/types/category";
import { DeliveryMatrixSection } from "./DeliveryMatrixSection";

/* ── 상수 ── */

const PAGE_SIZE = 30;
const ALL_CATEGORIES_VALUE = "__all__";

const PERIODS: { key: PeriodKey; label: string }[] = [
  { key: "this-week", label: "이번 주" },
  { key: "last-week", label: "지난 주" },
  { key: "this-month", label: "이번 달" },
  { key: "last-month", label: "지난 달" },
];

type StatusFilter = "ALL" | "SENT" | "FAILED" | "SKIPPED";

const STATUS_FILTERS: { key: StatusFilter; label: string }[] = [
  { key: "ALL", label: "전체" },
  { key: "SENT", label: "성공" },
  { key: "FAILED", label: "실패" },
  { key: "SKIPPED", label: "건너뛰기" },
];

/** 상태별 뱃지 스타일 매핑 */
const STATUS_BADGE_CONFIG: Record<
  string,
  { variant: "success" | "destructive" | "warning" | "info"; label: string }
> = {
  SENT: { variant: "success", label: "성공" },
  SUCCESS: { variant: "success", label: "성공" },
  FAILED: { variant: "destructive", label: "실패" },
  SKIPPED: { variant: "warning", label: "건너뛰기" },
  RESERVED: { variant: "info", label: "대기" },
};

/* ── 헬퍼 ── */

/** ISO datetime 또는 날짜+시간 → 짧은 한국어 표시 (MM/DD HH시) */
function formatDeliveryTime(date: string, hour: number): string {
  return `${date.slice(5).replace("-", "/")} ${String(hour).padStart(2, "0")}시`;
}

/** 카테고리 ID → 이름 매핑 */
function getCategoryName(
  categories: Category[],
  categoryId: string
): string {
  return categories.find((c) => c.id === categoryId)?.name ?? categoryId;
}

/* ── KPI 카드 래퍼 (아이콘 포함) ── */

interface DeliveryKpiCardProps {
  icon: React.ReactNode;
  label: string;
  value: string | number;
  colorClass?: string;
}

function DeliveryKpiCard({ icon, label, value, colorClass = "text-foreground" }: DeliveryKpiCardProps) {
  return (
    <div className="rounded-2xl bg-card p-4 shadow-sm border">
      <div className="flex items-center gap-2 text-muted-foreground mb-2">
        {icon}
        <span className="text-sm">{label}</span>
      </div>
      <p className={cn("text-2xl font-bold tabular-nums", colorClass)}>
        {value}
      </p>
    </div>
  );
}

/* ── 스켈레톤 로우 ── */

function SkeletonRows() {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
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

/* ── 메인 페이지 ── */

export function DeliveryPage() {
  const queryClient = useQueryClient();
  const { formatChannel } = useSlackChannelMap();

  // 필터 상태
  const [categoryId, setCategoryId] = useState<string | undefined>();
  const [period, setPeriod] = useState<PeriodKey>("this-week");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [page, setPage] = useState(0);

  const { from, to } = getPeriodRange(period);

  // 카테고리 목록
  const { data: categories = [] } = useQuery({
    queryKey: categoryKeys.lists(),
    queryFn: () => categoryService.getAll(),
  });

  // 발송 요약 통계 (오늘 기준)
  const { data: summary, isLoading: isSummaryLoading } = useQuery({
    queryKey: deliveryKeys.summary(),
    queryFn: () => deliveryService.getSummary(),
  });

  // 발송 이력 목록
  const queryParams = new URLSearchParams();
  queryParams.set("page", String(page));
  queryParams.set("size", String(PAGE_SIZE));
  queryParams.set("from", from);
  queryParams.set("to", to);
  if (categoryId) queryParams.set("categoryId", categoryId);
  if (statusFilter !== "ALL") queryParams.set("status", statusFilter);

  const {
    data: logsData,
    isLoading: isLogsLoading,
    isError: isLogsError,
  } = useQuery({
    queryKey: deliveryKeys.logsList({
      categoryId,
      from,
      to,
      status: statusFilter,
      page,
    }),
    queryFn: () => deliveryService.listLogs(queryParams),
  });

  const logs = logsData?.content ?? [];
  const totalCount = logsData?.totalCount ?? 0;
  const totalPages = Math.ceil(totalCount / PAGE_SIZE);

  // 재발송 뮤테이션 — logId 외에 채널 표시용 channelId도 함께 전달한다.
  const retryMutation = useMutation({
    mutationFn: ({ logId }: { logId: string; channelId: string }) => deliveryService.retry(logId),
    onSuccess: (_data, { channelId }) => {
      const channelLabel = formatChannel(channelId);
      const channelDesc = channelLabel !== "-" && channelLabel !== "알 수 없는 채널"
        ? ` (${channelLabel})`
        : "";
      toast.success(`재발송을 요청했어요${channelDesc}`);
      queryClient.invalidateQueries({ queryKey: deliveryKeys.all });
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "재발송하지 못했어요. 잠시 후 다시 시도해 주세요"));
    },
  });

  // 핸들러
  const handleCategoryChange = (value: string) => {
    setCategoryId(value === ALL_CATEGORIES_VALUE ? undefined : value);
    setPage(0);
  };

  const handlePeriodChange = (key: PeriodKey) => {
    setPeriod(key);
    setPage(0);
  };

  const handleStatusChange = (key: StatusFilter) => {
    setStatusFilter(key);
    setPage(0);
  };

  // KPI 색상 결정
  const failedColor =
    summary && summary.failedCount > 0
      ? "text-[var(--status-danger-text)]"
      : "text-foreground";

  const successRateColor =
    summary && summary.successRate >= 95
      ? "text-[var(--status-success-text)]"
      : summary && summary.successRate < 95
        ? "text-[var(--status-warning-text)]"
        : "text-foreground";

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div>
        <h1 className="text-xl sm:text-2xl font-bold">발송 관리</h1>
        <p className="text-sm text-muted-foreground mt-1">
          발송 이력과 실패 재발송을 관리해요
        </p>
      </div>

      {/* KPI 카드 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {isSummaryLoading ? (
          <>
            <SharedKpiCard label="" value="" loading />
            <SharedKpiCard label="" value="" loading />
            <SharedKpiCard label="" value="" loading />
            <SharedKpiCard label="" value="" loading />
          </>
        ) : (
          <>
            <DeliveryKpiCard
              icon={<Send className="h-4 w-4" />}
              label="총 발송"
              value={summary?.totalCount ?? 0}
            />
            <DeliveryKpiCard
              icon={<CheckCircle2 className="h-4 w-4" />}
              label="성공"
              value={summary?.sentCount ?? 0}
              colorClass="text-[var(--status-success-text)]"
            />
            <DeliveryKpiCard
              icon={<XCircle className="h-4 w-4" />}
              label="실패"
              value={summary?.failedCount ?? 0}
              colorClass={failedColor}
            />
            <DeliveryKpiCard
              icon={<Percent className="h-4 w-4" />}
              label="성공률"
              value={
                summary
                  ? `${summary.successRate.toFixed(1)}%`
                  : "-"
              }
              colorClass={successRateColor}
            />
          </>
        )}
      </div>

      {!isSummaryLoading && summary && summary.totalCount === 0 && (
        <p className="text-center text-sm text-muted-foreground">
          아직 발송 기록이 없어요
        </p>
      )}

      {/* 탭: 발송 이력 / 사용자별 현황 */}
      <Tabs defaultValue="logs">
        <TabsList>
          <TabsTrigger value="logs">발송 이력</TabsTrigger>
          <TabsTrigger value="matrix">사용자별 현황</TabsTrigger>
        </TabsList>

        {/* 발송 이력 탭 */}
        <TabsContent value="logs" className="mt-4 space-y-4">
          {/* 필터 영역 */}
          <div className="flex items-center gap-3 flex-wrap">
            {/* 카테고리 필터 */}
            <Select
              value={categoryId ?? ALL_CATEGORIES_VALUE}
              onValueChange={handleCategoryChange}
            >
              <SelectTrigger className="w-40">
                <SelectValue placeholder="전체 카테고리" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_CATEGORIES_VALUE}>전체 카테고리</SelectItem>
                {categories.map((cat) => (
                  <SelectItem key={cat.id} value={cat.id}>
                    {cat.name}
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

            <div className="w-px h-6 bg-border" />

            {/* 상태 필터 */}
            <div className="flex items-center gap-1">
              {STATUS_FILTERS.map((s) => (
                <Button
                  key={s.key}
                  variant={statusFilter === s.key ? "default" : "outline"}
                  size="sm"
                  onClick={() => handleStatusChange(s.key)}
                >
                  {s.label}
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
                  <TableHead>카테고리</TableHead>
                  <TableHead>채널</TableHead>
                  <TableHead className="text-center">상태</TableHead>
                  <TableHead className="text-right">건수</TableHead>
                  <TableHead className="text-right">액션</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {/* 로딩 */}
                {isLogsLoading && <SkeletonRows />}

                {/* 에러 */}
                {isLogsError && (
                  <TableRow>
                    <TableCell
                      colSpan={6}
                      className="text-center py-12"
                    >
                      <div className="flex flex-col items-center gap-3">
                        <p className="text-sm text-muted-foreground">
                          이력을 불러오는 중 문제가 발생했어요
                        </p>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => queryClient.invalidateQueries({ queryKey: deliveryKeys.all })}
                        >
                          다시 시도
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )}

                {/* 빈 상태 */}
                {!isLogsLoading && !isLogsError && logs.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} className="text-center py-16">
                      <div className="flex flex-col items-center gap-2 text-muted-foreground">
                        <Mail className="h-8 w-8" />
                        <p className="text-sm">아직 발송 기록이 없어요</p>
                      </div>
                    </TableCell>
                  </TableRow>
                )}

                {/* 데이터 행 */}
                {!isLogsLoading &&
                  !isLogsError &&
                  logs.map((log) => (
                    <DeliveryLogRow
                      key={log.id}
                      log={log}
                      categories={categories}
                      formatChannel={formatChannel}
                      onRetry={() => retryMutation.mutate({ logId: log.id, channelId: log.channelId })}
                      isRetrying={
                        retryMutation.isPending &&
                        retryMutation.variables?.logId === log.id
                      }
                    />
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
        </TabsContent>

        {/* 사용자별 현황 탭 */}
        <TabsContent value="matrix" className="mt-4">
          <DeliveryMatrixSection />
        </TabsContent>
      </Tabs>
    </div>
  );
}

/* ── 행 컴포넌트 ── */

function DeliveryLogRow({
  log,
  categories,
  formatChannel,
  onRetry,
  isRetrying,
}: {
  log: DeliveryLogRecord;
  categories: Category[];
  formatChannel: (channelId: string | null | undefined) => string;
  onRetry: () => void;
  isRetrying: boolean;
}) {
  const badgeCfg = STATUS_BADGE_CONFIG[log.status] ?? { variant: "info" as const, label: "알 수 없음" };
  const isFailed = log.status === "FAILED";
  const channelLabel = formatChannel(log.channelId);

  return (
    <TableRow className={cn(isFailed && "border-l-2 border-l-[var(--status-danger-text)]")}>
      <TableCell className="text-sm tabular-nums">
        {formatDeliveryTime(log.deliveryDate, log.deliveryHour)}
      </TableCell>
      <TableCell className="text-sm">
        {getCategoryName(categories, log.categoryId)}
      </TableCell>
      <TableCell
        className="text-xs text-muted-foreground truncate max-w-[120px]"
        title={log.channelId}
      >
        {channelLabel}
      </TableCell>
      <TableCell className="text-center">
        <Badge variant={badgeCfg.variant}>{badgeCfg.label}</Badge>
      </TableCell>
      <TableCell className="text-right text-sm tabular-nums">
        {log.itemCount}
      </TableCell>
      <TableCell className="text-right">
        {isFailed && !log.retryAttempted && (
          <Button
            variant="outline"
            size="sm"
            onClick={onRetry}
            disabled={isRetrying}
          >
            <RotateCcw className={cn("h-3.5 w-3.5 mr-1", isRetrying && "animate-spin")} />
            재발송
          </Button>
        )}
        {isFailed && log.retryAttempted && (
          <span className="text-xs text-muted-foreground">재시도 완료</span>
        )}
      </TableCell>
    </TableRow>
  );
}
