import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { reviewService } from "@/services/reviewService";
import { reviewKeys } from "@/queries/reviewKeys";
import { Button } from "@/components/ui/button";

export function ReviewAccuracySection() {
  const [period, setPeriod] = useState<"7d" | "30d">("7d");
  const { data: stats, isLoading } = useQuery({
    queryKey: reviewKeys.stats(period),
    queryFn: () => reviewService.getStats(period),
  });

  if (isLoading) {
    return (
      <div className="rounded-xl border bg-card p-6">
        <div className="h-6 w-40 animate-pulse rounded bg-muted mb-4" />
        <div className="grid grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-20 animate-pulse rounded-lg bg-muted" />
          ))}
        </div>
      </div>
    );
  }

  if (!stats || stats.totalReviewed === 0) {
    return (
      <div className="rounded-xl border bg-card p-6">
        <h3 className="text-base font-semibold mb-2">AI 검토 정확도</h3>
        <p className="text-sm text-muted-foreground">아직 검토 데이터가 없습니다.</p>
      </div>
    );
  }

  const delta = stats.previousPeriodAccuracy != null
    ? stats.overallAccuracy - stats.previousPeriodAccuracy
    : null;

  return (
    <div className="rounded-xl border bg-card p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold">AI 검토 정확도</h3>
        <div className="flex gap-1">
          <Button size="sm" variant={period === "7d" ? "default" : "ghost"} onClick={() => setPeriod("7d")}>7일</Button>
          <Button size="sm" variant={period === "30d" ? "default" : "ghost"} onClick={() => setPeriod("30d")}>30일</Button>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <MetricCard label="전체 일치율" value={stats.overallAccuracy} delta={delta} />
        <MetricCard label="INCLUDE 정확도" value={stats.includeAccuracy} />
        <MetricCard label="EXCLUDE 정확도" value={stats.excludeAccuracy} />
      </div>

      {stats.categoryBreakdown.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-muted-foreground">카테고리별 일치율</h4>
          {stats.categoryBreakdown.map((cat) => (
            <div key={cat.categoryId} className="flex items-center gap-3">
              <span className="text-xs w-24 truncate">{cat.categoryName}</span>
              <div className="flex-1 h-2 rounded-full bg-muted overflow-hidden">
                <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${cat.accuracy * 100}%` }} />
              </div>
              <span className="text-xs text-muted-foreground w-10 text-right">{(cat.accuracy * 100).toFixed(0)}%</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function MetricCard({ label, value, delta }: { label: string; value: number; delta?: number | null }) {
  return (
    <div className="rounded-lg border p-4 text-center">
      <div className="text-2xl font-bold">{(value * 100).toFixed(0)}%</div>
      {delta != null && (
        <div className={`text-xs ${delta >= 0 ? "text-[var(--status-success-text)]" : "text-[var(--status-danger-text)]"}`}>
          {delta >= 0 ? "+" : ""}{(delta * 100).toFixed(1)}%
        </div>
      )}
      <div className="text-xs text-muted-foreground mt-1">{label}</div>
    </div>
  );
}
