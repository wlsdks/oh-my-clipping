import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { personaAnalyticsService } from "@/services/personaAnalyticsService";
import { personaAnalyticsKeys } from "@/queries/personaAnalyticsKeys";
import { BackfillButton } from "./BackfillButton";
import {
  TossTooltip,
  GRID_PROPS,
  LINE_PROPS,
  AXIS_PROPS,
} from "@/utils/chartTheme";
import type {
  PersonaTrendSeries,
  WeeklyTrendsResponse,
} from "@/types/personaAnalytics";
import { hasVariation, isInProgressWeek } from "@/utils/personaRisk";

// ---------------------------------------------------------------------------
// 상수
// ---------------------------------------------------------------------------

const WEEK_OPTIONS = [4, 12, 26, 52] as const;
type WeekOption = (typeof WEEK_OPTIONS)[number];

const METRIC_OPTIONS = [
  { value: "activeSubs", label: "활성 구독" },
  { value: "engagedUsers", label: "참여 사용자" },
  { value: "deliveredCount", label: "발송 건수" },
] as const;
type MetricKey = "activeSubs" | "engagedUsers" | "deliveredCount";

const FILTER_OPTIONS = [
  { value: "all", label: "전체" },
  { value: "preset", label: "템플릿만" },
  { value: "custom", label: "커스텀만" },
] as const;
type FilterOption = "all" | "preset" | "custom";

/** CSS 변수 기반 라인 색상 (var 미지원 환경 fallback 포함). 8색 팔레트 (§5.2). */
const CHART_LINE_COLORS = [
  "var(--chart-1, #3b82f6)",
  "var(--chart-2, #10b981)",
  "var(--chart-3, #f59e0b)",
  "var(--chart-4, #8b5cf6)",
  "var(--chart-5, #06b6d4)",
  "var(--chart-6, #ec4899)",
  "var(--chart-7, #64a6a3)",
  "var(--chart-8, #94a37a)",
];

function chartLineColor(index: number): string {
  return CHART_LINE_COLORS[index % CHART_LINE_COLORS.length];
}

// ---------------------------------------------------------------------------
// 헬퍼
// ---------------------------------------------------------------------------

/** ISO 주차 문자열 "2026-W14" → "W14" 로 단축. */
function shortWeekLabel(weekStr: string): string {
  const parts = weekStr.split("-");
  return parts[parts.length - 1] ?? weekStr;
}

/**
 * WeeklyTrendsResponse 를 recharts 용 rows 로 변환한다.
 * 각 row 는 { week: "W14", [personaId]: number | null, ... } 형태.
 *
 * 마지막 주가 "진행 중" 이면 해당 주 모든 시리즈 값을 `null` 로 치환해
 * 미집계 구간의 0 급락 표시를 막는다 (§5.2).
 */
function buildChartData(
  data: WeeklyTrendsResponse,
  metric: MetricKey,
  series: PersonaTrendSeries[],
  now: Date = new Date(),
) {
  const lastIdx = data.weeks.length - 1;
  const inProgressLast =
    lastIdx >= 0 && isInProgressWeek(data.weeks[lastIdx], now);

  return data.weeks.map((week, weekIdx) => {
    const row: Record<string, string | number | null> = {
      week: shortWeekLabel(week),
    };
    for (const s of series) {
      const raw = s[metric][weekIdx];
      if (inProgressLast && weekIdx === lastIdx) {
        row[s.personaId] = null;
      } else {
        row[s.personaId] = raw ?? 0;
      }
    }
    return row;
  });
}

// ---------------------------------------------------------------------------
// 서브 컴포넌트
// ---------------------------------------------------------------------------

function ChartSkeleton() {
  return (
    <div
      data-testid="trends-skeleton"
      className="animate-pulse h-64 rounded-xl bg-muted/30 border border-border"
    />
  );
}

function ChartEmptyState({ weeks }: { weeks: number }) {
  return (
    <div
      data-testid="trends-empty"
      className="rounded-xl border border-border bg-card p-8 text-center space-y-3"
    >
      <p className="font-semibold text-foreground">아직 집계된 트렌드 데이터가 없어요</p>
      <p className="text-sm text-muted-foreground">
        지난 주의 데이터를 집계하면 주간 트렌드를 확인할 수 있어요.
      </p>
      <div className="flex justify-center">
        <BackfillButton weeks={weeks} />
      </div>
    </div>
  );
}

interface FilterBarProps {
  weeks: WeekOption;
  metric: MetricKey;
  filter: FilterOption;
  variationOnly: boolean;
  onWeeksChange: (v: WeekOption) => void;
  onMetricChange: (v: MetricKey) => void;
  onFilterChange: (v: FilterOption) => void;
  onVariationOnlyChange: (v: boolean) => void;
}

function FilterBar({
  weeks,
  metric,
  filter,
  variationOnly,
  onWeeksChange,
  onMetricChange,
  onFilterChange,
  onVariationOnlyChange,
}: FilterBarProps) {
  const selectClass =
    "h-8 rounded-lg border border-input bg-background px-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring";

  return (
    <div className="flex flex-wrap items-center gap-2">
      <select
        aria-label="조회 기간"
        className={selectClass}
        value={weeks}
        onChange={(e) => onWeeksChange(Number(e.target.value) as WeekOption)}
      >
        {WEEK_OPTIONS.map((w) => (
          <option key={w} value={w}>
            최근 {w}주
          </option>
        ))}
      </select>

      <select
        aria-label="지표"
        className={selectClass}
        value={metric}
        onChange={(e) => onMetricChange(e.target.value as MetricKey)}
      >
        {METRIC_OPTIONS.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>

      <select
        aria-label="페르소나 유형"
        className={selectClass}
        value={filter}
        onChange={(e) => onFilterChange(e.target.value as FilterOption)}
      >
        {FILTER_OPTIONS.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>

      <label className="inline-flex items-center gap-1.5 text-sm text-foreground">
        <input
          type="checkbox"
          className="h-4 w-4 rounded border-input accent-[var(--color-primary,currentColor)] focus:ring-2 focus:ring-ring"
          checked={variationOnly}
          onChange={(e) => onVariationOnlyChange(e.target.checked)}
          aria-label="변화 있는 페르소나만 보기"
        />
        <span>변화 있는 페르소나만</span>
      </label>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 메인 컴포넌트
// ---------------------------------------------------------------------------

/**
 * 페르소나별 주간 트렌드 라인 차트.
 *
 * - 기간(4/12/26/52주), 지표(활성 구독/참여/발송), 유형(전체/프리셋/커스텀) 필터
 * - 데이터 없으면 과거 데이터 집계 CTA 노출
 * - 로딩 중 스켈레톤 표시
 */
export function WeeklyTrendsChart() {
  const [weeks, setWeeks] = useState<WeekOption>(12);
  const [metric, setMetric] = useState<MetricKey>("activeSubs");
  const [filter, setFilter] = useState<FilterOption>("all");
  // 스펙 §5.2: 기본 ON. 차트가 flat 선 다발로 읽히는 문제를 완화.
  const [variationOnly, setVariationOnly] = useState(true);

  const trendsQuery = useQuery({
    queryKey: personaAnalyticsKeys.trends(weeks),
    queryFn: () => personaAnalyticsService.getTrends(weeks),
    staleTime: 5 * 60 * 1000,
  });

  const metricLabel =
    METRIC_OPTIONS.find((o) => o.value === metric)?.label ?? metric;

  const typeFilteredSeries =
    trendsQuery.data?.series.filter((s) => {
      if (filter === "preset") return s.isPreset;
      if (filter === "custom") return !s.isPreset;
      return true;
    }) ?? [];

  // 변화 있는 페르소나만: 선택된 지표 시계열의 min-max diff >= 1 인 것.
  const visibleSeries = variationOnly
    ? typeFilteredSeries.filter((s) => hasVariation(s[metric] ?? []))
    : typeFilteredSeries;

  const hasData =
    trendsQuery.data &&
    trendsQuery.data.weeks.length > 0 &&
    visibleSeries.length > 0;

  const chartData = hasData
    ? buildChartData(trendsQuery.data!, metric, visibleSeries)
    : [];

  return (
    <div className="rounded-2xl border bg-card p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-semibold">페르소나 주간 트렌드</h3>
        <div className="flex items-center gap-2 flex-wrap">
          <FilterBar
            weeks={weeks}
            metric={metric}
            filter={filter}
            variationOnly={variationOnly}
            onWeeksChange={setWeeks}
            onMetricChange={setMetric}
            onFilterChange={setFilter}
            onVariationOnlyChange={setVariationOnly}
          />
          <BackfillButton weeks={weeks} />
        </div>
      </div>

      {trendsQuery.isLoading && <ChartSkeleton />}

      {!trendsQuery.isLoading && !hasData && <ChartEmptyState weeks={weeks} />}

      {!trendsQuery.isLoading && hasData && (
        <div
          data-testid="trends-chart-container"
          className="h-72"
        >
          <ResponsiveContainer
            width="100%"
            height="100%"
            initialDimension={{ width: 600, height: 288 }}
          >
            <LineChart
              data={chartData}
              margin={{ top: 5, right: 20, bottom: 5, left: 0 }}
            >
              <CartesianGrid {...GRID_PROPS} />
              <XAxis dataKey="week" {...AXIS_PROPS} />
              <YAxis {...AXIS_PROPS} allowDecimals={false} />
              <Tooltip
                content={
                  <TossTooltip
                    formatter={(v) => `${v.toLocaleString()}${metric === "activeSubs" ? "건" : metric === "engagedUsers" ? "명" : "건"}`}
                  />
                }
              />
              <Legend
                iconType="circle"
                iconSize={8}
                wrapperStyle={{ fontSize: 12 }}
              />
              {visibleSeries.map((series, idx) => (
                <Line
                  key={series.personaId}
                  {...LINE_PROPS}
                  dataKey={series.personaId}
                  name={series.personaName}
                  stroke={chartLineColor(idx)}
                  connectNulls
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
          <p className="text-xs text-muted-foreground mt-1 text-right">{metricLabel} 기준</p>
        </div>
      )}
    </div>
  );
}
