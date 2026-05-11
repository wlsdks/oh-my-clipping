import { useQuery } from "@tanstack/react-query";
import { Activity, AlertTriangle, CheckCircle2, Clock, Loader2 } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/utils/cn";
import { schedulerStatusKeys } from "@/queries/schedulerStatusKeys";
import { schedulerStatusService } from "@/services/schedulerStatusService";
import type { SchedulerStatusItem } from "@/types/schedulerStatus";

/** 1분 단위 자동 갱신 — 스케줄러 변화 속도에 맞춘다. */
const REFETCH_INTERVAL = 60_000;

/** 길어진 에러 메시지 펼침/접힘을 단순화하기 위한 최대 표시 길이. */
const ERROR_SNIPPET_MAX = 140;

/**
 * 경과 시간(초)을 "3분 전", "2시간 전" 처럼 한국어로 포맷한다.
 */
function formatStaleness(seconds: number | null): string {
  if (seconds == null) return "—";
  if (seconds < 60) return `${seconds}초 전`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  return `${days}일 전`;
}

/** ISO-8601 시각을 로컬 시간 문자열로 포맷한다. */
function formatTimestamp(iso: string | null): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString("ko-KR");
  } catch {
    return "—";
  }
}

/** ms 단위 소요시간을 사람이 읽기 쉽게 포맷한다. */
function formatDuration(ms: number | null): string {
  if (ms == null) return "—";
  if (ms < 1000) return `${ms}ms`;
  const seconds = (ms / 1000).toFixed(1);
  return `${seconds}s`;
}

/** status 뱃지 */
function StatusBadge({ item }: { item: SchedulerStatusItem }) {
  if (item.status === "FAILED") {
    return (
      <span
        className="inline-flex items-center gap-1 rounded-full bg-[var(--status-danger-bg)] px-2 py-0.5 text-xs font-medium text-[var(--status-danger-text)]"
        aria-label="실패 상태"
      >
        <AlertTriangle size={12} aria-hidden="true" />
        실패
      </span>
    );
  }
  if (item.lastResult === "success") {
    return (
      <span
        className="inline-flex items-center gap-1 rounded-full bg-[var(--status-success-bg)] px-2 py-0.5 text-xs font-medium text-[var(--status-success-text)]"
        aria-label="정상 상태"
      >
        <CheckCircle2 size={12} aria-hidden="true" />
        정상
      </span>
    );
  }
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground"
      aria-label="미실행"
      title="서버 시작 후 아직 실행되지 않은 스케줄러예요"
    >
      <Clock size={12} aria-hidden="true" />
      대기
    </span>
  );
}

/**
 * 스케줄러 실시간 상태 패널.
 * 시스템 상태 페이지 하단에 렌더되어 1분 단위 폴링으로 갱신된다.
 */
export function SchedulerStatusPanel() {
  const { data, isLoading, isError, isFetching, refetch } = useQuery({
    queryKey: schedulerStatusKeys.list(),
    queryFn: () => schedulerStatusService.list(),
    refetchInterval: REFETCH_INTERVAL,
    refetchIntervalInBackground: false,
  });

  const failedCount = data?.filter((d) => d.status === "FAILED").length ?? 0;

  return (
    <div className="rounded-2xl bg-card p-6 shadow-sm border">
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2">
          <Activity className="h-5 w-5 text-[var(--status-neutral-text)]" />
          <h2 className="text-base font-semibold">스케줄러 실시간 상태</h2>
          {failedCount > 0 && (
            <span className="inline-flex items-center gap-1 rounded-full bg-[var(--status-danger-bg)] px-2 py-0.5 text-xs font-medium text-[var(--status-danger-text)]">
              실패 {failedCount}건
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={() => refetch()}
          disabled={isFetching}
          className="inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-medium text-muted-foreground hover:bg-muted transition-colors disabled:opacity-50"
          aria-label="새로고침"
        >
          {isFetching ? (
            <Loader2 className="h-3 w-3 animate-spin" />
          ) : (
            <Activity className="h-3 w-3" />
          )}
          새로고침
        </button>
      </div>

      {isError && (
        <div
          role="alert"
          className="rounded-xl bg-[var(--status-danger-bg)] border border-[var(--status-danger-bg)] p-4 text-sm text-[var(--status-danger-text)]"
        >
          스케줄러 상태를 불러오지 못했어요. 잠시 후 자동으로 다시 시도해요
        </div>
      )}

      {isLoading && !data && (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div
              key={i}
              className="h-10 w-full animate-pulse rounded-lg bg-muted"
            />
          ))}
        </div>
      )}

      {data && data.length === 0 && (
        <p className="text-sm text-muted-foreground py-6 text-center">
          등록된 스케줄러가 없어요
        </p>
      )}

      {data && data.length > 0 && (
        <div className="rounded-xl border bg-card overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>이름</TableHead>
                <TableHead>상태</TableHead>
                <TableHead>스케줄</TableHead>
                <TableHead>마지막 실행</TableHead>
                <TableHead>소요</TableHead>
                <TableHead>다음 실행</TableHead>
                <TableHead>메시지</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((item) => (
                <TableRow
                  key={item.trackerKey ?? item.name}
                  className={cn(item.status === "FAILED" && "bg-[var(--status-danger-bg)]/30")}
                >
                  <TableCell className="text-sm font-medium">
                    <div className="flex flex-col">
                      <span>{item.name}</span>
                      <span className="text-xs text-muted-foreground">
                        {item.description}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <StatusBadge item={item} />
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground font-mono">
                    {item.schedule}
                  </TableCell>
                  <TableCell className="text-xs tabular-nums">
                    <div className="flex flex-col">
                      <span>{formatTimestamp(item.lastRunAt)}</span>
                      {item.stalenessSeconds != null && (
                        <span className="text-[10px] text-muted-foreground">
                          {formatStaleness(item.stalenessSeconds)}
                        </span>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground tabular-nums">
                    {formatDuration(item.lastDurationMs)}
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground tabular-nums">
                    {formatTimestamp(item.nextRunAt)}
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground max-w-xs">
                    {item.lastError ? (
                      <span
                        className="text-[var(--status-danger-text)]"
                        title={item.lastError}
                      >
                        {item.lastError.length > ERROR_SNIPPET_MAX
                          ? `${item.lastError.slice(0, ERROR_SNIPPET_MAX)}…`
                          : item.lastError}
                      </span>
                    ) : (
                      "—"
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <p className="mt-3 text-xs text-muted-foreground">
        1분마다 자동 갱신 · 현재 인스턴스의 인메모리 기록 기준
      </p>
    </div>
  );
}
