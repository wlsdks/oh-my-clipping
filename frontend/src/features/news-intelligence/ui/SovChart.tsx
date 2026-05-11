import { Bar, XAxis, YAxis, ResponsiveContainer, Tooltip, BarChart, Cell } from "recharts";
import type { BarRectangleItem } from "recharts";
import type { SovResponse } from "../../../shared/types/admin";
import { CHART_COLORS, TossTooltip } from "@/utils/chartTheme";

interface SovChartProps {
  data: SovResponse | null;
  loading?: boolean;
  onBarClick?: (competitorId: string) => void;
}

interface ChartDatum {
  label: string;
  count: number;
  share: number;
  competitorId: string | null;
}

export function SovChart({ data, loading, onBarClick }: SovChartProps) {
  const chartData: ChartDatum[] = !data?.shares?.length
    ? []
    : data.shares.map((s) => ({
        label: `${s.name} (${s.count}건)`,
        count: s.count,
        share: s.share,
        competitorId: s.competitorId,
      }));

  const chartHeight = chartData.length * 44 + 16;

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>기사 점유율</h3>
      </div>

      {loading ? (
        <p className="py-6 text-center text-muted-foreground text-[13px]">로딩 중...</p>
      ) : chartData.length === 0 ? (
        <p className="py-6 text-center text-muted-foreground text-[13px]">
          경쟁사를 등록해 주세요
        </p>
      ) : (
        <ResponsiveContainer width="100%" height={chartHeight} aria-label="경쟁사 점유율 차트">
          <BarChart data={chartData} layout="vertical" margin={{ top: 4, right: 24, bottom: 4, left: 0 }}>
            <XAxis type="number" hide />
            <YAxis
              type="category"
              dataKey="label"
              width={130}
              tick={{ fontSize: 12, fill: "var(--text-secondary)" }}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip
              content={
                <TossTooltip
                  formatter={(v) => {
                    const d = chartData.find((c) => c.count === v);
                    if (!d) return `${v.toLocaleString()}건`;
                    return `${Math.round(d.share * 100)}% (${d.count}건)`;
                  }}
                />
              }
              cursor={{ fill: "rgba(0,0,0,0.04)" }}
            />
            <Bar
              dataKey="count"
              radius={[0, 6, 6, 0]}
              cursor={onBarClick ? "pointer" : undefined}
              onClick={(entry: BarRectangleItem & { competitorId?: string | null }) => {
                if (onBarClick && entry?.competitorId) {
                  onBarClick(entry.competitorId);
                }
              }}
            >
              {chartData.map((_, i) => (
                <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
