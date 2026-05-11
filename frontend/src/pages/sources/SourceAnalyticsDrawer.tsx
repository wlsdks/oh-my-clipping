import { useQuery } from "@tanstack/react-query";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { KpiCard } from "@/components/shared/KpiCard";
import { sourceService } from "@/services/sourceService";
import { sourceKeys } from "@/queries/sourceKeys";
import { CheckCircle2, XCircle } from "lucide-react";

const ANALYTICS_DAYS = 30;

interface SourceAnalyticsDrawerProps {
  sourceId: string | null;
  onClose: () => void;
}

/** 토큰 수를 읽기 쉬운 한국어 문자열로 변환한다. */
function formatTokenCount(tokens: number): string {
  if (tokens >= 1_000_000) return `${(tokens / 1_000_000).toFixed(1)}백만`;
  if (tokens >= 10_000) return `약 ${Math.round(tokens / 10_000)}만`;
  if (tokens >= 1_000) return `약 ${(tokens / 1_000).toFixed(1)}천`;
  return `${tokens}`;
}

export function SourceAnalyticsDrawer({
  sourceId,
  onClose,
}: SourceAnalyticsDrawerProps) {
  const {
    data,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: sourceKeys.analytics(sourceId ?? "", ANALYTICS_DAYS),
    queryFn: () => sourceService.getAnalytics(sourceId!, ANALYTICS_DAYS),
    enabled: sourceId !== null,
  });

  const {
    data: crawlData,
    isLoading: crawlLoading,
  } = useQuery({
    queryKey: sourceKeys.crawlHistory(sourceId ?? "", ANALYTICS_DAYS),
    queryFn: () => sourceService.getCrawlHistory(sourceId!, ANALYTICS_DAYS),
    enabled: sourceId !== null,
  });

  const {
    data: aiCostData,
  } = useQuery({
    queryKey: sourceKeys.aiCosts(ANALYTICS_DAYS),
    queryFn: () => sourceService.getAiCosts(ANALYTICS_DAYS),
    enabled: sourceId !== null,
  });

  // 차트 데이터: 날짜 오름차순으로 정렬
  const chartData = data?.dailyArticleCounts
    ? [...data.dailyArticleCounts]
        .sort((a, b) => a.date.localeCompare(b.date))
        .map((d) => ({
          date: d.date.slice(5), // "MM-DD" 형태
          count: d.count,
        }))
    : [];

  // 현재 소스의 AI 비용 데이터
  const sourceCost = sourceId && aiCostData?.costs?.[sourceId];

  return (
    <Sheet
      open={sourceId !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <SheetContent side="right" className="w-full sm:max-w-lg overflow-y-auto">
        {sourceId && (
          <>
            <SheetHeader>
              <SheetTitle className="text-base font-semibold">
                {isLoading ? "불러오는 중..." : data?.sourceName ?? "소스 분석"}
              </SheetTitle>
            </SheetHeader>

            <div className="space-y-5 mt-4">
              {/* 로딩 상태 */}
              {isLoading && (
                <div className="space-y-3">
                  <div className="grid grid-cols-3 gap-3">
                    <KpiCard label="" value="" loading />
                    <KpiCard label="" value="" loading />
                    <KpiCard label="" value="" loading />
                  </div>
                  <div className="h-48 animate-pulse rounded-xl bg-muted" />
                </div>
              )}

              {/* 에러 상태 */}
              {isError && !isLoading && (
                <div className="flex flex-col items-center gap-3 py-12 text-center">
                  <p className="text-sm text-muted-foreground">
                    분석 데이터를 불러오지 못했어요
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => refetch()}
                  >
                    다시 시도
                  </Button>
                </div>
              )}

              {/* 데이터 로드 완료 */}
              {data && !isLoading && (
                <>
                  {/* KPI 카드 3열 */}
                  <div className="grid grid-cols-3 gap-3">
                    <KpiCard
                      label={`${data.days}일 수집`}
                      value={`${data.totalArticles}건`}
                    />
                    <KpiCard
                      label="일 평균"
                      value={`${data.avgArticlesPerDay}건`}
                    />
                    <KpiCard
                      label="신뢰도"
                      value={`${data.reliabilityScore}점`}
                      status={
                        data.reliabilityScore >= 80
                          ? "success"
                          : data.reliabilityScore >= 50
                            ? "warning"
                            : "danger"
                      }
                    />
                  </div>

                  {/* 일별 수집 추이 차트 */}
                  <div className="space-y-2">
                    <h4 className="text-xs font-medium text-muted-foreground">
                      일별 수집 추이
                    </h4>
                    {chartData.length === 0 ? (
                      <p className="text-sm text-muted-foreground text-center py-8">
                        조회 기간에 수집된 기사가 없어요
                      </p>
                    ) : (
                      <div className="h-48 rounded-xl border bg-card p-3">
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={chartData}>
                            <CartesianGrid
                              strokeDasharray="3 3"
                              className="stroke-border"
                            />
                            <XAxis
                              dataKey="date"
                              tick={{ fontSize: 10 }}
                              className="fill-muted-foreground"
                              interval="preserveStartEnd"
                            />
                            <YAxis
                              tick={{ fontSize: 10 }}
                              className="fill-muted-foreground"
                              allowDecimals={false}
                              width={30}
                            />
                            <Tooltip
                              contentStyle={{
                                borderRadius: 8,
                                fontSize: 12,
                              }}
                              formatter={(value) => [
                                `${value}건`,
                                "수집",
                              ]}
                            />
                            <Area
                              type="monotone"
                              dataKey="count"
                              stroke="var(--color-primary)"
                              fill="var(--color-primary)"
                              fillOpacity={0.1}
                              strokeWidth={2}
                            />
                          </AreaChart>
                        </ResponsiveContainer>
                      </div>
                    )}
                  </div>

                  {/* 크롤 이력 */}
                  <CrawlHistorySection loading={crawlLoading} data={crawlData} />

                  {/* AI 비용 */}
                  {sourceCost && <AiCostSection cost={sourceCost} days={ANALYTICS_DAYS} />}

                  {/* 수집 상태 */}
                  <div className="space-y-2">
                    <h4 className="text-xs font-medium text-muted-foreground">
                      수집 상태
                    </h4>
                    <div className="rounded-lg border bg-muted/30 p-3 space-y-2 text-sm">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-muted-foreground">
                          마지막 수집 성공
                        </span>
                        <span className="text-xs">
                          {data.lastSuccessAt
                            ? new Date(data.lastSuccessAt).toLocaleString(
                                "ko-KR",
                              )
                            : "없음"}
                        </span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-muted-foreground">
                          연속 실패
                        </span>
                        <span
                          className={`text-xs ${data.crawlFailCount > 0 ? "text-[var(--status-danger-text)]" : ""}`}
                        >
                          {data.crawlFailCount}회
                        </span>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

/** 크롤 이력 섹션: 가동률 뱃지, 평균 응답시간, 최근 로그 목록 */
function CrawlHistorySection({
  loading,
  data,
}: {
  loading: boolean;
  data: Awaited<ReturnType<typeof sourceService.getCrawlHistory>> | undefined;
}) {
  if (loading) {
    return (
      <div className="space-y-2">
        <h4 className="text-xs font-medium text-muted-foreground">크롤 이력</h4>
        <div className="h-24 animate-pulse rounded-xl bg-muted" />
      </div>
    );
  }

  if (!data || data.totalCrawls === 0) {
    return (
      <div className="space-y-2">
        <h4 className="text-xs font-medium text-muted-foreground">크롤 이력</h4>
        <p className="text-sm text-muted-foreground text-center py-4">
          조회 기간에 크롤 이력이 없어요
        </p>
      </div>
    );
  }

  const uptimeColor =
    data.uptimePercent != null && data.uptimePercent >= 95
      ? "bg-[var(--status-success-bg)] text-[var(--status-success-text)]"
      : data.uptimePercent != null && data.uptimePercent >= 80
        ? "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]"
        : "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]";

  const recentLogs = data.logs.slice(0, 10);

  return (
    <div className="space-y-2">
      <h4 className="text-xs font-medium text-muted-foreground">크롤 이력</h4>
      <div className="rounded-lg border bg-muted/30 p-3 space-y-3">
        {/* 요약 통계 */}
        <div className="flex items-center gap-3 text-xs">
          {data.uptimePercent != null && (
            <span className={`inline-flex items-center rounded-full px-2 py-0.5 font-medium ${uptimeColor}`}>
              가동률 {data.uptimePercent}%
            </span>
          )}
          {data.avgResponseTimeMs != null && (
            <span className="text-muted-foreground">
              평균 응답 {data.avgResponseTimeMs}ms
            </span>
          )}
          <span className="text-muted-foreground">
            성공 {data.successCount} / 실패 {data.failCount}
          </span>
        </div>

        {/* 최근 로그 목록 */}
        {recentLogs.length > 0 && (
          <div className="space-y-1">
            {recentLogs.map((log, i) => (
              <div
                key={i}
                className="flex items-center justify-between text-xs py-1"
              >
                <div className="flex items-center gap-1.5">
                  {log.success ? (
                    <CheckCircle2 className="h-3 w-3 text-[var(--status-success-text)]" />
                  ) : (
                    <XCircle className="h-3 w-3 text-[var(--status-danger-text)]" />
                  )}
                  <span className="text-muted-foreground">
                    {new Date(log.crawledAt).toLocaleString("ko-KR", {
                      month: "numeric",
                      day: "numeric",
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  {log.success && (
                    <span className="text-muted-foreground">
                      {log.articlesFound}건
                    </span>
                  )}
                  {log.responseTimeMs != null && (
                    <span className="text-muted-foreground">
                      {log.responseTimeMs}ms
                    </span>
                  )}
                  {!log.success && log.errorMessage && (
                    <span className="text-[var(--status-danger-text)] truncate max-w-[120px]">
                      {log.errorMessage}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/** AI 비용 섹션: 호출 횟수, 토큰 사용량, 추정 비용 */
function AiCostSection({
  cost,
  days,
}: {
  cost: { requestCount: number; tokensIn: number; tokensOut: number; estimatedUsd: number };
  days: number;
}) {
  return (
    <div className="space-y-2">
      <h4 className="text-xs font-medium text-muted-foreground">
        AI 비용 ({days}일)
      </h4>
      <div className="rounded-lg border bg-muted/30 p-3">
        <p className="text-sm">
          AI 호출 <span className="font-medium">{cost.requestCount}회</span>
          {" · "}토큰 약{" "}
          <span className="font-medium">
            {formatTokenCount(cost.tokensIn + cost.tokensOut)}
          </span>{" "}
          개{" · "}추정{" "}
          <span className="font-medium">${cost.estimatedUsd}</span>
        </p>
      </div>
    </div>
  );
}
