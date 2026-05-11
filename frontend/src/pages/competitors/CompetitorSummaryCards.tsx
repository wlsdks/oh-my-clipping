import type { Competitor } from "@/types/competitor";

interface CompetitorSummaryCardsProps {
  competitors: Competitor[];
}

interface MetricCardProps {
  label: string;
  count: number;
}

function MetricCard({ label, count }: MetricCardProps) {
  return (
    <div className="rounded-xl border bg-card p-4 space-y-1">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="text-2xl font-bold tabular-nums">{count}개</p>
    </div>
  );
}

export function CompetitorSummaryCards({ competitors }: CompetitorSummaryCardsProps) {
  const directCount = competitors.filter((c) => c.tier === "DIRECT").length;
  const adjacentCount = competitors.filter((c) => c.tier === "ADJACENT").length;
  const globalCount = competitors.filter((c) => c.tier === "GLOBAL").length;

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <MetricCard label="전체" count={competitors.length} />
      <MetricCard label="직접경쟁" count={directCount} />
      <MetricCard label="인접" count={adjacentCount} />
      <MetricCard label="글로벌" count={globalCount} />
    </div>
  );
}
