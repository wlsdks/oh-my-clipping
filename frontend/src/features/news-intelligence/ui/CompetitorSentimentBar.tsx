import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts";
import { EmptyState } from "@/components/shared/EmptyState";
import type { CompetitorSentimentResponse } from "../../../shared/types/admin";
import { SENTIMENT_COLORS } from "@/utils/chartTheme";

const LABELS: Record<string, string> = {
  positive: "\uAE0D\uC815",
  neutral: "\uC911\uB9BD",
  negative: "\uBD80\uC815",
};

interface CompetitorSentimentBarProps {
  data: CompetitorSentimentResponse | null;
  loading?: boolean;
}

interface ChartDatum {
  name: string;
  positive: number;
  neutral: number;
  negative: number;
  total: number;
}

interface TooltipPayload {
  payload: ChartDatum;
}

function CustomTooltip({ active, payload, label }: { active?: boolean; payload?: TooltipPayload[]; label?: string }) {
  if (!active || !payload?.length) return null;
  const datum = payload[0]?.payload;
  if (!datum) return null;

  const pct = (v: number) => (datum.total > 0 ? Math.round((v / datum.total) * 100) : 0);

  return (
    <div className="bg-card rounded-xl shadow-lg border-none px-3.5 py-2.5 text-[13px] leading-relaxed">
      <div className="font-semibold mb-1">{label}</div>
      {(["positive", "neutral", "negative"] as const).map((key) => (
        <div key={key} className="flex items-center gap-1.5">
          <span
            className="w-2 h-2 rounded-full shrink-0"
            style={{ background: SENTIMENT_COLORS[key] }}
          />
          <span>{LABELS[key]}</span>
          <span className="ml-auto font-medium">
            {datum[key]}건 ({pct(datum[key])}%)
          </span>
        </div>
      ))}
    </div>
  );
}

export function CompetitorSentimentBar({ data, loading }: CompetitorSentimentBarProps) {
  const chartData: ChartDatum[] = !data?.competitors?.length
    ? []
    : data.competitors.map((c) => ({
        name: c.competitorName,
        positive: c.positive,
        neutral: c.neutral,
        negative: c.negative,
        total: c.total,
      }));

  const chartHeight = Math.max(chartData.length * 44 + 16, 120);

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>경쟁사 논조 비교</h3>
      </div>

      {loading ? (
        <p className="py-6 text-center text-muted-foreground text-[13px]">로딩 중...</p>
      ) : chartData.length === 0 ? (
        <EmptyState
          title="논조 데이터가 없어요"
          description="경쟁사 뉴스가 수집되면 여기에 표시돼요."
          className="bg-muted rounded-xl py-8 mx-4 mb-4"
        />
      ) : (
        <ResponsiveContainer width="100%" height={chartHeight}>
          <BarChart data={chartData} layout="vertical" margin={{ top: 4, right: 24, bottom: 4, left: 0 }}>
            <XAxis type="number" hide />
            <YAxis
              type="category"
              dataKey="name"
              width={100}
              tick={{ fontSize: 12, fill: "var(--text-secondary)" }}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip content={<CustomTooltip />} cursor={{ fill: "rgba(0,0,0,0.04)" }} />
            <Bar dataKey="positive" stackId="sentiment" fill={SENTIMENT_COLORS.positive} radius={[0, 0, 0, 0]} />
            <Bar dataKey="neutral" stackId="sentiment" fill={SENTIMENT_COLORS.neutral} radius={[0, 0, 0, 0]} />
            <Bar dataKey="negative" stackId="sentiment" fill={SENTIMENT_COLORS.negative} radius={[0, 6, 6, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
