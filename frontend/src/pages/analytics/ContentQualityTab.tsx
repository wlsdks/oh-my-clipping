import { useQuery } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { analyticsKeys } from "@/queries/analyticsKeys";
import { analyticsService } from "@/services/analyticsService";
import { KpiCard } from "./components/KpiCard";
import { DailyKpiTable } from "./components/DailyKpiTable";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";

interface ContentQualityTabProps {
  categoryId?: string;
  from: string;
  to: string;
  days: number;
}

export function ContentQualityTab({
  categoryId,
  from,
  to,
}: ContentQualityTabProps) {
  const queryParams = { categoryId, from, to };

  const { data, isLoading, isError, isFetching, refetch } = useQuery({
    queryKey: analyticsKeys.dailyKpi(queryParams),
    queryFn: () => analyticsService.getDailyOperationalKpi(queryParams),
  });

  // 에러 재시도: 화면 유지 + 백그라운드 refetch
  const handleRetry = async () => {
    try {
      await refetch();
    } catch {
      toast.error("데이터를 불러오지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  // 로딩 스켈레톤
  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <KpiCard key={i} label="" value="" loading />
          ))}
        </div>
        <DailyKpiTable rows={[]} loading />
      </div>
    );
  }

  // 에러 화면
  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <p className="text-lg font-medium text-foreground">
          데이터를 불러오지 못했어요
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          네트워크 상태를 확인하고 다시 시도해 주세요.
        </p>
        <Button
          variant="outline"
          size="sm"
          className="mt-4 gap-1.5"
          onClick={handleRetry}
          disabled={isFetching}
        >
          <RefreshCw size={14} className={isFetching ? "animate-spin" : ""} />
          {isFetching ? "시도 중..." : "다시 시도"}
        </Button>
      </div>
    );
  }

  const rows = data ?? [];

  // 빈 상태
  if (rows.length === 0) {
    return (
      <EmptyState
        title="선택한 기간에 데이터가 없어요"
        description="다른 기간이나 카테고리를 선택해 보세요."
      />
    );
  }

  // 평균값 계산
  const avgNoise =
    rows.reduce((sum, r) => sum + r.noiseRate, 0) / rows.length;
  const avgDuplicate =
    rows.reduce((sum, r) => sum + r.duplicateRate, 0) / rows.length;
  const avgLeadTime =
    rows.reduce((sum, r) => sum + r.reviewLeadTimeHours, 0) / rows.length;

  return (
    <div className="space-y-6">
      {/* KPI 요약 카드 */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiCard
          label="평균 노이즈율"
          value={`${(avgNoise * 100).toFixed(1)}%`}
          subtitle="불필요 기사 비율"
          tooltip="AI가 '가치 낮음/광고/중복'으로 필터링해 발송 후보에서 제외한 기사 비율입니다."
        />
        <KpiCard
          label="평균 중복율"
          value={`${(avgDuplicate * 100).toFixed(1)}%`}
          subtitle="중복 기사 비율"
          tooltip="같은 카테고리에서 유사/중복으로 판정된 기사의 비율입니다."
        />
        <KpiCard
          label="검토 리드타임"
          value={`${avgLeadTime.toFixed(1)}h`}
          subtitle="수집~검토 평균 소요"
          tooltip="파이프라인 시작부터 AI 요약 및 발송 결정이 완료되기까지 평균 소요 시간입니다. 사람 검토는 별도입니다."
        />
      </div>

      {/* 일별 상세 테이블 */}
      <div>
        <h3 className="text-sm font-semibold mb-3">일별 상세</h3>
        <DailyKpiTable rows={rows} />
      </div>
    </div>
  );
}
