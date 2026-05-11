import { useQuery } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Link } from "react-router-dom";
import { analyticsKeys } from "@/queries/analyticsKeys";
import { analyticsService } from "@/services/analyticsService";
import { KpiCard } from "./components/KpiCard";
import { DauTrendChart } from "./components/DauTrendChart";
import { WeeklyComparison } from "./components/WeeklyComparison";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";

interface OverviewTabProps {
  categoryId?: string;
  from: string;
  to: string;
  days: number;
}

export function OverviewTab({ from, to, days }: OverviewTabProps) {
  // DAU 차트 데이터
  const dauQuery = useQuery({
    queryKey: analyticsKeys.dauRange(from, to),
    queryFn: () => analyticsService.getDauByRange(from, to),
  });

  // KPI 요약: 선택된 기간
  const qualityQuery = useQuery({
    queryKey: analyticsKeys.qualitySummary(days),
    queryFn: () => analyticsService.getOpsQualitySummary(days),
  });

  // 주간 비교: 고정 7일 / 14일 기간
  const weekQuery = useQuery({
    queryKey: analyticsKeys.qualitySummary(7),
    queryFn: () => analyticsService.getOpsQualitySummary(7),
  });

  const twoWeekQuery = useQuery({
    queryKey: analyticsKeys.qualitySummary(14),
    queryFn: () => analyticsService.getOpsQualitySummary(14),
  });

  // 클릭률 요약
  const clickRateQuery = useQuery({
    queryKey: analyticsKeys.clickRate(days),
    queryFn: () => analyticsService.getClickRateSummary(days),
  });

  const isLoading =
    dauQuery.isLoading ||
    qualityQuery.isLoading ||
    weekQuery.isLoading ||
    twoWeekQuery.isLoading;

  const hasError =
    dauQuery.isError ||
    qualityQuery.isError;

  // 에러 재시도: 화면 유지 + 백그라운드 refetch
  const handleRetry = async () => {
    try {
      await Promise.all([
        dauQuery.refetch(),
        qualityQuery.refetch(),
        weekQuery.refetch(),
        twoWeekQuery.refetch(),
      ]);
    } catch {
      toast.error("데이터를 불러오지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  // 로딩 스켈레톤
  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-4">
          {[1, 2, 3, 4, 5].map((i) => (
            <KpiCard key={i} label="" value="" loading />
          ))}
        </div>
        <div className="animate-pulse h-48 rounded-xl bg-muted/30 border border-border" />
        <div className="grid grid-cols-3 gap-3">
          {[1, 2, 3].map((i) => (
            <KpiCard key={i} label="" value="" loading />
          ))}
        </div>
      </div>
    );
  }

  // 에러 화면: 유지한 채 재시도
  if (hasError) {
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
          disabled={dauQuery.isFetching || qualityQuery.isFetching}
        >
          <RefreshCw
            size={14}
            className={
              dauQuery.isFetching || qualityQuery.isFetching
                ? "animate-spin"
                : ""
            }
          />
          {dauQuery.isFetching || qualityQuery.isFetching
            ? "시도 중..."
            : "다시 시도"}
        </Button>
      </div>
    );
  }

  const dauData = dauQuery.data?.data ?? [];
  const quality = qualityQuery.data;

  // 빈 상태: 데이터가 전혀 없는 경우
  if (dauData.length === 0 && !quality) {
    return (
      <EmptyState
        title="아직 수집된 데이터가 없어요"
        description="뉴스 소스를 추가해 보세요."
        action={
          <Link
            to="/admin/sources"
            className="text-sm text-primary hover:underline"
          >
            소스 관리 바로가기
          </Link>
        }
      />
    );
  }

  // 오늘 DAU
  const todayDau = dauData.length > 0 ? dauData[dauData.length - 1].count : 0;

  // KPI 카드 데이터
  const collected = quality?.itemsCollected ?? 0;
  const sent = quality?.itemsSent ?? 0;
  const positiveRate = quality?.feedbackPositiveRate ?? 0;
  const sendSuccessRate = quality?.sendSuccessRate ?? 0;

  // 발송 성공률 상태 판정
  const sendSuccessStatus: "success" | "warning" | "danger" =
    sendSuccessRate < 0.9 ? "danger" : sendSuccessRate < 0.95 ? "warning" : "success";

  // 요약 헤드라인
  const headlineParts: string[] = [];
  if (collected > 0) headlineParts.push(`뉴스 ${collected.toLocaleString()}건 수집`);
  if (sendSuccessRate > 0) headlineParts.push(`발송 성공률 ${(sendSuccessRate * 100).toFixed(1)}%`);
  const headline = headlineParts.length > 0
    ? `최근 ${days}일 — ${headlineParts.join(", ")}`
    : null;

  return (
    <div className="space-y-6">
      {/* 요약 헤드라인 */}
      {headline && (
        <p className="text-sm font-medium text-muted-foreground">{headline}</p>
      )}

      {/* KPI: 서비스 건강도 (운영 기준) */}
      <section aria-labelledby="overview-ops-heading" className="space-y-3">
        <h2
          id="overview-ops-heading"
          className="text-sm font-semibold text-foreground"
        >
          서비스 건강도
          <span className="ml-2 text-[11px] font-normal text-muted-foreground">
            운영 기준
          </span>
        </h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
          <KpiCard
            label="수집 항목"
            value={`${collected.toLocaleString()}건`}
            subtitle={`최근 ${days}일 기준`}
            tooltip="선택 기간 내 RSS로 수집된 기사 건수(중복 제거 전)입니다. 측정 주체: 운영."
          />
          <KpiCard
            label="발송 항목"
            value={`${sent.toLocaleString()}건`}
            subtitle={`최근 ${days}일 기준`}
            tooltip="선택 기간 내 Slack으로 발송된 다이제스트 항목 수입니다. 측정 주체: 운영."
          />
          <KpiCard
            label="발송 성공률"
            value={`${(sendSuccessRate * 100).toFixed(1)}%`}
            subtitle={`최근 ${days}일 기준`}
            status={sendSuccessStatus}
            tooltip="Slack API 전송에 성공한 건수 / 전체 발송 시도. 채널 접근 실패·토큰 만료 등은 실패로 집계됩니다. 측정 주체: 운영."
          />
        </div>
      </section>

      {/* KPI: 사용자 참여 (사용자 기준) */}
      <section aria-labelledby="overview-user-heading" className="space-y-3">
        <h2
          id="overview-user-heading"
          className="text-sm font-semibold text-foreground"
        >
          사용자 참여
          <span className="ml-2 text-[11px] font-normal text-muted-foreground">
            사용자 기준
          </span>
        </h2>
        <div className="grid grid-cols-2 gap-4">
          <KpiCard
            label="DAU (오늘)"
            value={`${todayDau.toLocaleString()}명`}
            subtitle="일 활성 사용자"
            tooltip="유저 사이트에서 오늘 1개 이상 이벤트(클릭·조회 등)를 발생시킨 고유 사용자 수입니다. 측정 주체: 사용자."
          />
          <KpiCard
            label="긍정 피드백률"
            value={`${(positiveRate * 100).toFixed(1)}%`}
            subtitle="좋아요 비율"
            tooltip="👍 반응 / (👍 + 👎). 중립/무반응은 제외됩니다. 측정 주체: 사용자."
          />
        </div>
      </section>

      {/* DAU 추이 차트 */}
      <DauTrendChart data={dauData} />

      {/* 주간 비교: 고정 기간 */}
      <WeeklyComparison
        thisWeek={weekQuery.data}
        twoWeeks={twoWeekQuery.data}
      />

      {/* 클릭률 요약: 운영 × 사용자 조합 지표 */}
      {clickRateQuery.data && (
        <div className="rounded-xl border border-border bg-card p-5">
          <div className="flex items-baseline justify-between mb-3">
            <h3 className="text-sm font-semibold">기사 클릭률</h3>
            <span className="text-[11px] text-muted-foreground">
              운영 × 사용자 조합
            </span>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <KpiCard
              label="총 클릭"
              value={`${clickRateQuery.data.totalClicks.toLocaleString()}회`}
              subtitle={`최근 ${days}일 · 사용자 기준`}
            />
            <KpiCard
              label="총 발송"
              value={`${clickRateQuery.data.totalDeliveries.toLocaleString()}건`}
              subtitle={`최근 ${days}일 · 운영 기준`}
            />
            <KpiCard
              label="클릭률"
              value={`${clickRateQuery.data.clickRate.toFixed(1)}%`}
              subtitle="클릭 / 발송"
              tooltip="클릭된 기사 수(사용자 지표) / 발송된 기사 수(운영 지표). 두 측정 주체를 조합한 발송 효율 지표입니다."
            />
          </div>
        </div>
      )}
    </div>
  );
}
