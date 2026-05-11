import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ChevronLeft, ChevronRight, ShieldOff, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EmptyState } from "@/components/shared/EmptyState";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { cn } from "@/utils/cn";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { autoExcludedKeys } from "@/queries/categoryRuleKeys";
import { categoryKeys } from "@/queries/categoryKeys";
import { categoryRuleService } from "@/services/categoryRuleService";
import { categoryService } from "@/services/categoryService";
import type { AutoExcludedItem } from "@/types/autoExcludedItem";
import { AutoExcludeDetailDrawer } from "./AutoExcludeDetailDrawer";

/* ── 상수 ── */

const PAGE_SIZE = 20;
const ALL_VALUE = "__all__";

/** 기간 옵션 (일 단위). 서버에서 1..30 로 clamp. */
const PERIOD_OPTIONS: { value: number; label: string }[] = [
  { value: 3, label: "3일" },
  { value: 7, label: "7일" },
  { value: 14, label: "14일" },
  { value: 30, label: "30일" }
];

/** reason 기계 코드 → 한국어 라벨 (레퍼런스: AGENTS.md 룰 엔진 정책). */
const REASON_LABELS: Record<string, string> = {
  "rule:event_type_blacklist": "이벤트 타입 차단",
  "rule:zero_signal": "시그널 없음"
};

/** reason 필터 옵션 — 서버가 받는 값은 풀 prefix(`rule:...`) 그대로. */
const REASON_FILTER_OPTIONS: { value: string; label: string }[] = [
  { value: "rule:event_type_blacklist", label: "이벤트 타입 차단" },
  { value: "rule:zero_signal", label: "시그널 없음" }
];

/* ── 헬퍼 ── */

function reasonLabel(reason: string): string {
  return REASON_LABELS[reason] ?? reason;
}

/** ISO datetime → 한국어 짧은 표시 (MM/DD HH:mm). */
function formatDateTime(iso: string): string {
  const d = new Date(iso);
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  const hour = String(d.getHours()).padStart(2, "0");
  const min = String(d.getMinutes()).padStart(2, "0");
  return `${month}/${day} ${hour}:${min}`;
}

/* ── 스켈레톤 ── */

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

/* ── 요약 카드 ── */

interface SummaryHeaderProps {
  totalCount: number;
  reasonBreakdown: Record<string, number>;
  periodDays: number;
}

function SummaryHeader({ totalCount, reasonBreakdown, periodDays }: SummaryHeaderProps) {
  const breakdownEntries = Object.entries(reasonBreakdown).filter(([, count]) => count > 0);

  return (
    <div className="rounded-xl border bg-card p-5 space-y-3">
      <div className="flex items-baseline gap-2">
        <h2 className="text-lg font-semibold">지난 {periodDays}일 자동 제외</h2>
        <span className="text-2xl font-bold tabular-nums">{totalCount}</span>
        <span className="text-sm text-muted-foreground">건</span>
      </div>
      {breakdownEntries.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          {breakdownEntries.map(([reason, count]) => (
            <span
              key={reason}
              className="inline-flex items-center gap-1.5 rounded-full bg-[var(--status-neutral-bg)] px-3 py-1 text-xs font-medium text-[var(--status-neutral-text)]"
            >
              <span>{reasonLabel(reason)}</span>
              <span className="tabular-nums font-semibold">{count}</span>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

/* ── 테이블 ── */

interface AutoExcludedTableProps {
  items: AutoExcludedItem[];
  isLoading: boolean;
  isError: boolean;
  onRestoreClick: (item: AutoExcludedItem) => void;
  onTitleClick: (item: AutoExcludedItem) => void;
  isRestoring: boolean;
  pendingId: string | null;
}

function AutoExcludedTable({
  items,
  isLoading,
  isError,
  onRestoreClick,
  onTitleClick,
  isRestoring,
  pendingId
}: AutoExcludedTableProps) {
  return (
    <div className="rounded-xl border bg-card overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>제목</TableHead>
            <TableHead>카테고리</TableHead>
            <TableHead className="text-right">점수</TableHead>
            <TableHead className="w-28 whitespace-nowrap">사유</TableHead>
            <TableHead>제외일시</TableHead>
            <TableHead className="w-24 text-right">작업</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading && <SkeletonRows />}
          {isError && (
            <TableRow>
              <TableCell colSpan={6} className="text-center py-12 text-sm text-muted-foreground">
                자동 제외 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
              </TableCell>
            </TableRow>
          )}
          {!isLoading && !isError && items.length === 0 && (
            <TableRow>
              <TableCell colSpan={6} className="py-10">
                <EmptyState
                  icon={<ShieldOff className="h-8 w-8" />}
                  title="자동 제외된 기사가 없어요"
                  description="선택한 기간·카테고리·사유 조건에 해당하는 자동 제외 항목이 없습니다."
                />
              </TableCell>
            </TableRow>
          )}
          {!isLoading &&
            !isError &&
            items.map((item) => (
              <TableRow key={item.summaryId}>
                <TableCell className="text-sm font-medium">
                  <button
                    type="button"
                    onClick={() => onTitleClick(item)}
                    aria-label={`${item.title} 상세 보기`}
                    className="text-left hover:underline text-primary"
                  >
                    {item.title}
                  </button>
                </TableCell>
                <TableCell className="text-sm text-muted-foreground max-w-[240px]">
                  <span className="line-clamp-2" title={item.categoryName}>
                    {item.categoryName}
                  </span>
                </TableCell>
                <TableCell className="text-sm text-right tabular-nums">{item.score.toFixed(2)}</TableCell>
                <TableCell>
                  <span className="inline-flex items-center rounded-full bg-[var(--status-warning-bg)] px-2.5 py-0.5 text-xs font-medium text-[var(--status-warning-text)]">
                    {reasonLabel(item.reason)}
                  </span>
                </TableCell>
                <TableCell className="text-sm tabular-nums whitespace-nowrap text-muted-foreground">
                  {formatDateTime(item.excludedAt)}
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onRestoreClick(item)}
                    disabled={isRestoring && pendingId === item.summaryId}
                    className="h-8"
                  >
                    <RotateCcw className="h-3.5 w-3.5 mr-1" />
                    복구
                  </Button>
                </TableCell>
              </TableRow>
            ))}
        </TableBody>
      </Table>
    </div>
  );
}

/* ── 메인 페이지 ── */

export function AutoExcludeAuditPage() {
  const queryClient = useQueryClient();

  // 필터 상태
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined);
  const [reason, setReason] = useState<string | undefined>(undefined);
  const [days, setDays] = useState<number>(7);
  const [page, setPage] = useState(0);

  // 복구 대상 상태 (confirm 모달 열림 여부도 겸한다)
  const [pendingRestore, setPendingRestore] = useState<AutoExcludedItem | null>(null);

  // 드로어 선택 상태 — 제목 클릭 시 summaryId 를 저장해 드로어를 연다.
  const [selectedSummaryId, setSelectedSummaryId] = useState<string | null>(null);

  // 카테고리 필터 옵션
  const { data: categories = [] } = useQuery({
    queryKey: categoryKeys.list({ scope: "auto-exclude-audit" }),
    queryFn: () => categoryService.getAll(),
    staleTime: 60_000
  });

  // 자동 제외 목록 조회
  const listParams = {
    categoryId,
    reason,
    days,
    page,
    size: PAGE_SIZE
  };
  const {
    data: listData,
    isLoading,
    isError
  } = useQuery({
    queryKey: autoExcludedKeys.list(listParams),
    queryFn: () => categoryRuleService.listAutoExcluded(listParams),
    staleTime: 30_000
  });

  const items = listData?.items ?? [];
  const totalCount = listData?.totalCount ?? 0;
  const reasonBreakdown = listData?.reasonBreakdown ?? {};
  const totalPages = Math.ceil(totalCount / PAGE_SIZE);

  // 선택된 항목 — 드로어가 이걸 props 로 받아서 열린다.
  const selectedItem = items.find((i) => i.summaryId === selectedSummaryId) ?? null;

  // 레이스 가드 — 리스트 갱신 후 선택된 항목이 사라졌으면 드로어 닫고 안내.
  // 다른 관리자가 서버에서 이미 복구했거나 필터가 바뀌어 목록에서 빠진 경우.
  useEffect(() => {
    if (selectedSummaryId && !selectedItem && !isLoading) {
      setSelectedSummaryId(null);
      toast.info("항목이 갱신되었어요");
    }
  }, [selectedSummaryId, selectedItem, isLoading]);

  // 복구 mutation
  const {
    mutate: restore,
    isPending: isRestoring,
    variables: restoringVars
  } = useMutation({
    mutationFn: (summaryId: string) => categoryRuleService.restoreFromAutoExclude(summaryId),
    onSuccess: () => {
      toast.success("REVIEW 로 복구했어요");
      // 드로어가 열려 있으면 닫는다 — stale 항목을 계속 렌더하지 않도록.
      setSelectedSummaryId(null);
      // 자동 제외 리스트 + 리뷰 큐 배지 모두 무효화
      queryClient.invalidateQueries({ queryKey: autoExcludedKeys.all });
      queryClient.invalidateQueries({ queryKey: ["review-queue"] });
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "복구하지 못했어요"));
    }
  });

  // 필터 핸들러 — 값 변경 시 페이지를 0 으로 리셋해서 유령 페이지 방지.
  const handleCategoryChange = (value: string) => {
    setCategoryId(value === ALL_VALUE ? undefined : value);
    setPage(0);
  };

  const handleReasonChange = (value: string) => {
    setReason(value === ALL_VALUE ? undefined : value);
    setPage(0);
  };

  const handlePeriodChange = (value: number) => {
    setDays(value);
    setPage(0);
  };

  const handleConfirmRestore = () => {
    if (!pendingRestore) return;
    // 복구 확정 — mutation 은 onSettled 이후 invalidate 로 list 자체 갱신.
    restore(pendingRestore.summaryId);
    setPendingRestore(null);
  };

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div>
        <h1 className="text-2xl font-bold">자동 제외 감사</h1>
        <p className="text-sm text-muted-foreground mt-1">
          룰 엔진이 자동으로 제외한 기사를 검토하고 필요 시 REVIEW 로 복구합니다
        </p>
      </div>

      {/* 요약 카드 */}
      <SummaryHeader totalCount={totalCount} reasonBreakdown={reasonBreakdown} periodDays={days} />

      {/* 필터 바 */}
      <div className="flex items-center gap-3 flex-wrap">
        {/* 카테고리 필터 */}
        <Select value={categoryId ?? ALL_VALUE} onValueChange={handleCategoryChange}>
          <SelectTrigger className="w-52" aria-label="카테고리 필터">
            <SelectValue placeholder="전체 카테고리" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_VALUE}>전체 카테고리</SelectItem>
            {categories.map((c) => (
              <SelectItem key={c.id} value={c.id}>
                {c.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* 사유 필터 */}
        <Select value={reason ?? ALL_VALUE} onValueChange={handleReasonChange}>
          <SelectTrigger className="w-44" aria-label="사유 필터">
            <SelectValue placeholder="전체 사유" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_VALUE}>전체 사유</SelectItem>
            {REASON_FILTER_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* 기간 필터 */}
        <div className="flex items-center gap-1">
          {PERIOD_OPTIONS.map((p) => (
            <Button
              key={p.value}
              variant={days === p.value ? "default" : "outline"}
              size="sm"
              onClick={() => handlePeriodChange(p.value)}
              className={cn(days === p.value && "font-semibold")}
            >
              {p.label}
            </Button>
          ))}
        </div>
      </div>

      {/* 테이블 */}
      <AutoExcludedTable
        items={items}
        isLoading={isLoading}
        isError={isError}
        onRestoreClick={setPendingRestore}
        onTitleClick={(item) => setSelectedSummaryId(item.summaryId)}
        isRestoring={isRestoring}
        pendingId={typeof restoringVars === "string" ? restoringVars : null}
      />

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <span>
            전체 {totalCount}건 중 {page * PAGE_SIZE + 1}~{Math.min((page + 1) * PAGE_SIZE, totalCount)}건
          </span>
          <div className="flex items-center gap-1">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              <ChevronLeft className="h-4 w-4" />
              이전
            </Button>
            <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
              다음
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* 복구 확인 모달 */}
      <ConfirmModal
        open={pendingRestore !== null}
        onOpenChange={(open) => !open && setPendingRestore(null)}
        title="REVIEW 로 복구할까요?"
        description={
          pendingRestore
            ? `"${pendingRestore.title}" 을(를) REVIEW 상태로 되돌리고 검토 대기열로 다시 보냅니다.`
            : undefined
        }
        confirmLabel="복구"
        onConfirm={handleConfirmRestore}
      />

      {/* 상세 드로어 — 제목 클릭 시 열림. 드로어의 복구 버튼도 같은 ConfirmModal 을 거친다. */}
      <AutoExcludeDetailDrawer
        item={selectedItem}
        onClose={() => setSelectedSummaryId(null)}
        onRestoreClick={(item) => setPendingRestore(item)}
        isRestoring={isRestoring}
      />
    </div>
  );
}
