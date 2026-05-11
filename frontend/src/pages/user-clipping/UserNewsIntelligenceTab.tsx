import { useQuery } from "@tanstack/react-query";
import { userHistoryKeys } from "@/queries/userHistoryKeys";
import { userHistoryService } from "@/services/userHistoryService";

interface KeywordTrendItem {
  keyword: string;
  totalCount: number;
  changeRate: number;
  dailyCounts: number[];
}

interface UserNewsIntelligenceTabProps {
  yearMonth: string;
  approvedCategoryIds?: string[];
}

function getMonthRange(yearMonth: string): { from: string; to: string } {
  const [y, m] = yearMonth.split("-").map(Number);
  const now = new Date();
  const isCurrentMonth = y === now.getFullYear() && m === now.getMonth() + 1;
  const firstDay = `${yearMonth}-01`;
  if (isCurrentMonth) {
    const today = `${yearMonth}-${String(now.getDate()).padStart(2, "0")}`;
    return { from: firstDay, to: today };
  }
  const lastDay = new Date(y, m, 0).getDate();
  return { from: firstDay, to: `${yearMonth}-${String(lastDay).padStart(2, "0")}` };
}

function trendLabel(rate: number): { text: string; color: string } {
  const abs = Math.abs(rate);
  if (rate > 0) {
    if (abs >= 1.0) return { text: "급상승", color: "var(--status-danger-text)" };
    if (abs >= 0.5) return { text: "상승", color: "var(--status-danger-text)" };
    if (abs >= 0.2) return { text: "소폭↑", color: "var(--status-warning-text)" };
    return { text: "유지", color: "var(--muted-foreground)" };
  }
  if (rate < 0) {
    if (abs >= 0.5) return { text: "하락", color: "var(--status-neutral-text)" };
    if (abs >= 0.1) return { text: "소폭↓", color: "var(--status-neutral-text)" };
    return { text: "유지", color: "var(--muted-foreground)" };
  }
  return { text: "유지", color: "var(--muted-foreground)" };
}

function KeywordRankList({ keywords, title }: { keywords: KeywordTrendItem[]; title: string }) {
  const maxCount = Math.max(...keywords.map((k) => k.totalCount), 1);

  return (
    <div className="rounded-xl border bg-card p-4 space-y-3">
      <h4 className="text-sm font-semibold">{title}</h4>
      {keywords.length === 0 ? (
        <p className="text-xs text-muted-foreground">데이터가 없어요</p>
      ) : (
        <div className="space-y-2">
          {keywords.map((kw, i) => {
            const { text, color } = trendLabel(kw.changeRate);
            const barWidth = Math.round((kw.totalCount / maxCount) * 100);
            return (
              <div key={kw.keyword} className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground w-4 shrink-0">{i + 1}</span>
                <div className="flex-1 min-w-0 space-y-0.5">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-medium truncate">{kw.keyword}</span>
                    <span className="text-xs font-medium shrink-0" style={{ color }}>
                      {text}
                    </span>
                  </div>
                  <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                    <div className="h-full rounded-full bg-[var(--status-neutral-text)] transition-all" style={{ width: `${barWidth}%` }} />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {kw.totalCount}건
                    {kw.changeRate !== 0 && (
                      <span className="ml-1">
                        ({kw.changeRate > 0 ? "+" : ""}
                        {(kw.changeRate * 100).toFixed(0)}%)
                      </span>
                    )}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export function UserNewsIntelligenceTab({ yearMonth }: UserNewsIntelligenceTabProps) {
  const range = getMonthRange(yearMonth);

  const { data: historyPage, isLoading } = useQuery({
    queryKey: userHistoryKeys.articles({ from: range.from, to: range.to }),
    queryFn: () =>
      userHistoryService.searchArticleHistory({
        page: 0,
        size: 3,
        dateFrom: range.from,
        dateTo: range.to
      })
  });

  const topArticles = historyPage?.items ?? [];
  const displayArticles = topArticles.slice(0, 3);

  if (isLoading) {
    return <div className="py-8 text-center text-sm text-muted-foreground">불러오는 중...</div>;
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <KeywordRankList keywords={[]} title="전체 키워드 트렌드 TOP 10" />
        <KeywordRankList keywords={[]} title="내 구독 키워드 트렌드" />
      </div>

      <div className="rounded-xl border bg-card p-4 space-y-3">
        <h4 className="text-sm font-semibold">주요 기사 TOP 3</h4>
        {displayArticles.length > 0 ? (
          <div className="space-y-3">
            {displayArticles.map((article, i) => (
              <div key={article.id ?? i} className="flex items-start gap-3">
                <span
                  className={`shrink-0 text-sm font-bold w-6 text-center ${i < 3 ? "text-[var(--status-warning-text)]" : "text-muted-foreground"}`}
                >
                  {i + 1}
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">{article.title}</p>
                  {article.categoryName && (
                    <span className="text-xs text-muted-foreground">{article.categoryName}</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">데이터가 없어요</p>
        )}
      </div>
    </div>
  );
}
