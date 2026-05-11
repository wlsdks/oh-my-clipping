import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from "recharts";
import type { SentimentDailyCount } from "../../../shared/types/admin";
import { SENTIMENT_COLORS } from "@/utils/chartTheme";

/* ── 상수 ── */

const SENTIMENT_LABELS: Record<string, string> = {
  positive: "\uAE0D\uC815",
  neutral: "\uC911\uB9BD",
  negative: "\uBD80\uC815",
};

/* ── Props ── */

interface SentimentTrendChartProps {
  daily: SentimentDailyCount[];
  onAreaClick?: (date: string, sentiment: string) => void;
}

/* ── 순수 헬퍼: 인사이트 자동 생성 ── */

/** 논조 추이 데이터에서 주요 인사이트를 추출한다. */
export function generateSentimentInsights(daily: SentimentDailyCount[]): string[] {
  const insights: string[] = [];

  // total이 0인 날 제외
  const valid = daily.filter((d) => d.total > 0);
  if (valid.length === 0) return insights;

  // 부정 비율 급증일 감지 (negative/total > 0.4)
  for (const d of valid) {
    const negativeRatio = d.negative / d.total;
    if (negativeRatio > 0.4) {
      const dateLabel = formatDateShort(d.date);
      const pct = Math.round(negativeRatio * 100);
      insights.push(`${dateLabel} \uBD80\uC815 \uBE44\uC728 ${pct}%\uB85C \uAE09\uC99D`);
    }
  }

  // 긍정 연속 트렌드 감지 (3일 이상 positive > 50%)
  let consecutivePositive = 0;
  let streakStart = "";
  for (const d of valid) {
    const positiveRatio = d.positive / d.total;
    if (positiveRatio > 0.5) {
      if (consecutivePositive === 0) streakStart = d.date;
      consecutivePositive++;
    } else {
      if (consecutivePositive >= 3) {
        insights.push(`${formatDateShort(streakStart)}\uBD80\uD130 ${consecutivePositive}\uC77C \uC5F0\uC18D \uAE0D\uC815 \uC6B0\uC138`);
      }
      consecutivePositive = 0;
    }
  }
  // 루프 종료 후 잔여 streak 처리
  if (consecutivePositive >= 3) {
    insights.push(`${formatDateShort(streakStart)}\uBD80\uD130 ${consecutivePositive}\uC77C \uC5F0\uC18D \uAE0D\uC815 \uC6B0\uC138`);
  }

  return insights;
}

/** "2026-03-01" -> "3/1" 형식 변환 */
function formatDateShort(dateStr: string): string {
  const parts = dateStr.split("-");
  if (parts.length < 3) return dateStr;
  const month = parseInt(parts[1], 10);
  const day = parseInt(parts[2], 10);
  return `${month}/${day}`;
}

/* ── 커스텀 툴팁 ── */

interface TooltipPayloadItem {
  dataKey: string;
  value: number;
  payload: Record<string, number | string>;
}

function TrendTooltip({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: string;
}) {
  if (!active || !payload?.length) return null;

  // payload에서 원본 데이터 꺼내기
  const row = payload[0]?.payload;
  const total = (row?.total as number) || 0;
  const sentiments = ["positive", "neutral", "negative"] as const;

  return (
    <div className="bg-card rounded-xl shadow-lg border-none px-3.5 py-2.5 text-[13px] min-w-[140px]">
      <div className="font-semibold mb-1.5 text-foreground">{label}</div>
      {sentiments.map((key) => {
        const count = (row?.[key] as number) || 0;
        const pct = total > 0 ? Math.round((count / total) * 100) : 0;
        return (
          <div key={key} className="flex items-center gap-1.5 mb-0.5">
            <span
              className="w-2 h-2 rounded-full shrink-0"
              style={{ background: SENTIMENT_COLORS[key] }}
            />
            <span className="text-foreground">
              {SENTIMENT_LABELS[key]} {count}건
            </span>
            <span className="text-muted-foreground text-xs">({pct}%)</span>
          </div>
        );
      })}
    </div>
  );
}

/* ── 컴포넌트 ── */

export function SentimentTrendChart({ daily, onAreaClick }: SentimentTrendChartProps) {
  if (!daily.length) {
    return (
      <div className="panel">
        <div className="panel-head">
          <h3>논조 추이</h3>
        </div>
        <p className="text-sm text-muted-foreground text-center py-8">
          논조 추이 데이터가 없어요
        </p>
      </div>
    );
  }

  // X축용 날짜 포맷 변환
  const chartData = daily.map((d) => ({
    ...d,
    dateLabel: formatDateShort(d.date),
  }));

  // 인사이트 자동 생성
  const insights = generateSentimentInsights(daily);

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>논조 추이</h3>
      </div>

      {/* 인사이트 배너 */}
      {insights.length > 0 && (
        <div className="px-3.5 py-2.5 bg-[var(--status-warning-bg)] rounded-[10px] mb-4 text-[13px] leading-relaxed text-foreground">
          {insights.map((text, i) => (
            <div key={i}>
              <span className="mr-1.5 text-destructive">!</span>
              {text}
            </div>
          ))}
        </div>
      )}

      {/* 범례 */}
      <div className="flex gap-4 mb-3 justify-end">
        {(["positive", "neutral", "negative"] as const).map((key) => (
          <div key={key} className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <span
              className="w-2 h-2 rounded-full"
              style={{ background: SENTIMENT_COLORS[key] }}
            />
            {SENTIMENT_LABELS[key]}
          </div>
        ))}
      </div>

      {/* 100% 스택 에어리어 차트 */}
      <ResponsiveContainer width="100%" height={260}>
        <AreaChart
          data={chartData}
          stackOffset="expand"
          onClick={(e) => {
            if (onAreaClick && e?.activeLabel) {
              // 클릭된 날짜의 원본 date 찾기
              const found = chartData.find((d) => d.dateLabel === e.activeLabel);
              if (found) {
                onAreaClick(found.date, "ALL");
              }
            }
          }}
        >
          <CartesianGrid strokeDasharray="4 4" stroke="var(--border-default, #e2e2f0)" vertical={false} />
          <XAxis
            dataKey="dateLabel"
            tick={{ fontSize: 11, fill: "var(--text-tertiary)" }}
            axisLine={{ stroke: "var(--border-default, #e2e2f0)" }}
            tickLine={false}
          />
          <YAxis
            tickFormatter={(v: number) => `${Math.round(v * 100)}%`}
            tick={{ fontSize: 11, fill: "var(--text-tertiary)" }}
            width={42}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip content={<TrendTooltip />} />
          <Area
            type="monotone"
            dataKey="positive"
            stackId="1"
            stroke={SENTIMENT_COLORS.positive}
            fill={SENTIMENT_COLORS.positive}
            fillOpacity={0.7}
            cursor="pointer"
          />
          <Area
            type="monotone"
            dataKey="neutral"
            stackId="1"
            stroke={SENTIMENT_COLORS.neutral}
            fill={SENTIMENT_COLORS.neutral}
            fillOpacity={0.5}
            cursor="pointer"
          />
          <Area
            type="monotone"
            dataKey="negative"
            stackId="1"
            stroke={SENTIMENT_COLORS.negative}
            fill={SENTIMENT_COLORS.negative}
            fillOpacity={0.7}
            cursor="pointer"
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
