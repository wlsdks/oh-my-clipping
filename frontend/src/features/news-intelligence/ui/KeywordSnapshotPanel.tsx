import { useQuery } from "@tanstack/react-query";
import { LineChart, Line, XAxis, YAxis, ResponsiveContainer, Tooltip } from "recharts";
import { EmptyState } from "@/components/shared/EmptyState";
import { newsIntelligenceService } from "@/services/newsIntelligenceService";
import { newsIntelligenceKeys } from "@/queries/newsIntelligenceKeys";
import { userFriendlyMessage } from "../../../shared/lib/httpError";
import { CHART_COLORS, TossTooltip } from "@/utils/chartTheme";

interface KeywordSnapshotPanelProps {
  detailHref?: string;
}

export function KeywordSnapshotPanel({ detailHref }: KeywordSnapshotPanelProps) {
  const { data, isLoading, error } = useQuery({
    queryKey: newsIntelligenceKeys.keywordTrend({ days: 7, top: 5 }),
    queryFn: () => newsIntelligenceService.getKeywordTrend({ days: 7, top: 5 }),
  });

  const top3 = data?.keywords.slice(0, 3) ?? [];

  // 차트 데이터: 날짜별로 키워드 count를 병합
  const chartData = (() => {
    if (top3.length === 0) return [];
    const dateMap = new Map<string, Record<string, number>>();
    for (const kw of top3) {
      for (const dc of kw.dailyCounts) {
        const entry = dateMap.get(dc.date) ?? {};
        entry[kw.keyword] = dc.count;
        dateMap.set(dc.date, entry);
      }
    }
    return Array.from(dateMap.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, counts]) => ({ date: date.slice(5), ...counts }));
  })();

  const allKeywords = data?.keywords ?? [];

  function trendIndicator(changeRate: number): string {
    if (changeRate > 0.2) return "\uD83D\uDD25";
    if (changeRate < -0.1) return "\u2744\uFE0F";
    return "\u2014";
  }

  return (
    <section className="panel">
      <div className="panel-head">
        <h3>키워드 트렌드</h3>
        {detailHref && (
          <a href={detailHref} className="text-xs text-primary">
            상세 &rarr;
          </a>
        )}
      </div>

      {isLoading && <p className="text-sm text-muted-foreground">불러오는 중...</p>}
      {error && (
        <p className="text-sm text-destructive">
          {userFriendlyMessage(error, "키워드 트렌드를 불러오지 못했어요")}
        </p>
      )}

      {!isLoading && !error && allKeywords.length === 0 && (
        <EmptyState
          title="키워드 데이터가 아직 없어요"
          className="bg-muted rounded-xl py-6"
        />
      )}

      {!isLoading && !error && top3.length > 0 && (
        <>
          <ResponsiveContainer width="100%" height={120}>
            <LineChart data={chartData}>
              <XAxis dataKey="date" tick={{ fontSize: 10 }} />
              <YAxis hide />
              <Tooltip content={<TossTooltip />} />
              {top3.map((kw, i) => (
                <Line
                  key={kw.keyword}
                  type="monotone"
                  dataKey={kw.keyword}
                  stroke={CHART_COLORS[i % CHART_COLORS.length]}
                  strokeWidth={2}
                  dot={false}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>

          <div className="mt-3 grid gap-1">
            {allKeywords.map((kw) => (
              <div
                key={kw.keyword}
                className="flex items-center justify-between py-1 text-[13px]"
              >
                <span>{kw.keyword}</span>
                <span className="text-sm">{trendIndicator(kw.changeRate)}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  );
}
