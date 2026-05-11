import { Info } from "lucide-react";
import { LiveTotalsCards } from "@/components/shared/LiveTotalsCards";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { EXCLUDED_REASON_LABELS } from "@/constants/personaSignalLabels";
import type {
  ExcludedPersonaItem,
  LiveSnapshotResponse,
  PortfolioStatus,
} from "@/types/personaAnalytics";
import { RecentCustomPersonasTable } from "./RecentCustomPersonasTable";

interface PortfolioSummaryProps {
  live: LiveSnapshotResponse;
  excluded: ExcludedPersonaItem[];
}

const PORTFOLIO_STATUS_LABELS: Record<PortfolioStatus, string> = {
  HEALTHY: "건강",
  WATCHING: "주시",
  DECLINING: "하락",
  UNUSED: "미사용",
};

/**
 * 포트폴리오 요약 섹션. 상단 4개 metric + 상태별 count chip + 커스텀 최근 목록.
 * Spec §1.6, §5.1 — 기존 `PresetPortfolioTable` 를 대체하는 컴팩트 뷰.
 */
export function PortfolioSummary({ live, excluded }: PortfolioSummaryProps) {
  const statusCounts = countPresetByStatus(live.presetPortfolio);

  return (
    <div className="space-y-4">
      <LiveTotalsCards totals={live.totals} />

      <div className="rounded-2xl border bg-card p-5 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-foreground">
            템플릿 상태 분포
          </h3>
          {excluded.length > 0 && (
            <ExcludedChip excluded={excluded} />
          )}
        </div>

        <div className="flex flex-wrap gap-2">
          {(Object.keys(PORTFOLIO_STATUS_LABELS) as PortfolioStatus[]).map(
            (status) => (
              <StatusChip
                key={status}
                status={status}
                count={statusCounts[status] ?? 0}
              />
            ),
          )}
        </div>
      </div>

      <RecentCustomPersonasTable
        personas={live.customSummary.recentPersonas}
      />
    </div>
  );
}

function countPresetByStatus(
  items: LiveSnapshotResponse["presetPortfolio"],
): Record<PortfolioStatus, number> {
  const counts: Record<PortfolioStatus, number> = {
    HEALTHY: 0,
    WATCHING: 0,
    DECLINING: 0,
    UNUSED: 0,
  };
  for (const item of items) {
    counts[item.status] = (counts[item.status] ?? 0) + 1;
  }
  return counts;
}

function StatusChip({
  status,
  count,
}: {
  status: PortfolioStatus;
  count: number;
}) {
  const pillClass =
    status === "HEALTHY"
      ? "bg-[var(--status-success-bg)] text-[var(--status-success-text)]"
      : status === "WATCHING"
        ? "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]"
        : status === "DECLINING"
          ? "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]"
          : "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]";

  return (
    <span
      className={
        "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold tabular-nums " +
        pillClass
      }
    >
      <span>{PORTFOLIO_STATUS_LABELS[status]}</span>
      <span>{count}</span>
    </span>
  );
}

function ExcludedChip({ excluded }: { excluded: ExcludedPersonaItem[] }) {
  return (
    <TooltipProvider delayDuration={200}>
      <Tooltip>
        <TooltipTrigger
          type="button"
          className="inline-flex items-center gap-1 rounded-full bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          aria-label="분석 대상 외 페르소나 자세히 보기"
        >
          <Info className="h-3.5 w-3.5" aria-hidden />
          <span>분석 대상 외 {excluded.length}개</span>
        </TooltipTrigger>
        <TooltipContent
          side="top"
          className="max-w-xs text-xs leading-relaxed"
        >
          <p className="mb-1 font-semibold">
            noise floor 미달로 이번 주 판정에서 제외됐어요
          </p>
          <ul className="space-y-0.5">
            {excluded.slice(0, 10).map((item) => (
              <li key={item.personaId}>
                {item.personaName} —{" "}
                {EXCLUDED_REASON_LABELS[item.reason] ?? item.reason}
              </li>
            ))}
            {excluded.length > 10 && (
              <li className="text-muted-foreground">
                … 외 {excluded.length - 10}개
              </li>
            )}
          </ul>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
