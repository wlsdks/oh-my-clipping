import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from "recharts";
import { RefreshCw } from "lucide-react";
import { pipelineAnalyticsKeys } from "@/queries/pipelineAnalyticsKeys";
import { pipelineAnalyticsService } from "@/services/pipelineAnalyticsService";
import type { PipelineDailyRow } from "@/services/pipelineAnalyticsService";
import { cn } from "@/utils/cn";
import { shortDate } from "@/utils/date";
import { STALE_TIMES } from "@/lib/queryConfig";
import { TossTooltip, GRID_PROPS, AXIS_PROPS, getChartColors } from "@/utils/chartTheme";

/* ── 상수 ── */

const PERIOD_OPTIONS = [7, 14, 30] as const;
type Period = (typeof PERIOD_OPTIONS)[number];

const REJECT_REASON_LABELS: Record<string, string> = {
  CHARS_TOO_SHORT: "글자 수 부족",
  PARAGRAPHS_TOO_FEW: "문단 수 부족",
  SENTENCES_TOO_FEW: "문장 수 부족",
  SENTENCES_AFTER_CLAMP: "길이 제한 후 문장 부족",
  OTHER: "기타",
};

function rejectLabel(key: string): string {
  return REJECT_REASON_LABELS[key] ?? key;
}

/* ── 로딩 스켈레톤 ── */

function ChartSkeleton() {
  return (
    <div className="animate-pulse h-48 rounded-xl bg-muted/30 border border-border" />
  );
}

/* ── 빈 상태 ── */

function ChartEmpty({ message }: { message: string }) {
  return (
    <p className="text-sm text-muted-foreground text-center py-8">
      {message}
    </p>
  );
}

/* ── 기간 선택 Pill 버튼 ── */

function PeriodSelector({
  value,
  onChange,
}: {
  value: Period;
  onChange: (p: Period) => void;
}) {
  return (
    <div className="flex items-center gap-1.5">
      {PERIOD_OPTIONS.map((p) => (
        <button
          type="button"
          key={p}
          onClick={() => onChange(p)}
          className={cn(
            "rounded-full px-3 py-1 text-xs font-medium transition-colors",
            value === p
              ? "bg-primary text-primary-foreground"
              : "bg-muted text-muted-foreground hover:bg-muted/80",
          )}
        >
          {p}일
        </button>
      ))}
    </div>
  );
}

/* ── 커스텀 퍼센트 툴팁 ── */

interface PercentTooltipEntry {
  color?: string;
  name?: string;
  value?: number | string | null;
}

interface PercentTooltipProps {
  active?: boolean;
  payload?: PercentTooltipEntry[];
  label?: string | number;
}

function PercentTooltip({ active, payload, label }: PercentTooltipProps) {
  if (!active || !payload?.length) return null;
  const total = payload.reduce((s, e) => s + Number(e.value ?? 0), 0);

  return (
    <div className="rounded-xl border bg-card px-3 py-2 shadow-lg text-sm">
      <p className="font-medium text-foreground mb-1">{label}</p>
      {payload.map((entry, i) => {
        const val = Number(entry.value ?? 0);
        const pct = total > 0 ? ((val / total) * 100).toFixed(1) : "0.0";
        return (
          <div key={i} className="flex items-center gap-2 text-xs">
            <span
              className="inline-block w-2 h-2 rounded-full shrink-0"
              style={{ background: entry.color }}
            />
            <span className="text-muted-foreground">{entry.name}</span>
            <span className="font-medium ml-auto">
              {val.toLocaleString()}건 ({pct}%)
            </span>
          </div>
        );
      })}
    </div>
  );
}

/* ── 1) 수집 건수 차트 ── */

function CollectionChart({ data }: { data: PipelineDailyRow[] }) {
  const chartData = data.map((r) => ({
    label: shortDate(r.date),
    collected: r.collected,
    duplicateSkipped: r.duplicateSkipped,
  }));

  const hasData = chartData.some((d) => d.collected > 0 || d.duplicateSkipped > 0);

  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <h3 className="text-sm font-semibold mb-4">수집 건수</h3>
      {!hasData ? (
        <ChartEmpty message="수집 데이터가 아직 없어요" />
      ) : (
        <div className="h-[200px]">
          <ResponsiveContainer width="100%" height="100%" aria-label="일별 수집 건수 차트">
            <BarChart data={chartData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
              <CartesianGrid {...GRID_PROPS} />
              <XAxis dataKey="label" {...AXIS_PROPS} />
              <YAxis {...AXIS_PROPS} allowDecimals={false} />
              <Tooltip content={<TossTooltip formatter={(v) => `${v.toLocaleString()}건`} />} />
              <Bar dataKey="collected" name="수집" fill="var(--chart-1)" radius={[4, 4, 0, 0]} />
              <Bar
                dataKey="duplicateSkipped"
                name="중복 제외"
                fill="var(--color-muted-foreground)"
                radius={[4, 4, 0, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}

/* ── 2) 요약 성공/reject/실패 차트 ── */

function SummarizeChart({ data }: { data: PipelineDailyRow[] }) {
  const chartData = data.map((r) => ({
    label: shortDate(r.date),
    succeeded: r.summarizeSucceeded,
    rejected: r.summarizeRejected,
    failed: r.summarizeFailed,
  }));

  const hasData = chartData.some(
    (d) => d.succeeded > 0 || d.rejected > 0 || d.failed > 0,
  );

  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <h3 className="text-sm font-semibold mb-4">요약 성공률</h3>
      {!hasData ? (
        <ChartEmpty message="요약 데이터가 아직 없어요" />
      ) : (
        <div className="h-[200px]">
          <ResponsiveContainer width="100%" height="100%" aria-label="일별 요약 성공률 차트">
            <BarChart
              data={chartData}
              margin={{ top: 5, right: 20, bottom: 5, left: 0 }}
              stackOffset="none"
            >
              <CartesianGrid {...GRID_PROPS} />
              <XAxis dataKey="label" {...AXIS_PROPS} />
              <YAxis {...AXIS_PROPS} allowDecimals={false} />
              <Tooltip content={<PercentTooltip />} />
              <Bar
                dataKey="succeeded"
                name="성공"
                stackId="summarize"
                fill="var(--status-success-text)"
                radius={[0, 0, 0, 0]}
              />
              <Bar
                dataKey="rejected"
                name="제외"
                stackId="summarize"
                fill="var(--status-warning-text)"
                radius={[0, 0, 0, 0]}
              />
              <Bar
                dataKey="failed"
                name="실패"
                stackId="summarize"
                fill="var(--status-danger-text)"
                radius={[4, 4, 0, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}

/* ── 3) 발송 현황 차트 ── */

function DeliveryChart({ data }: { data: PipelineDailyRow[] }) {
  const chartData = data.map((r) => ({
    label: shortDate(r.date),
    sent: r.deliverySent,
    skipped: r.deliverySkipped,
    failed: r.deliveryFailed,
  }));

  const hasData = chartData.some(
    (d) => d.sent > 0 || d.skipped > 0 || d.failed > 0,
  );

  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <h3 className="text-sm font-semibold mb-4">발송 현황</h3>
      {!hasData ? (
        <ChartEmpty message="발송 데이터가 아직 없어요" />
      ) : (
        <div className="h-[200px]">
          <ResponsiveContainer width="100%" height="100%" aria-label="일별 발송 현황 차트">
            <BarChart
              data={chartData}
              margin={{ top: 5, right: 20, bottom: 5, left: 0 }}
              stackOffset="none"
            >
              <CartesianGrid {...GRID_PROPS} />
              <XAxis dataKey="label" {...AXIS_PROPS} />
              <YAxis {...AXIS_PROPS} allowDecimals={false} />
              <Tooltip content={<PercentTooltip />} />
              <Bar
                dataKey="sent"
                name="발송"
                stackId="delivery"
                fill="var(--status-success-text)"
                radius={[0, 0, 0, 0]}
              />
              <Bar
                dataKey="skipped"
                name="건너뜀"
                stackId="delivery"
                fill="var(--status-neutral-text)"
                radius={[0, 0, 0, 0]}
              />
              <Bar
                dataKey="failed"
                name="실패"
                stackId="delivery"
                fill="var(--status-danger-text)"
                radius={[4, 4, 0, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}

/* ── 4) Reject 사유 분포 ── */

function RejectReasonsChart({ reasons }: { reasons: Record<string, number> }) {
  const entries = Object.entries(reasons)
    .map(([key, count]) => ({ key, label: rejectLabel(key), count }))
    .filter((e) => e.count > 0)
    .sort((a, b) => b.count - a.count);

  const total = entries.reduce((s, e) => s + e.count, 0);
  const chartColors = getChartColors();

  // 도넛 차트: 2개 이상의 사유가 있을 때
  if (entries.length >= 2) {
    return (
      <div className="rounded-xl border border-border bg-card p-4">
        <h3 className="text-sm font-semibold mb-4">Reject 사유 분포</h3>
        <div className="h-[200px]">
          <ResponsiveContainer width="100%" height="100%" aria-label="Reject 사유 분포 차트">
            <PieChart>
              <Pie
                data={entries}
                dataKey="count"
                nameKey="label"
                cx="50%"
                cy="50%"
                innerRadius={50}
                outerRadius={80}
                paddingAngle={2}
              >
                {entries.map((_, i) => (
                  <Cell key={i} fill={chartColors[i % chartColors.length]} />
                ))}
              </Pie>
              <Tooltip
                content={
                  <TossTooltip
                    formatter={(v) => {
                      const pct = total > 0 ? ((v / total) * 100).toFixed(1) : "0.0";
                      return `${v.toLocaleString()}건 (${pct}%)`;
                    }}
                  />
                }
              />
              <Legend
                verticalAlign="bottom"
                iconType="circle"
                wrapperStyle={{ fontSize: 12, paddingTop: 12 }}
              />
            </PieChart>
          </ResponsiveContainer>
        </div>
        <p className="text-center text-xs text-muted-foreground mt-2">
          총 {total.toLocaleString()}건 reject
        </p>
      </div>
    );
  }

  // 바 리스트: 0~1개 사유이거나 데이터 없음
  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <h3 className="text-sm font-semibold mb-4">Reject 사유 분포</h3>
      {entries.length === 0 ? (
        <ChartEmpty message="Reject 데이터가 아직 없어요" />
      ) : (
        <div className="space-y-3" aria-label="Reject 사유 분포 차트">
          {entries.map((entry) => {
            const widthPct = total > 0 ? (entry.count / total) * 100 : 0;
            return (
              <div key={entry.key} className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-muted-foreground">{entry.label}</span>
                  <span className="font-medium tabular-nums">
                    {entry.count.toLocaleString()}건
                  </span>
                </div>
                <div className="h-2 w-full rounded-full bg-muted">
                  <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{
                      width: `${Math.max(widthPct, 2)}%`,
                      backgroundColor: chartColors[0],
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

/* ── 메인 섹션 ── */

export function PipelineChartsSection() {
  const [days, setDays] = useState<Period>(7);

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: pipelineAnalyticsKeys.daily(days),
    queryFn: () => pipelineAnalyticsService.getDaily(days),
    staleTime: STALE_TIMES.FREQUENT,
  });

  return (
    <div className="space-y-4">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">파이프라인 추이</h2>
        <div className="flex items-center gap-3">
          <PeriodSelector value={days} onChange={setDays} />
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            aria-label="파이프라인 차트 새로고침"
            className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1 text-xs font-medium text-muted-foreground hover:bg-muted transition-colors disabled:opacity-50"
          >
            <RefreshCw className={cn("h-3 w-3", isFetching && "animate-spin")} />
          </button>
        </div>
      </div>

      {/* 에러 상태 */}
      {isError && (
        <div className="rounded-2xl bg-[var(--status-danger-bg)] border border-[var(--status-danger-bg)] p-6 text-center space-y-3">
          <p className="text-sm text-[var(--status-danger-text)]">
            파이프라인 데이터를 불러올 수 없어요
          </p>
          <button
            onClick={() => refetch()}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
          >
            다시 시도
          </button>
        </div>
      )}

      {/* 로딩 스켈레톤 */}
      {isLoading && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <ChartSkeleton />
          <ChartSkeleton />
          <ChartSkeleton />
          <ChartSkeleton />
        </div>
      )}

      {/* 차트 그리드 */}
      {data && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <CollectionChart data={data.days} />
          <SummarizeChart data={data.days} />
          <DeliveryChart data={data.days} />
          <RejectReasonsChart reasons={data.periodSummary.rejectReasons} />
        </div>
      )}
    </div>
  );
}
