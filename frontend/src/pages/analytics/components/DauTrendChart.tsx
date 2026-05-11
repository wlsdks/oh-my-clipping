import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import type { DauRow } from "@/services/analyticsService";
import {
  CHART_COLORS,
  TossTooltip,
  AreaGradientDef,
  GRID_PROPS,
  LINE_PROPS,
  AXIS_PROPS,
} from "@/utils/chartTheme";

function shortDate(dateStr: string): string {
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

interface DauTrendChartProps {
  data: DauRow[];
  loading?: boolean;
}

export function DauTrendChart({ data, loading }: DauTrendChartProps) {
  if (loading) {
    return (
      <div className="animate-pulse h-48 rounded-xl bg-muted/30 border border-border" />
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-card p-4">
        <h3 className="text-sm font-semibold mb-4">DAU 추이</h3>
        <p className="text-sm text-muted-foreground text-center py-8">
          활성 사용자 데이터가 아직 없어요
        </p>
      </div>
    );
  }

  const chartData = data.map((row) => ({
    label: shortDate(row.date),
    count: row.count,
  }));

  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <h3 className="text-sm font-semibold mb-4">DAU 추이</h3>
      <div className="h-48">
        <ResponsiveContainer width="100%" height="100%" aria-label="일일 활성 사용자 추이 차트">
          <AreaChart
            data={chartData}
            margin={{ top: 5, right: 20, bottom: 5, left: 0 }}
          >
            <CartesianGrid {...GRID_PROPS} />
            <XAxis dataKey="label" {...AXIS_PROPS} />
            <YAxis {...AXIS_PROPS} allowDecimals={false} />
            <Tooltip
              content={
                <TossTooltip formatter={(v) => `${v.toLocaleString()}명`} />
              }
            />
            <AreaGradientDef id="dauTrendGrad" color={CHART_COLORS[0]} />
            <Area
              {...LINE_PROPS}
              dataKey="count"
              name="DAU"
              stroke={CHART_COLORS[0]}
              fill="url(#dauTrendGrad)"
              connectNulls
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
