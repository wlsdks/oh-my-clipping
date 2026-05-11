import { useQuery } from "@tanstack/react-query";
import { Calendar } from "lucide-react";
import { Link } from "react-router-dom";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CostSparkline } from "@/components/shared/CostSparkline";
import { costKeys } from "@/queries/costKeys";
import { costService } from "@/services/costService";

import { useOpsMetricsData } from "../hooks/useOpsMetricsData";
import { computeSparklineData } from "../model/dashboardState";
import { UserEngagementCard } from "./UserEngagementCard";

function formatRelative(nextRunIso: string, now: Date): string {
  const nextRun = new Date(nextRunIso);
  const diffMinutes = Math.max(0, Math.floor((nextRun.getTime() - now.getTime()) / 60_000));
  if (diffMinutes < 60) return `${diffMinutes}분 후`;
  const hours = Math.floor(diffMinutes / 60);
  const mins = diffMinutes % 60;
  return mins === 0 ? `${hours}시간 후` : `${hours}시간 ${mins}분 후`;
}

export function OpsMetricsSection() {
  const now = new Date();
  const {
    forecast,
    pipelineSummary,
    deliverySummary,
    engagement,
    opsSummary,
    isLoading,
  } = useOpsMetricsData();

  // 비용 7일 범위 계산
  const to = now.toISOString().split("T")[0];
  const fromDate = new Date(now);
  fromDate.setDate(fromDate.getDate() - 6);
  const from = fromDate.toISOString().split("T")[0];

  const costQuery = useQuery({
    queryKey: costKeys.overview({ from, to }),
    queryFn: () => costService.getOverview(from, to),
    staleTime: 300_000,
  });

  if (isLoading) {
    return (
      <section data-testid="ops-metrics-section" className="space-y-3">
        <div className="h-16 rounded-lg bg-muted animate-pulse" />
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="h-32 rounded-lg bg-muted animate-pulse" />
          ))}
        </div>
      </section>
    );
  }

  // 파이프라인 통계 계산 — status 카운트는 서버 pre-aggregated 값 사용 (정확)
  const pipelineFailed = opsSummary?.pipeline.failed ?? 0;
  const pipelineTotal = opsSummary?.pipeline.total ?? 0;
  const pipelineSuccessRate =
    pipelineTotal > 0
      ? Math.round(((pipelineTotal - pipelineFailed) / pipelineTotal) * 100)
      : 100;

  // avgDurationMs — 최근 100건 샘플 기반 추정 (서버 집계 대상 아님, 추후 개선 가능)
  const pipelineRunSample = pipelineSummary?.content ?? [];
  const avgDurationMs =
    pipelineRunSample.length > 0
      ? Math.round(
          pipelineRunSample.reduce((a, r) => a + (r.durationMs ?? 0), 0) /
            pipelineRunSample.length,
        )
      : 0;
  // total 이 sample 크기보다 크면 평균·채널 수치는 완전 집계가 아니라 샘플 추정임을
  // 사용자가 오해하지 않도록 "(샘플)" 마커를 붙인다. 같거나 작으면 샘플 = 전체.
  const pipelineAvgIsSampled = pipelineTotal > pipelineRunSample.length;

  // 다이제스트 통계 계산 — status 카운트는 서버 pre-aggregated 값 사용
  const deliveryFailed = opsSummary?.delivery.failed ?? 0;
  const deliveryTotal = opsSummary?.delivery.total ?? 0;
  const deliverySuccessRate =
    deliveryTotal > 0
      ? Math.round(((deliveryTotal - deliveryFailed) / deliveryTotal) * 100)
      : 100;

  // uniqueChannels — 최근 100건 샘플 기반 추정 (서버 집계 대상 아님)
  const deliveryLogsSample = deliverySummary?.content ?? [];
  const uniqueChannels = new Set(deliveryLogsSample.map((l) => l.channelId)).size;
  const deliveryChannelIsSampled = deliveryTotal > deliveryLogsSample.length;

  // 비용 스파크라인 계산
  const costRows = costQuery.data?.dailyBreakdown ?? [];
  const sparklineData = computeSparklineData(costRows);
  const totalCostUsd = costQuery.data?.totalCostUsd ?? 0;
  const budgetUsd = costQuery.data?.budgetUsd ?? null;
  const usagePct =
    budgetUsd != null && budgetUsd > 0
      ? Math.round((totalCostUsd / budgetUsd) * 100)
      : costQuery.data?.budgetUsedPercent != null
        ? Math.round(costQuery.data.budgetUsedPercent)
        : null;

  return (
    <section data-testid="ops-metrics-section" className="space-y-3">
      {/* Forecast 배너 */}
      <div className="rounded-lg border bg-muted/30 p-3 flex items-center gap-2 text-sm flex-wrap">
        <Calendar className="h-4 w-4 text-muted-foreground" />
        <span className="font-medium">오늘 예정</span>
        <span>· 파이프라인 {forecast?.expectedRunCount ?? 0}회</span>
        <span>· 다이제스트 {forecast?.expectedDigestCount ?? 0}건</span>
        {forecast?.nextRunAtKst && (
          <span className="text-muted-foreground">
            · 다음 파이프라인{" "}
            {new Date(forecast.nextRunAtKst).toLocaleTimeString("ko-KR", {
              hour: "2-digit",
              minute: "2-digit",
            })}{" "}
            ({formatRelative(forecast.nextRunAtKst, now)})
          </span>
        )}
      </div>

      {/* 4카드 그리드 */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {/* 파이프라인 카드 */}
        <Link to="/admin/pipeline">
          <Card className="hover:shadow-md transition-shadow h-full">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm text-muted-foreground">파이프라인 (오늘)</CardTitle>
            </CardHeader>
            <CardContent>
              {pipelineFailed === 0 ? (
                <>
                  <div
                    className="text-lg font-semibold text-muted-foreground"
                    data-testid="pipeline-ok"
                  >
                    ✓ 이상 없음
                  </div>
                  <div className="text-xs text-muted-foreground">
                    전체 {pipelineTotal}회 · 평균 {Math.round(avgDurationMs / 1000)}초
                    {pipelineAvgIsSampled && (
                      <span title="최근 100건 샘플 기반 추정"> (샘플)</span>
                    )}
                  </div>
                </>
              ) : (
                <>
                  <div
                    className="text-3xl font-bold text-destructive"
                    data-testid="pipeline-failed"
                  >
                    {pipelineFailed}건 실패
                  </div>
                  <div className="text-xs text-muted-foreground">
                    전체 {pipelineTotal}회 · 성공률 {pipelineSuccessRate}% · 평균{" "}
                    {Math.round(avgDurationMs / 1000)}초
                    {pipelineAvgIsSampled && (
                      <span title="최근 100건 샘플 기반 추정"> (샘플)</span>
                    )}
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        </Link>

        {/* 다이제스트 발송 카드 */}
        <Link to="/admin/delivery">
          <Card className="hover:shadow-md transition-shadow h-full">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm text-muted-foreground">다이제스트 발송 (오늘)</CardTitle>
            </CardHeader>
            <CardContent>
              {deliveryFailed === 0 ? (
                <>
                  <div className="text-lg font-semibold text-muted-foreground">✓ 이상 없음</div>
                  <div className="text-xs text-muted-foreground">
                    전체 {deliveryTotal}건 · 채널 {uniqueChannels}개
                    {deliveryChannelIsSampled && (
                      <span title="최근 100건 샘플 기반 추정"> (샘플)</span>
                    )}
                  </div>
                </>
              ) : (
                <>
                  <div className="text-3xl font-bold text-destructive">
                    {deliveryFailed}건 실패
                  </div>
                  <div className="text-xs text-muted-foreground">
                    전체 {deliveryTotal} · 성공률 {deliverySuccessRate}% · 채널 {uniqueChannels}개
                    {deliveryChannelIsSampled && (
                      <span title="최근 100건 샘플 기반 추정"> (샘플)</span>
                    )}
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        </Link>

        {/* LLM 비용 카드 */}
        <Link to="/admin/cost">
          <Card className="hover:shadow-md transition-shadow h-full">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm text-muted-foreground">LLM 비용</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold">${totalCostUsd.toFixed(2)}</div>
              <div className="text-xs text-muted-foreground">
                {usagePct != null ? `월 예산 ${usagePct}%` : "예산 미설정"}
              </div>
              <div className="mt-2">
                <CostSparkline data={sparklineData} />
              </div>
            </CardContent>
          </Card>
        </Link>

        {/* 사용자 반응 카드 */}
        {engagement && (
          <UserEngagementCard
            yesterdayClickRate={engagement.yesterdayClickRate}
            sevenDayAvg={engagement.sevenDayAvgClickRate}
            stdDev={engagement.sevenDayStdDev}
            positive={engagement.feedbackPositiveYesterday}
            negative={engagement.feedbackNegativeYesterday}
          />
        )}
      </div>
    </section>
  );
}
