import type { ReactNode } from "react";

/** 차트 색상 팔레트. globals.css의 --chart-* 토큰을 단일 소스로 사용한다. */
export const CHART_COLORS = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
  "var(--chart-6)",
  "var(--chart-7)",
  "var(--chart-8)"
] as const;

/**
 * globals.css에 정의된 --chart-1 ~ --chart-8 CSS 변수에서 런타임 색상을 읽어 반환한다.
 * 다크모드 전환 시 CSS 변수가 자동으로 바뀌므로 테마에 맞는 색상을 얻을 수 있다.
 * CSS 변수가 없으면 var() 토큰 문자열을 반환해 브라우저 해석에 맡긴다.
 */
export function getChartColors(): string[] {
  if (typeof document === "undefined") return [...CHART_COLORS];
  const style = getComputedStyle(document.documentElement);
  const colors: string[] = [];
  for (let i = 1; i <= CHART_COLORS.length; i++) {
    const val = style.getPropertyValue(`--chart-${i}`).trim();
    colors.push(val || CHART_COLORS[(i - 1) % CHART_COLORS.length]);
  }
  return colors;
}

export function chartColor(index: number): string {
  const colors = getChartColors();
  return colors[index % colors.length];
}

interface TooltipEntry {
  color?: string;
  name?: string;
  value?: number | string | null;
}

interface TossTooltipProps {
  active?: boolean;
  payload?: TooltipEntry[];
  label?: string | number;
  formatter?: (value: number) => ReactNode;
}

export function TossTooltip({ active, payload, label, formatter }: TossTooltipProps) {
  if (!active || !payload?.length) return null;

  return (
    <div className="rounded-xl border bg-card px-3 py-2 shadow-lg text-sm">
      <p className="font-medium text-foreground mb-1">{label}</p>
      {payload.map((entry, i) => (
        <div key={i} className="flex items-center gap-2 text-xs">
          <span className="inline-block w-2 h-2 rounded-full shrink-0" style={{ background: entry.color }} />
          <span className="text-muted-foreground">{entry.name}</span>
          <span className="font-medium ml-auto">
            {formatter ? formatter(Number(entry.value ?? 0)) : Number(entry.value ?? 0).toLocaleString()}
          </span>
        </div>
      ))}
    </div>
  );
}

export function AreaGradientDef({ id, color }: { id: string; color: string }) {
  return (
    <defs>
      <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stopColor={color} stopOpacity={0.15} />
        <stop offset="100%" stopColor={color} stopOpacity={0.01} />
      </linearGradient>
    </defs>
  );
}

/** Sentiment-specific colors */
export const SENTIMENT_COLORS = {
  positive: "var(--status-success-text)",
  neutral: "var(--status-neutral-text)",
  negative: "var(--status-danger-text)",
} as const;

/** Tooltip styling shared across all chart components (for recharts inline style) */
export const CHART_TOOLTIP_STYLE = {
  fontSize: 13,
  borderRadius: 12,
  border: "none",
  boxShadow: "0 4px 16px rgba(0,0,0,0.12)",
  padding: "10px 14px",
} as const;

export const GRID_PROPS = {
  strokeDasharray: "3 3",
  stroke: "var(--color-border)",
  vertical: false
} as const;

export const LINE_PROPS = {
  strokeWidth: 2.5,
  type: "monotone" as const,
  dot: false,
  activeDot: { r: 5, strokeWidth: 0 }
} as const;

export const AXIS_PROPS = {
  fontSize: 12,
  tickLine: false,
  axisLine: false,
  tick: { fill: "var(--color-muted-foreground)" }
} as const;
