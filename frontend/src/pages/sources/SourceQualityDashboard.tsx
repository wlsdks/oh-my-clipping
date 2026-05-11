import { useState } from "react";
import { ChevronRight, Star, Heart, ShieldCheck, TrendingUp } from "lucide-react";
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from "recharts";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import type { Source } from "@/types/source";
import { getHealthLevel } from "./sourceHelpers";
import type { HealthLevel } from "./sourceHelpers";

interface SourceQualityDashboardProps {
  sources: Source[];
  articleCounts: Record<string, number>;
}

const HEALTH_COLORS: Record<string, string> = {
  healthy: "var(--status-success-text)",
  warning: "var(--status-warning-text)",
  error: "var(--status-danger-text)",
  pending: "var(--muted-foreground)",
  archived: "var(--muted-foreground)",
};

const HEALTH_LABELS: Record<string, string> = {
  healthy: "정상",
  warning: "주의",
  error: "오류",
  pending: "대기",
  archived: "비활성",
};

/** 신뢰도 점수에 따른 시맨틱 색상 클래스 */
function reliabilityColorClass(score: number): string {
  if (score >= 70) return "text-[var(--status-success-text)]";
  if (score >= 40) return "text-[var(--status-warning-text)]";
  return "text-[var(--status-danger-text)]";
}

/** 준법 검토 상태 분류 */
function classifyCompliance(sources: Source[]) {
  const now = Date.now();
  const ninetyDays = 90 * 24 * 60 * 60 * 1000;

  let reviewed = 0;
  let expired = 0;
  let never = 0;

  for (const s of sources) {
    if (!s.termsReviewedAt) {
      never += 1;
    } else {
      const reviewedAt = new Date(s.termsReviewedAt).getTime();
      if (now - reviewedAt > ninetyDays) {
        expired += 1;
      } else {
        reviewed += 1;
      }
    }
  }

  return { reviewed, expired, never };
}

/** 커스텀 도넛 툴팁 */
function DonutTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: { name: string; value: number }[];
}) {
  if (!active || !payload?.length) return null;
  const item = payload[0];
  return (
    <div className="bg-card border border-border rounded-lg px-3 py-1.5 text-xs text-foreground shadow-lg">
      {item.name} {item.value}개
    </div>
  );
}

export function SourceQualityDashboard({
  sources,
  articleCounts,
}: SourceQualityDashboardProps) {
  const [open, setOpen] = useState(false);

  if (sources.length === 0) return null;

  // 1) 평균 신뢰도 점수
  const avgReliability =
    sources.reduce((sum, s) => sum + s.reliabilityScore, 0) / sources.length;

  // 2) 헬스 분포
  const healthMap = new Map<HealthLevel, number>();
  for (const s of sources) {
    const level = getHealthLevel(s);
    healthMap.set(level, (healthMap.get(level) ?? 0) + 1);
  }
  const healthData = Array.from(healthMap.entries())
    .filter(([, count]) => count > 0)
    .map(([level, count]) => ({
      name: HEALTH_LABELS[level] ?? level,
      value: count,
      color: HEALTH_COLORS[level] ?? "var(--muted-foreground)",
    }));

  // 3) 준법 검토 상태
  const compliance = classifyCompliance(sources);

  // 4) 기사 수집 Top 5
  const top5 = [...sources]
    .map((s) => ({ name: s.name, emoji: s.emoji, count: articleCounts[s.id] ?? 0 }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 5);

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="w-full flex items-center gap-2 py-2 px-3 rounded-lg hover:bg-accent/30 transition-colors"
        >
          <ChevronRight
            size={16}
            className={`text-muted-foreground transition-transform ${open ? "rotate-90" : ""}`}
          />
          <span className="text-sm font-medium">소스 품질 현황</span>
        </button>
      </CollapsibleTrigger>

      <CollapsibleContent className="mt-3">
        <div className="rounded-xl border bg-card p-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* 평균 신뢰도 */}
            <div className="rounded-lg border p-4 space-y-1">
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Star size={14} />
                평균 신뢰도
              </div>
              <p className={`text-2xl font-bold ${reliabilityColorClass(avgReliability)}`}>
                {Math.round(avgReliability)}
                <span className="text-sm font-normal text-muted-foreground ml-0.5">/ 100</span>
              </p>
            </div>

            {/* 헬스 분포 도넛 */}
            <div className="rounded-lg border p-4 space-y-1">
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Heart size={14} />
                건강 분포
              </div>
              <div className="flex items-center gap-3">
                <div className="w-16 h-16">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={healthData}
                        dataKey="value"
                        nameKey="name"
                        innerRadius={16}
                        outerRadius={28}
                        strokeWidth={0}
                      >
                        {healthData.map((d, i) => (
                          <Cell key={i} fill={d.color} />
                        ))}
                      </Pie>
                      <Tooltip content={<DonutTooltip />} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <div className="space-y-0.5 text-xs">
                  {healthData.map((d) => (
                    <div key={d.name} className="flex items-center gap-1.5">
                      <span
                        className="inline-block w-2 h-2 rounded-full"
                        style={{ backgroundColor: d.color }}
                      />
                      <span className="text-muted-foreground">
                        {d.name} {d.value}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* 준법 검토 */}
            <div className="rounded-lg border p-4 space-y-1">
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <ShieldCheck size={14} />
                준법 검토
              </div>
              <div className="space-y-1 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">검토 완료</span>
                  <span className="text-[var(--status-success-text)] font-medium">
                    {compliance.reviewed}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">기한 만료</span>
                  <span className="text-[var(--status-warning-text)] font-medium">
                    {compliance.expired}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">미검토</span>
                  <span className="text-[var(--status-danger-text)] font-medium">
                    {compliance.never}
                  </span>
                </div>
              </div>
            </div>

            {/* Top 5 수집량 */}
            <div className="rounded-lg border p-4 space-y-1">
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <TrendingUp size={14} />
                수집량 Top 5
              </div>
              <div className="space-y-1">
                {top5.map((item, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between text-xs"
                  >
                    <span className="text-muted-foreground truncate max-w-[120px]">
                      {item.emoji && <span className="mr-1">{item.emoji}</span>}
                      {item.name}
                    </span>
                    <span className="font-medium tabular-nums">{item.count}건</span>
                  </div>
                ))}
                {top5.length === 0 && (
                  <p className="text-xs text-muted-foreground">데이터 없음</p>
                )}
              </div>
            </div>
          </div>
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}
