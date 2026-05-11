import { TrendingUp, TrendingDown } from "lucide-react";
import type { QualitySummary } from "@/types/insight";
import { KpiCard } from "./KpiCard";
import { pct, trendDir } from "./trendUtils";
import { InfoTooltip } from "@/components/shared/InfoTooltip";

interface WeeklyComparisonProps {
  thisWeek?: QualitySummary;
  twoWeeks?: QualitySummary;
  loading?: boolean;
}

export function WeeklyComparison({ thisWeek, twoWeeks, loading }: WeeklyComparisonProps) {
  if (loading) {
    return (
      <div className="space-y-3">
        <h3 className="text-sm font-semibold">
          운영 주간 추이
          <span className="ml-2 text-[11px] font-normal text-muted-foreground">
            운영 기준
          </span>
        </h3>
        <div className="grid grid-cols-3 gap-3">
          {[1, 2, 3].map((i) => (
            <KpiCard key={i} label="" value="" loading />
          ))}
        </div>
      </div>
    );
  }

  if (!thisWeek || !twoWeeks) return null;

  // 지난 주 = 2주 누적 - 이번 주
  const lastWeek = {
    collected: twoWeeks.itemsCollected - thisWeek.itemsCollected,
    summarized: twoWeeks.itemsSummarized - thisWeek.itemsSummarized,
    sent: twoWeeks.itemsSent - thisWeek.itemsSent
  };

  const cards = [
    {
      label: "수집 기사",
      thisVal: thisWeek.itemsCollected,
      lastVal: lastWeek.collected,
      tooltip: "이번 주 수집된 기사 수와 지난 주 대비 변화입니다."
    },
    {
      label: "AI 요약",
      thisVal: thisWeek.itemsSummarized,
      lastVal: lastWeek.summarized,
      tooltip: "이번 주 AI가 생성한 요약 수와 지난 주 대비 변화입니다."
    },
    {
      label: "발송 완료",
      thisVal: thisWeek.itemsSent,
      lastVal: lastWeek.sent,
      tooltip: "이번 주 Slack으로 발송된 다이제스트 건수와 지난 주 대비 변화입니다."
    }
  ];

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold">
          운영 주간 추이
          <span className="ml-2 text-[11px] font-normal text-muted-foreground">
            운영 기준
          </span>
        </h3>
        <span className="text-[11px] text-muted-foreground">이번 주 vs 지난 주</span>
      </div>
      <div className="grid grid-cols-3 gap-3">
        {cards.map((c) => {
          const p = pct(c.thisVal, c.lastVal);
          const dir = trendDir(c.thisVal, c.lastVal);
          const trendValue = dir === "neutral" ? "0%" : `${dir === "up" ? "+" : ""}${p}%`;

          return (
            <div key={c.label} className="rounded-xl border border-border bg-card p-4 space-y-3">
              <div className="flex items-center gap-1">
                <span className="text-xs text-muted-foreground font-medium">{c.label}</span>
                <InfoTooltip content={c.tooltip} ariaLabel={`${c.label} 설명`} />
              </div>
              <div className="flex items-baseline gap-3">
                <div>
                  <p className="text-[11px] text-muted-foreground mb-0.5">이번 주</p>
                  <p className="text-xl font-bold tabular-nums tracking-tight">
                    {c.thisVal.toLocaleString()}
                    <span className="text-xs font-normal text-muted-foreground ml-0.5">건</span>
                  </p>
                </div>
                <div>
                  <p className="text-[11px] text-muted-foreground mb-0.5">지난 주</p>
                  <p className="text-xl font-bold tabular-nums tracking-tight text-muted-foreground">
                    {c.lastVal.toLocaleString()}
                    <span className="text-xs font-normal ml-0.5">건</span>
                  </p>
                </div>
              </div>
              {dir !== "neutral" && (
                <span
                  className={`inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded-md text-[11px] font-semibold tabular-nums ${
                    dir === "up" ? "text-foreground/70" : "text-muted-foreground"
                  }`}
                >
                  {dir === "up" ? <TrendingUp size={11} /> : <TrendingDown size={11} />}
                  {trendValue}
                </span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
