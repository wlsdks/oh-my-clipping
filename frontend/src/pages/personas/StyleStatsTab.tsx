import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { personaAnalyticsService } from "@/services/personaAnalyticsService";
import { personaAnalyticsKeys } from "@/queries/personaAnalyticsKeys";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";

/**
 * 페르소나 관리 페이지의 "스타일 통계" 탭 축소판.
 *
 * 이 페이지는 페르소나 CRUD 에 집중하고, 차트/포트폴리오/최근 커스텀 같은
 * 상세 분석은 Analytics 페이지의 Persona Insights 탭으로 이관했다.
 * 여기서는 스타일 구성 관련 핵심 카드 3개 + Analytics 링크만 노출한다.
 * 활성 구독 수는 구독 관리 페이지에서 확인한다.
 */
export function StyleStatsTab() {
  const { data, isLoading } = useQuery({
    queryKey: personaAnalyticsKeys.live(),
    queryFn: () => personaAnalyticsService.getLive(),
  });

  if (isLoading) {
    return (
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 animate-pulse">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="rounded-2xl border bg-card h-24" />
        ))}
      </div>
    );
  }
  if (!data) return <EmptyState title="아직 통계 데이터가 없어요" description="집계된 통계가 없어요" />;

  const { totals } = data;

  return (
    <div className="space-y-4">
      {/* 스타일 구성 카드 — 활성 구독은 구독 관리 페이지에서 확인 */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        <StyleMetricCard
          label="총 스타일 수"
          value={`${totals.totalStyles}개`}
          sub={`템플릿 ${totals.presetCount} · 커스텀 ${totals.customCount}`}
        />
        <StyleMetricCard
          label="템플릿 사용률"
          value={`${Math.round(totals.presetUsageRate * 100)}%`}
          sub="활성 구독 중 템플릿이 차지하는 비율"
        />
        <StyleMetricCard
          label="커스텀 비중"
          value={`${Math.round(totals.customStyleRatio * 100)}%`}
          sub="전체 스타일 중 유저 커스텀 비율"
        />
      </div>
      <div className="rounded-2xl border bg-muted/30 p-4 flex items-center justify-between gap-3 flex-wrap">
        <div>
          <p className="text-sm font-medium">더 자세한 분석이 필요하세요?</p>
          <p className="text-xs text-muted-foreground mt-0.5">
            Analytics 대시보드에서 템플릿 포트폴리오와 최근 커스텀 스타일을 확인하세요.
          </p>
        </div>
        <Link to="/admin/analytics?tab=personas">
          <Button size="sm">
            Analytics 열기
            <ArrowRight className="size-4 ml-1" />
          </Button>
        </Link>
      </div>
    </div>
  );
}

interface StyleMetricCardProps {
  label: string;
  value: string;
  sub?: string;
}

function StyleMetricCard({ label, value, sub }: StyleMetricCardProps) {
  return (
    <div className="rounded-2xl border bg-card p-4 space-y-1 transition-all hover:-translate-y-px hover:shadow-sm">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-2xl font-bold">{value}</p>
      {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
    </div>
  );
}
