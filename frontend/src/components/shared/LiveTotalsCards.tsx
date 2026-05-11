import type { TotalsCard } from "@/types/personaAnalytics";

interface Props {
  totals: TotalsCard;
}

/**
 * Analytics > Persona Insights 탭 상단의 4 개 카드.
 *
 * 시맨틱 색상 토큰만 사용하고 raw Tailwind 색상 클래스는 쓰지 않는다.
 * Slice 1 단계에서는 weekOverWeekDelta 가 항상 null 이라 delta 표시는
 * Slice 2 부터 활성화된다.
 */
export function LiveTotalsCards({ totals }: Props) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <MetricCard
        label="총 스타일 수"
        value={`${totals.totalStyles}개`}
        sub={`템플릿 ${totals.presetCount} · 커스텀 ${totals.customCount}`}
      />
      <MetricCard
        label="활성 구독"
        value={`${totals.activeSubscriptions}건`}
        delta={totals.weekOverWeekDelta ?? undefined}
      />
      <MetricCard
        label="템플릿 사용률"
        value={`${Math.round(totals.presetUsageRate * 100)}%`}
        sub="활성 구독 중 템플릿이 차지하는 비율"
      />
      <MetricCard
        label="커스텀 비중"
        value={`${Math.round(totals.customStyleRatio * 100)}%`}
        sub="전체 스타일 중 유저 커스텀 비율"
      />
    </div>
  );
}

interface MetricCardProps {
  label: string;
  value: string;
  sub?: string;
  delta?: number;
}

function MetricCard({ label, value, sub, delta }: MetricCardProps) {
  return (
    <div className="rounded-2xl border bg-card p-4 space-y-1 transition-all hover:-translate-y-px hover:shadow-sm">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-2xl font-bold">{value}</p>
      {delta !== undefined && delta !== 0 && (
        <p
          className={
            delta > 0
              ? "text-xs text-[var(--status-success-text)]"
              : "text-xs text-[var(--status-danger-text)]"
          }
        >
          {delta > 0 ? "▲" : "▼"} {Math.abs(delta)} 전주 대비
        </p>
      )}
      {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
    </div>
  );
}
