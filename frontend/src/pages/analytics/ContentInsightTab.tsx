// frontend/src/pages/analytics/ContentInsightTab.tsx
import { useQuery } from "@tanstack/react-query";
import { InfoTooltip } from "@/components/shared/InfoTooltip";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { insightKeys } from "@/queries/insightKeys";
import { newsReportService } from "@/services/newsReportService";
import { EmptyState } from "@/components/shared/EmptyState";

interface ContentInsightTabProps {
  categoryId?: string;
  from: string;
  to: string;
  days: number;
}

function getTrendLabel(changeRate: number): { text: string; className: string } {
  if (changeRate > 20) return { text: "상승", className: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]" };
  if (changeRate < -10) return { text: "하락", className: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]" };
  return { text: "유지", className: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]" };
}

function formatChangeRate(rate: number): { text: string; className: string } {
  const prefix = rate > 0 ? "+" : "";
  const text = `${prefix}${Math.round(rate)}%`;
  const className = rate > 0
    ? "text-[var(--status-success-text)]"
    : rate < 0
      ? "text-[var(--status-danger-text)]"
      : "text-muted-foreground";
  return { text, className };
}

function formatShortDate(dateStr: string): string {
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

export function ContentInsightTab({ categoryId, days }: ContentInsightTabProps) {
  const keywordQuery = useQuery({
    queryKey: insightKeys.keywords({ days, categoryId }),
    queryFn: () => newsReportService.getKeywordTrend({ days, top: 10, categoryId }),
  });

  const sentimentQuery = useQuery({
    queryKey: insightKeys.sentiment({ days, categoryId }),
    queryFn: () => newsReportService.getSentimentTrend({ days, categoryId }),
  });

  const isAllEmpty =
    !keywordQuery.data?.keywords?.length &&
    !sentimentQuery.data?.daily?.length;

  if (keywordQuery.isLoading && sentimentQuery.isLoading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          <div className="h-64 rounded-xl bg-muted/30 border border-border animate-pulse" />
          <div className="h-64 rounded-xl bg-muted/30 border border-border animate-pulse" />
        </div>
      </div>
    );
  }

  if (isAllEmpty && !keywordQuery.isLoading) {
    return (
      <EmptyState
        title="아직 분석할 데이터가 없어요"
        description="파이프라인이 실행되면 여기에 인사이트가 나타나요"
      />
    );
  }

  const keywords = keywordQuery.data?.keywords ?? [];
  const sentiment = sentimentQuery.data;

  return (
    <div className="space-y-8">
      {/* 키워드 추이 */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">키워드 추이</h2>
          {keywordQuery.data?.period && (
            <span className="text-xs text-muted-foreground">
              {keywordQuery.data.period.from} ~ {keywordQuery.data.period.to}
            </span>
          )}
        </div>
        {keywords.length > 0 ? (
          <div className="rounded-xl border bg-card overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/30">
                  <th className="text-left p-3 font-medium">키워드</th>
                  <th className="text-right p-3 font-medium">총 언급수</th>
                  <th className="text-right p-3 font-medium">변화율</th>
                  <th className="text-center p-3 font-medium">추세</th>
                </tr>
              </thead>
              <tbody>
                {keywords.map((kw) => {
                  const change = formatChangeRate(kw.changeRate);
                  const trend = getTrendLabel(kw.changeRate);
                  return (
                    <tr key={kw.keyword} className="border-b last:border-b-0">
                      <td className="p-3 font-medium">{kw.keyword}</td>
                      <td className="p-3 text-right tabular-nums">{kw.totalCount}</td>
                      <td className={`p-3 text-right tabular-nums ${change.className}`}>
                        {change.text}
                      </td>
                      <td className="p-3 text-center">
                        <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${trend.className}`}>
                          {trend.text}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">키워드 데이터가 없어요</p>
        )}
      </section>

      {/* 감성 트렌드 */}
      {sentiment && (
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <h2 className="text-lg font-semibold">감성 트렌드</h2>
              <InfoTooltip
                ariaLabel="감성 트렌드 설명"
                content="Gemini AI가 기사 본문을 분석해 POSITIVE/NEUTRAL/NEGATIVE 중 하나로 판정합니다. 요약 생성 시점에 한 번 계산되어 저장됩니다."
              />
            </div>
            {sentiment.summary.dominantSentiment && (
              <span className="text-xs text-muted-foreground">
                주요 감성: {sentiment.summary.dominantSentiment}
              </span>
            )}
          </div>

          {/* 감성 비율 카드 */}
          <div className="grid grid-cols-3 gap-4">
            <div className="rounded-xl border bg-card p-4">
              <p className="text-xs text-muted-foreground">긍정률</p>
              <p className="text-xl font-bold mt-1">{sentiment.summary.positiveRate.toFixed(1)}%</p>
            </div>
            <div className="rounded-xl border bg-card p-4">
              <p className="text-xs text-muted-foreground">중립률</p>
              <p className="text-xl font-bold mt-1">{sentiment.summary.neutralRate.toFixed(1)}%</p>
            </div>
            <div className="rounded-xl border bg-card p-4">
              <p className="text-xs text-muted-foreground">부정률</p>
              <p className="text-xl font-bold mt-1">{sentiment.summary.negativeRate.toFixed(1)}%</p>
            </div>
          </div>

          {/* 차트 */}
          {sentiment.daily.length > 1 && (
            <div className="rounded-xl border bg-card p-4">
              <ResponsiveContainer width="100%" height={240}>
                <AreaChart data={sentiment.daily}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                  <XAxis
                    dataKey="date"
                    tickFormatter={formatShortDate}
                    className="text-xs"
                    tick={{ fill: "var(--color-muted-foreground)" }}
                  />
                  <YAxis className="text-xs" tick={{ fill: "var(--color-muted-foreground)" }} />
                  <Tooltip />
                  <Area
                    type="monotone"
                    dataKey="positive"
                    name="긍정"
                    stackId="1"
                    stroke="var(--status-success-text)"
                    fill="var(--status-success-bg)"
                  />
                  <Area
                    type="monotone"
                    dataKey="neutral"
                    name="중립"
                    stackId="1"
                    stroke="var(--status-neutral-text)"
                    fill="var(--status-neutral-bg)"
                  />
                  <Area
                    type="monotone"
                    dataKey="negative"
                    name="부정"
                    stackId="1"
                    stroke="var(--status-danger-text)"
                    fill="var(--status-danger-bg)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
