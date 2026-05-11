// frontend/src/pages/source-quality/components/SourceQualityKpiCards.tsx
import { AlertTriangle, HelpCircle, MousePointerClick, Send } from "lucide-react";
import type { SourceQualityRow, SourceQualityPeriod } from "@/types/sourceQuality";
import { cn } from "@/utils/cn";

interface Props {
  rows: SourceQualityRow[];
  period: SourceQualityPeriod;
}

const PERIOD_LABEL: Record<SourceQualityPeriod, string> = {
  "7d": "최근 7일",
  "14d": "최근 14일",
  "28d": "최근 28일",
  "90d": "최근 90일",
};

/**
 * Source quality 탭 상단 4-KPI 카드.
 * 액션 유도형 지표: "검토 필요", "신호 부족", "평균 클릭률", "총 발송".
 */
export function SourceQualityKpiCards({ rows, period }: Props) {
  // 상태별 카운트 — 1차 액션 지표
  const reviewCount = rows.filter((r) => r.statusLabel === "review").length;
  const defaultCount = rows.filter((r) => r.statusLabel === "default").length;

  // 총 발송 — 보조 지표
  const totalDelivered = rows.reduce((sum, r) => sum + r.delivered, 0);

  // 평균 클릭률 — delivered-weighted, null clickRate 제외
  const eligible = rows.filter((r) => r.clickRatePct != null && r.delivered > 0);
  const weightedDelivered = eligible.reduce((sum, r) => sum + r.delivered, 0);
  const avgClickRate =
    weightedDelivered > 0
      ? eligible.reduce((sum, r) => sum + (r.clickRatePct as number) * r.delivered, 0) /
        weightedDelivered
      : null;

  const periodLabel = PERIOD_LABEL[period];

  return (
    <section
      data-testid="source-quality-kpi-cards"
      className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3"
      aria-label="소스 품질 요약 지표"
    >
      <KpiCard
        testId="kpi-card-review"
        label="검토 필요"
        icon={AlertTriangle}
        value={String(reviewCount)}
        hint={`${periodLabel} 기준`}
        emphasize={reviewCount > 0}
      />
      <KpiCard
        testId="kpi-card-default"
        label="신호 부족"
        icon={HelpCircle}
        value={String(defaultCount)}
        hint="판정 데이터 부족"
      />
      <KpiCard
        testId="kpi-card-avg-click-rate"
        label="평균 클릭률"
        icon={MousePointerClick}
        value={avgClickRate != null ? `${avgClickRate.toFixed(1)}%` : "—"}
        hint="발송량 가중 평균"
      />
      <KpiCard
        testId="kpi-card-total-delivered"
        label="총 발송"
        icon={Send}
        value={totalDelivered.toLocaleString("ko-KR")}
        hint={periodLabel}
        secondary
      />
    </section>
  );
}

interface KpiCardProps {
  testId: string;
  label: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
  value: string;
  hint: string;
  emphasize?: boolean;
  secondary?: boolean;
}

function KpiCard({ testId, label, icon: Icon, value, hint, emphasize, secondary }: KpiCardProps) {
  return (
    <div
      data-testid={testId}
      className={cn(
        "rounded-2xl border bg-card p-4 flex flex-col gap-2 transition-shadow",
        "hover:shadow-sm",
        emphasize && "border-[var(--status-warning-text)]/40",
      )}
    >
      <div className="flex items-center gap-1.5 text-muted-foreground">
        <Icon size={14} className="shrink-0" />
        <span className="text-xs font-medium">{label}</span>
      </div>
      <div
        className={cn(
          "font-bold tabular-nums",
          secondary ? "text-xl" : "text-2xl",
          emphasize && "text-[var(--status-warning-text)]",
          !emphasize && "text-foreground",
        )}
      >
        {value}
      </div>
      <div className="text-[11px] text-muted-foreground">{hint}</div>
    </div>
  );
}
