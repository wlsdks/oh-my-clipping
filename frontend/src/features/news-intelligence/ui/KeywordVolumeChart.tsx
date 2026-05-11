import { useState } from "react";
import { Line, XAxis, YAxis, ResponsiveContainer, Tooltip, CartesianGrid, LineChart } from "recharts";
import { cn } from "@/utils/cn";
import type { KeywordTrendItem } from "../../../shared/types/admin";
import { CHART_COLORS, CHART_TOOLTIP_STYLE } from "@/utils/chartTheme";

const DAY_NAMES = ["\uC77C", "\uC6D4", "\uD654", "\uC218", "\uBAA9", "\uAE08", "\uD1A0"];

interface KeywordTrendChartProps {
  keywords: KeywordTrendItem[];
  height?: number;
  defaultVisibleCount?: number;
  showToggle?: boolean;
}

function formatTooltipDate(dateStr: string): string {
  const now = new Date();
  const year = now.getFullYear();
  const parts = dateStr.split(/[-/]/);
  if (parts.length === 2) {
    const month = parseInt(parts[0], 10) - 1;
    const day = parseInt(parts[1], 10);
    const d = new Date(year, month, day);
    if (!isNaN(d.getTime())) {
      return `${parts[0]}/${parts[1]} (${DAY_NAMES[d.getDay()]})`;
    }
  }
  return dateStr;
}

function changeLabel(rate: number): { text: string; className: string } {
  const abs = Math.abs(rate);
  if (rate > 0) {
    if (abs >= 1.0) return { text: "\uAE09\uC0C1\uC2B9", className: "text-destructive" };
    if (abs >= 0.5) return { text: "\uC0C1\uC2B9", className: "text-destructive" };
    if (abs >= 0.2) return { text: "\uC18C\uD3ED\u2191", className: "text-[var(--status-warning-text)]" };
    return { text: "\uBCC0\uB3D9 \uC5C6\uC74C", className: "text-muted-foreground" };
  }
  if (rate < 0) {
    if (abs >= 0.5) return { text: "\uD558\uB77D", className: "text-[var(--status-neutral-text)]" };
    if (abs >= 0.1) return { text: "\uC18C\uD3ED\u2193", className: "text-[var(--status-neutral-text)]" };
    return { text: "\uBCC0\uB3D9 \uC5C6\uC74C", className: "text-muted-foreground" };
  }
  return { text: "\uBCC0\uB3D9 \uC5C6\uC74C", className: "text-muted-foreground" };
}

export function KeywordTrendChart({
  keywords,
  height = 300,
  defaultVisibleCount = 3,
  showToggle = true,
}: KeywordTrendChartProps) {
  const maxCount = Math.min(keywords.length, 5);
  const [expanded, setExpanded] = useState(false);
  const visibleCount = expanded ? maxCount : Math.min(defaultVisibleCount, maxCount);

  const [visible, setVisible] = useState<Set<string>>(
    () => new Set(keywords.slice(0, defaultVisibleCount).map((k) => k.keyword)),
  );

  function toggle(keyword: string) {
    setVisible((prev) => {
      const next = new Set(prev);
      if (next.has(keyword)) {
        if (next.size > 1) next.delete(keyword);
      } else {
        next.add(keyword);
      }
      return next;
    });
  }

  const dateMap = new Map<string, Record<string, number>>();
  for (const kw of keywords.slice(0, maxCount)) {
    for (const dc of kw.dailyCounts) {
      const entry = dateMap.get(dc.date) ?? {};
      entry[kw.keyword] = dc.count;
      dateMap.set(dc.date, entry);
    }
  }
  const chartData = Array.from(dateMap.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, counts]) => ({ date: date.slice(5), ...counts }));

  const displayedKeywords = keywords.slice(0, visibleCount);

  // 1위 키워드 인사이트 요약
  const topKw = keywords[0];
  const topChange = topKw ? changeLabel(topKw.changeRate) : null;

  return (
    <div>
      {/* 인사이트 요약 */}
      {topKw && (
        <div className="px-4 py-3 bg-primary/5 rounded-[10px] mb-4 text-[13px] leading-relaxed text-foreground">
          이번 달 가장 핫한 키워드는 <strong>{topKw.keyword}</strong>
          <span className="text-muted-foreground"> ({topKw.totalCount}건)</span>
          {topKw.changeRate !== 0 && topChange && (
            <span className={cn("ml-1 font-semibold", topChange.className)}>{topChange.text}</span>
          )}
          {keywords.length > 1 && (
            <span className="text-muted-foreground">
              {" \u00B7 "}
              <strong>{keywords[1].keyword}</strong> {keywords[1].totalCount}건
              {keywords.length > 2 && (
                <>
                  , <strong>{keywords[2].keyword}</strong> {keywords[2].totalCount}건도 주목
                </>
              )}
            </span>
          )}
        </div>
      )}

      {/* 키워드 필터 + 변화율 뱃지 */}
      <div className="flex flex-wrap items-center gap-2 mb-4">
        {displayedKeywords.map((kw, i) => {
          const color = CHART_COLORS[i % CHART_COLORS.length];
          const isVisible = visible.has(kw.keyword);
          const change = changeLabel(kw.changeRate);
          return (
            <button
              key={kw.keyword}
              type="button"
              onClick={() => toggle(kw.keyword)}
              aria-label={`키워드 ${kw.keyword} ${visible.has(kw.keyword) ? "숨기기" : "보이기"}`}
              className={cn(
                "flex items-center gap-1.5 px-3.5 py-1 rounded-full border-none text-xs cursor-pointer transition-all duration-200",
                isVisible ? "text-white font-semibold" : "bg-secondary text-muted-foreground"
              )}
              style={isVisible ? { background: color } : undefined}
            >
              {kw.keyword}
              <span
                className={cn(
                  "text-[10px] font-medium",
                  isVisible ? "opacity-85 text-white" : change.className
                )}
              >
                {change.text}
              </span>
            </button>
          );
        })}
        {showToggle && maxCount > defaultVisibleCount && (
          <button
            type="button"
            aria-label={expanded ? "키워드 목록 접기" : "키워드 목록 더 보기"}
            onClick={() => {
              if (!expanded) {
                const newVisible = new Set(visible);
                keywords.slice(defaultVisibleCount, maxCount).forEach((kw) => newVisible.add(kw.keyword));
                setVisible(newVisible);
              }
              setExpanded(!expanded);
            }}
            className="px-3 py-1 rounded-full border border-border bg-transparent text-xs text-primary cursor-pointer transition-all duration-150 hover:bg-primary/5"
          >
            {expanded ? "\uC811\uAE30" : "\uB354 \uBCF4\uAE30"}
          </button>
        )}
      </div>

      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={chartData}>
          <CartesianGrid strokeDasharray="4 4" stroke="var(--border-default, #e2e2f0)" vertical={false} />
          <XAxis
            dataKey="date"
            tick={{ fontSize: 11, fill: "var(--text-tertiary)" }}
            axisLine={{ stroke: "var(--border-default, #e2e2f0)" }}
            tickLine={false}
          />
          <YAxis
            tick={{ fontSize: 11, fill: "var(--text-tertiary)" }}
            width={36}
            axisLine={false}
            tickLine={false}
            allowDecimals={false}
            label={{ value: "\uAC74\uC218", position: "top", offset: 12, style: { fontSize: 11, fill: "var(--text-tertiary)" } }}
          />
          <Tooltip
            contentStyle={CHART_TOOLTIP_STYLE}
            labelFormatter={(label) => formatTooltipDate(String(label))}
            formatter={(value, name) => [`${Number(value)}건`, String(name)]}
          />
          {displayedKeywords.map((kw, i) => {
            if (!visible.has(kw.keyword)) return null;
            const color = CHART_COLORS[i % CHART_COLORS.length];
            return (
              <Line
                key={kw.keyword}
                type="linear"
                dataKey={kw.keyword}
                stroke={color}
                strokeWidth={2.5}
                dot={{ r: 4, fill: color, stroke: "#fff", strokeWidth: 2 }}
                activeDot={{ r: 6, fill: color, stroke: "#fff", strokeWidth: 2 }}
                connectNulls
              />
            );
          })}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
