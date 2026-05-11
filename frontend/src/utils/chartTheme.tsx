import type { ReactNode } from "react";

/**
 * 차트 색상 팔레트 (하드코딩 폴백).
 * 가능하면 `getChartColors()`를 사용해 CSS 변수에서 런타임 값을 읽는다.
 */
export const CHART_COLORS = [
  "#2f6fec",
  "#34d399",
  "#f59e0b",
  "#ec4899",
  "#8b5cf6",
  "#06b6d4",
  "#f97316",
  "#14b8a6"
] as const;

/**
 * globals.css에 정의된 --chart-1 ~ --chart-6 CSS 변수에서 런타임 색상을 읽어 반환한다.
 * 다크모드 전환 시 CSS 변수가 자동으로 바뀌므로 테마에 맞는 색상을 얻을 수 있다.
 * CSS 변수가 없으면 CHART_COLORS 하드코딩 폴백을 사용한다.
 */
export function getChartColors(): string[] {
  if (typeof document === "undefined") return [...CHART_COLORS];
  const style = getComputedStyle(document.documentElement);
  const colors: string[] = [];
  for (let i = 1; i <= 6; i++) {
    const val = style.getPropertyValue(`--chart-${i}`).trim();
    colors.push(val || CHART_COLORS[(i - 1) % CHART_COLORS.length]);
  }
  // Pad to 8 with hardcoded fallbacks for indices beyond CSS variables
  colors.push(CHART_COLORS[6], CHART_COLORS[7]);
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
  positive: "#10b981",
  neutral: "#94a3b8",
  negative: "#f43f5e",
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
  stroke: "var(--border, #f0f0f0)",
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
  tick: { fill: "var(--color-muted-foreground, #8b95a1)" }
} as const;
