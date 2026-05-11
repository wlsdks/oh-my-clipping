import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, History } from "lucide-react";
import { InfoTooltip } from "@/components/shared/InfoTooltip";
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
import { pipelineKeys } from "@/queries/pipelineKeys";
import { pipelineService } from "@/services/pipelineService";
import type {
  PipelineRunRecord,
  PipelineStepStatusType,
  PipelineStepName,
} from "@/types/pipeline";
import type { Category } from "@/types/category";
import { GanttTimeline } from "./GanttTimeline";

/* ── 상수 ── */

const PAGE_SIZE = 20;

const ALL_CATEGORIES_VALUE = "__all__";

const PERIODS: { key: PeriodKey; label: string }[] = [
  { key: "this-week", label: "이번 주" },
  { key: "last-week", label: "지난 주" },
  { key: "this-month", label: "이번 달" },
  { key: "last-month", label: "지난 달" },
];

/** 스텝 상태 → 뱃지 스타일 + 한국어 라벨 */
const STEP_STATUS_CONFIG: Record<
  PipelineStepStatusType,
  { dot: string; text: string; label: string }
> = {
  SUCCEEDED: { dot: "bg-[var(--status-success-text)]", text: "text-[var(--status-success-text)]", label: "성공" },
  FAILED: { dot: "bg-[var(--status-danger-text)]", text: "text-[var(--status-danger-text)]", label: "실패" },
  RUNNING: { dot: "bg-[var(--status-neutral-text)] animate-pulse", text: "text-[var(--status-neutral-text)]", label: "실행 중" },
  SKIPPED: { dot: "bg-muted-foreground", text: "text-muted-foreground", label: "건너뛰기" },
};

const STEP_ORDER: PipelineStepName[] = ["COLLECT", "SUMMARIZE", "DIGEST"];

/* ── 헬퍼 ── */

/** 스텝 트레이스 배열에서 특정 스텝의 상태를 찾는다. */
function findStepStatus(
  traces: PipelineRunRecord["stepTraces"],
  step: PipelineStepName
): PipelineStepStatusType | null {
  return traces?.find((t) => t.step === step)?.status ?? null;
}

/** ISO datetime → 짧은 한국어 표시 (MM/DD HH:mm) */
function formatShortDatetime(iso: string): string {
  const d = new Date(iso);
  const month = d.getMonth() + 1;
  const day = d.getDate();
  const hour = String(d.getHours()).padStart(2, "0");
  const minute = String(d.getMinutes()).padStart(2, "0");
  return `${month}/${day} ${hour}:${minute}`;
}

/** 영어 에러 메시지에서 UUID/기술 용어를 정리하여 한글 안내로 변환 */
function sanitizeErrorMessage(msg: string): string {
  // UUID 제거 (00000000-0000-0000-0000-000000000309 같은 패턴)
  let cleaned = msg.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, "").trim();
  // "for source" 등 불필요한 영어 구문 정리
  cleaned = cleaned.replace(/\s*for source\s*/gi, "").trim();
  // 끝에 남는 콜론/공백 정리
  cleaned = cleaned.replace(/:\s*$/, "").trim();
  return cleaned || msg;
}

/** ms → 사람이 읽기 쉬운 형태 */
function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return "-";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}초`;
}

/* ── 스텝 상태 뱃지 ── */

function StepStatusBadge({ status }: { status: PipelineStepStatusType | null }) {
  if (!status) return <span className="text-xs text-muted-foreground">-</span>;
  const cfg = STEP_STATUS_CONFIG[status];
  return (
    <span className={cn("inline-flex items-center gap-1 text-xs font-medium", cfg.text)}>
      <span className={cn("inline-block h-1.5 w-1.5 rounded-full", cfg.dot)} />
      {cfg.label}
    </span>
  );
}

/* ── 스켈레톤 로우 ── */

function SkeletonRows() {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
        <TableRow key={i}>
          {Array.from({ length: 7 }).map((_, j) => (
            <TableCell key={j}>
              <div className="h-4 w-full animate-pulse rounded bg-muted" />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  );
}

/* ── 메인 컴포넌트 ── */

interface PipelineHistoryProps {
  categories: Category[];
  /** URL에서 전달된 페르소나 필터. 지정 시 해당 페르소나 카테고리의 실행만 조회한다. */
  personaIdFilter?: string;
  /** URL ?runId= 파라미터에서 내려오는 자동 펼침 대상 run ID */
  expandedRunId?: string | null;
  /** 행을 펼치거나 닫을 때 부모에게 알림 (runId=null이면 닫힘) */
  onExpandChange?: (runId: string | null) => void;
}

export function PipelineHistory({ categories, personaIdFilter, expandedRunId, onExpandChange }: PipelineHistoryProps) {
  const [filterCategoryId, setFilterCategoryId] = useState<string | undefined>();
  const [period, setPeriod] = useState<PeriodKey>("this-week");
  const [page, setPage] = useState(0);
  // URL param이 없을 때는 로컬 상태로 fallback
  const [localExpandedId, setLocalExpandedId] = useState<string | null>(null);
  const expandedId = expandedRunId !== undefined ? (expandedRunId ?? null) : localExpandedId;

  const { from, to } = getPeriodRange(period);

  // 쿼리 파라미터 구성
  const queryParams = new URLSearchParams();
  queryParams.set("page", String(page));
  queryParams.set("size", String(PAGE_SIZE));
  queryParams.set("from", from);
  queryParams.set("to", to);
  if (filterCategoryId) queryParams.set("categoryId", filterCategoryId);
  if (personaIdFilter) queryParams.set("personaId", personaIdFilter);

  const { data, isLoading, isError } = useQuery({
    queryKey: pipelineKeys.runsList({
      categoryId: filterCategoryId,
      from,
      to,
      page,
      personaId: personaIdFilter
    }),
    queryFn: () => pipelineService.listRuns(queryParams),
  });

  const runs = data?.content ?? [];
  const totalCount = data?.totalCount ?? 0;
  const totalPages = Math.ceil(totalCount / PAGE_SIZE);

  // 카테고리 선택 핸들러
  const handleCategoryChange = (value: string) => {
    setFilterCategoryId(value === ALL_CATEGORIES_VALUE ? undefined : value);
    setPage(0);
  };

  // 기간 변경 핸들러
  const handlePeriodChange = (key: PeriodKey) => {
    setPeriod(key);
    setPage(0);
  };

  // 행 클릭 → 아코디언 토글 (URL 파라미터 또는 로컬 상태 업데이트)
  const toggleExpand = (runId: string) => {
    const next = expandedId === runId ? null : runId;
    if (onExpandChange) {
      onExpandChange(next);
    } else {
      setLocalExpandedId(next);
    }
  };

  return (
    <div className="space-y-4">
      {/* 필터 영역 */}
      <div className="flex items-center gap-3 flex-wrap">
        {/* 카테고리 필터 */}
        <Select
          value={filterCategoryId ?? ALL_CATEGORIES_VALUE}
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
      </div>

      {/* 테이블 */}
      <div className="rounded-xl border bg-card overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>실행 시각</TableHead>
              <TableHead>카테고리</TableHead>
              <TableHead className="text-center">수집</TableHead>
              <TableHead className="text-center">요약</TableHead>
              <TableHead className="text-center">다이제스트</TableHead>
              <TableHead className="text-right">소요시간</TableHead>
              <TableHead className="text-right">처리 건수</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {/* 로딩 */}
            {isLoading && <SkeletonRows />}

            {/* 에러 */}
            {isError && (
              <TableRow>
                <TableCell colSpan={7} className="text-center py-12 text-sm text-muted-foreground">
                  이력을 불러오는 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.
                </TableCell>
              </TableRow>
            )}

            {/* 빈 상태 */}
            {!isLoading && !isError && runs.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="text-center py-16">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <History className="h-8 w-8" />
                    <p className="text-sm">파이프라인을 실행하면 여기에 이력이 표시돼요</p>
                  </div>
                </TableCell>
              </TableRow>
            )}

            {/* 데이터 행 */}
            {!isLoading &&
              !isError &&
              runs.map((run) => (
                <RunRow
                  key={run.id}
                  run={run}
                  isExpanded={expandedId === run.id}
                  onToggle={() => toggleExpand(run.id)}
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
    </div>
  );
}

/* ── 행 컴포넌트 ── */

function RunRow({
  run,
  isExpanded,
  onToggle,
}: {
  run: PipelineRunRecord;
  isExpanded: boolean;
  onToggle: () => void;
}) {
  const traces = run.stepTraces ?? [];
  const processedCount = run.totalCollected + run.totalSummarized + run.totalDigestSelected;
  const isFailed = run.status === "FAILED";
  const isRunning = run.status === "RUNNING";

  // 스텝 트레이스가 없고 전체 실행이 FAILED이면, 시작 전 실패로 간주
  const hasNoTraces = traces.length === 0;

  return (
    <>
      {/* 데이터 행 */}
      <TableRow
        className={cn(
          "cursor-pointer",
          isFailed && "border-l-2 border-l-[var(--status-danger-text)]",
          isRunning && "border-l-2 border-l-[var(--status-neutral-text)]"
        )}
        onClick={onToggle}
      >
        <TableCell className="text-sm tabular-nums">
          <div className="flex items-center gap-2">
            {formatShortDatetime(run.startedAt)}
            {isFailed && hasNoTraces && (
              <span className="inline-flex items-center gap-1 rounded-full bg-[var(--status-danger-bg)] px-2 py-0.5 text-[10px] font-medium text-[var(--status-danger-text)]">
                실패
              </span>
            )}
          </div>
        </TableCell>
        <TableCell className="text-sm">
          <div>
            {run.categoryName ?? "-"}
            {isFailed && hasNoTraces && run.errorMessage && (
              <p className="text-xs text-[var(--status-danger-text)] mt-0.5 truncate max-w-[200px]">{sanitizeErrorMessage(run.errorMessage)}</p>
            )}
          </div>
        </TableCell>
        {STEP_ORDER.map((step) => {
          // 트레이스 없이 FAILED이면 첫 스텝에 실패 표시
          const stepStatus = findStepStatus(traces, step);
          const showFailedPlaceholder = !stepStatus && isFailed && hasNoTraces && step === "COLLECT";
          return (
            <TableCell key={step} className="text-center">
              {showFailedPlaceholder ? (
                <StepStatusBadge status="FAILED" />
              ) : (
                <StepStatusBadge status={stepStatus} />
              )}
            </TableCell>
          );
        })}
        <TableCell className="text-right text-sm tabular-nums">
          {formatDuration(run.durationMs)}
        </TableCell>
        <TableCell className="text-right text-sm tabular-nums">
          {processedCount}
        </TableCell>
      </TableRow>

      {/* 아코디언 확장 영역 */}
      {isExpanded && (
        <TableRow>
          <TableCell colSpan={7} className="bg-muted/30 px-6 py-4">
            <div className="space-y-3">
              {/* Gantt 타임라인 */}
              {traces.length > 0 && run.durationMs != null && (
                <GanttTimeline traces={traces} totalDurationMs={run.durationMs} />
              )}

              {/* 에러 메시지 */}
              {run.errorMessage && (
                <div className="rounded-lg bg-[var(--status-danger-bg)] p-3 text-sm text-[var(--status-danger-text)]">
                  <span className="font-medium">오류: </span>
                  {sanitizeErrorMessage(run.errorMessage)}
                </div>
              )}

              {/* 실행 트리거 정보 */}
              {run.triggeredBy && (
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <span className="font-medium">실행 방식:</span>
                  <span>{run.triggeredBy === "scheduled" ? "스케줄 자동 실행" : run.triggeredBy === "admin-manual" ? "관리자 수동 트리거" : run.triggeredBy}</span>
                  <InfoTooltip
                    ariaLabel="실행 방식 설명"
                    content="scheduled = 스케줄러가 자동으로 트리거한 실행 / admin-manual = 관리자가 수동으로 트리거한 실행"
                  />
                </div>
              )}

              {/* 트레이스가 없는 경우 */}
              {traces.length === 0 && !run.errorMessage && (
                <p className="text-sm text-muted-foreground">상세 정보가 없어요.</p>
              )}
            </div>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}
