import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { TrendingUp, TrendingDown, Minus, Newspaper, FileText, Sparkles, ArrowUpRight, ExternalLink } from "lucide-react";
import { userKeys } from "@/queries/userKeys";
import { userHistoryKeys } from "@/queries/userHistoryKeys";
import { userClippingKeys } from "@/queries/userClippingKeys";
import { userService } from "@/services/userService";
import { userHistoryService } from "@/services/userHistoryService";
import { userIntelligenceService } from "@/services/userIntelligenceService";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState } from "@/components/shared/EmptyState";
import { cn } from "@/utils/cn";
import type { KeywordTrendItem, KeywordTrendResponse, TopArticleItem } from "@/types/newsReport";

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function prevYearMonth(ym: string): string {
  const [y, m] = ym.split("-").map(Number);
  const d = new Date(y, m - 2, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

function generatePastMonths(count: number): { value: string; label: string }[] {
  const result: { value: string; label: string }[] = [];
  const now = new Date();
  for (let i = 0; i < count; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    const label = `${d.getFullYear()}년 ${d.getMonth() + 1}월`;
    result.push({ value, label });
  }
  return result;
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

function formatChange(current: number, previous: number): { label: string; type: "up" | "down" | "flat" } {
  if (previous === 0) return { label: current > 0 ? "신규" : "-", type: "flat" };
  const diff = current - previous;
  const pct = Math.round((diff / previous) * 100);
  if (pct === 0) return { label: "동일", type: "flat" };
  if (pct > 0) return { label: `+${pct}%`, type: "up" };
  return { label: `${pct}%`, type: "down" };
}

function trendBadge(rate: number): { text: string; type: "up" | "down" | "flat" } {
  const abs = Math.abs(rate);
  if (rate > 0) {
    if (abs >= 1.0) return { text: "급상승", type: "up" };
    if (abs >= 0.5) return { text: "상승", type: "up" };
    if (abs >= 0.2) return { text: "소폭↑", type: "up" };
    return { text: "유지", type: "flat" };
  }
  if (rate < 0) {
    if (abs >= 0.5) return { text: "하락", type: "down" };
    if (abs >= 0.1) return { text: "소폭↓", type: "down" };
    return { text: "유지", type: "flat" };
  }
  return { text: "유지", type: "flat" };
}

function mergeKeywordTrends(responses: KeywordTrendResponse[]): KeywordTrendItem[] {
  const map = new Map<string, { totalCount: number; changeRate: number; weight: number }>();
  for (const res of responses) {
    for (const kw of res.keywords) {
      const existing = map.get(kw.keyword);
      if (existing) {
        const newTotal = existing.totalCount + kw.totalCount;
        existing.changeRate = (existing.changeRate * existing.weight + kw.changeRate * kw.totalCount) / newTotal;
        existing.totalCount = newTotal;
        existing.weight = newTotal;
      } else {
        map.set(kw.keyword, { totalCount: kw.totalCount, changeRate: kw.changeRate, weight: kw.totalCount });
      }
    }
  }
  return Array.from(map.entries())
    .map(([keyword, data]) => ({
      keyword,
      totalCount: data.totalCount,
      changeRate: Math.round(data.changeRate * 100) / 100,
      dailyCounts: []
    }))
    .sort((a, b) => b.totalCount - a.totalCount);
}

const CATEGORY_COLORS = [
  "var(--chart-1)", "var(--chart-2)", "var(--chart-3)",
  "var(--chart-4)", "var(--chart-5)", "var(--chart-6)",
];

export function UserNewsReportPage() {
  const [selectedMonth, setSelectedMonth] = useState(currentYearMonth());
  const monthOptions = generatePastMonths(12);
  const range = getMonthRange(selectedMonth);
  const prevMonth = prevYearMonth(selectedMonth);
  const [, month] = selectedMonth.split("-").map(Number);
  const monthLabel = `${month}월`;

  const { data: requests = [], isLoading: isLoadingRequests } = useQuery({
    queryKey: userKeys.clippingRequests(),
    queryFn: () => userService.listClippingRequests()
  });

  const activeRequests = requests.filter((r) => r.status === "APPROVED");
  const approvedCategoryIds = activeRequests.map((r) => r.approvedCategoryId).filter((id): id is string => Boolean(id));
  const enabled = approvedCategoryIds.length > 0;

  // 현재 월 통계
  const { data: stats = [], isLoading: isLoadingStats } = useQuery({
    queryKey: userHistoryKeys.monthlyStats(selectedMonth),
    queryFn: () => userHistoryService.getUserMonthlyStats(selectedMonth),
    enabled,
  });

  // 전월 통계 (비교용)
  const { data: prevStats = [] } = useQuery({
    queryKey: userHistoryKeys.monthlyStats(prevMonth),
    queryFn: () => userHistoryService.getUserMonthlyStats(prevMonth),
    enabled,
  });

  // 전체 키워드 트렌드
  const { data: allTrendData } = useQuery({
    queryKey: userClippingKeys.keywordTrendAll(range.from, range.to),
    queryFn: () => userIntelligenceService.getKeywordTrend({ from: range.from, to: range.to, top: 10 }),
    enabled,
  });

  // 카테고리별 키워드 트렌드
  const { data: categoryTrendData } = useQuery({
    queryKey: userClippingKeys.keywordTrendCategories(range.from, range.to, approvedCategoryIds.join(",")),
    queryFn: async () => {
      const results = await Promise.all(
        approvedCategoryIds.map((catId) =>
          userIntelligenceService.getKeywordTrend({ from: range.from, to: range.to, top: 10, categoryId: catId })
        )
      );
      return mergeKeywordTrends(results).slice(0, 10);
    },
    enabled,
  });

  // 핵심 기사
  const { data: topArticlesData } = useQuery({
    queryKey: userClippingKeys.topArticles(range.from, range.to),
    queryFn: () => userIntelligenceService.getTopArticles({ from: range.from, to: range.to, limit: 5 }),
    enabled,
  });

  const totalSent = stats.reduce((sum, s) => sum + s.itemsSent, 0);
  const totalCollected = stats.reduce((sum, s) => sum + s.itemsCollected, 0);
  const totalSummarized = stats.reduce((sum, s) => sum + s.itemsSummarized, 0);
  const prevTotalSent = prevStats.reduce((sum, s) => sum + s.itemsSent, 0);
  const prevTotalCollected = prevStats.reduce((sum, s) => sum + s.itemsCollected, 0);
  const prevTotalSummarized = prevStats.reduce((sum, s) => sum + s.itemsSummarized, 0);

  const allKeywords = allTrendData?.keywords ?? [];
  const myKeywords = categoryTrendData ?? [];
  const topArticles: TopArticleItem[] = topArticlesData?.items ?? [];

  // 가장 급상승한 키워드 찾기
  const topRisingKeyword = [...allKeywords, ...myKeywords]
    .filter((k) => k.changeRate > 0)
    .sort((a, b) => b.changeRate - a.changeRate)[0];

  if (isLoadingRequests) {
    return (
      <div className="p-4 sm:p-6 space-y-5">
        <div className="h-8 w-48 rounded-lg bg-muted animate-pulse" />
        <div className="h-16 rounded-xl bg-muted animate-pulse" />
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => <div key={i} className="h-28 rounded-xl bg-muted animate-pulse" />)}
        </div>
        <div className="h-64 rounded-xl bg-muted animate-pulse" />
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">뉴스 리포트</h1>
          <p className="text-sm text-muted-foreground mt-1">
            내가 받은 뉴스를 한눈에 확인하고, 파일로 내려받을 수 있어요.
          </p>
        </div>
        <a
          href={`/api/user/reports/monthly.csv?yearMonth=${selectedMonth}`}
          className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors border rounded-lg px-3 py-1.5"
        >
          <ArrowUpRight className="h-3.5 w-3.5" />
          CSV 다운로드
        </a>
      </div>

      {/* 월 선택 */}
      <Select value={selectedMonth} onValueChange={setSelectedMonth}>
        <SelectTrigger className="w-36">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {monthOptions.map((opt) => (
            <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
          ))}
        </SelectContent>
      </Select>

      {!enabled ? (
        <EmptyState title="구독 중인 주제가 없어요" description="구독이 승인되면 월별 통계를 확인할 수 있어요" />
      ) : (
        <>
          {/* AI 인사이트 한 줄 */}
          {topRisingKeyword && (
            <div className="rounded-xl border border-primary/20 bg-primary/5 px-4 py-3 flex items-center gap-2.5">
              <Sparkles className="h-4 w-4 text-primary shrink-0" />
              <p className="text-sm text-foreground">
                이번 달 <span className="font-semibold text-primary">'{topRisingKeyword.keyword}'</span> 키워드가
                전월 대비{" "}
                <span className="font-semibold">
                  {Math.round(topRisingKeyword.changeRate * 100) > 0
                    ? `+${Math.round(topRisingKeyword.changeRate * 100)}%`
                    : "급상승"}
                </span>
                했어요
              </p>
            </div>
          )}

          {/* 핵심 기사 (최우선 노출) */}
          {topArticles.length > 0 && (
            <div className="rounded-xl border bg-card p-5 space-y-4">
              <h3 className="text-sm font-semibold">놓치면 아쉬운 {monthLabel} 핵심 기사</h3>
              <div className="space-y-3">
                {topArticles.map((article, i) => (
                  <div key={article.summaryId} className="flex items-start gap-3 group">
                    <span
                      className={cn(
                        "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold",
                        i < 3 ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
                      )}
                    >
                      {i + 1}
                    </span>
                    <div className="space-y-1.5 min-w-0 flex-1">
                      <a
                        href={article.sourceLink}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-sm font-medium hover:text-primary transition-colors line-clamp-2 block"
                      >
                        {article.title}
                        <ExternalLink className="inline h-3 w-3 ml-1 opacity-0 group-hover:opacity-50 transition-opacity" />
                      </a>
                      {article.keywords.length > 0 && (
                        <div className="flex flex-wrap gap-1">
                          {article.keywords.slice(0, 4).map((kw) => (
                            <span key={kw} className="text-xs bg-muted rounded-full px-2 py-0.5 text-muted-foreground">
                              {kw}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 키워드 트렌드 TOP 10 */}
          {allKeywords.length > 0 && (
            <div className="rounded-xl border bg-card p-5 space-y-4">
              <div>
                <h3 className="text-sm font-semibold">{monthLabel}에 어떤 키워드가 화제일까요?</h3>
                <p className="text-xs text-muted-foreground mt-0.5">전체 뉴스에서 가장 많이 등장한 키워드</p>
              </div>
              <div className="divide-y divide-border">
                {allKeywords.slice(0, 10).map((kw, i) => {
                  const trend = trendBadge(kw.changeRate);
                  const maxCount = allKeywords[0]?.totalCount ?? 1;
                  const barWidth = Math.max((kw.totalCount / maxCount) * 100, 8);
                  return (
                    <div key={kw.keyword} className="py-2.5 space-y-1.5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2.5">
                          <span className={cn(
                            "text-sm font-bold w-5 text-center",
                            i < 3 ? "text-primary" : "text-muted-foreground"
                          )}>
                            {i + 1}
                          </span>
                          <span className="text-sm font-medium">{kw.keyword}</span>
                        </div>
                        <div className="flex items-center gap-2 text-xs">
                          <span className="text-muted-foreground">{kw.totalCount}건</span>
                          {trend.type !== "flat" && (
                            <span className={cn(
                              "inline-flex items-center gap-0.5 font-medium rounded-full px-2 py-0.5",
                              trend.type === "up" && "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]",
                              trend.type === "down" && "bg-[var(--status-success-bg)] text-[var(--status-success-text)]"
                            )}>
                              {trend.type === "up" ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
                              {trend.text}
                            </span>
                          )}
                          {trend.type === "flat" && (
                            <span className="inline-flex items-center gap-0.5 text-muted-foreground bg-muted rounded-full px-2 py-0.5">
                              <Minus className="h-3 w-3" />
                              유지
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="h-1 bg-muted rounded-full overflow-hidden ml-7">
                        <div
                          className="h-full rounded-full transition-all"
                          style={{ width: `${barWidth}%`, backgroundColor: i < 3 ? "var(--chart-1)" : "var(--chart-3)", opacity: 1 - i * 0.06 }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* 통계 카드 — 전월 대비 표시 */}
          {isLoadingStats ? (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {[1, 2, 3].map((i) => <div key={i} className="h-28 rounded-xl bg-muted animate-pulse" />)}
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <StatCard
                icon={<Newspaper className="h-4 w-4" />}
                label="받은 뉴스"
                value={totalSent}
                change={formatChange(totalSent, prevTotalSent)}
                color="var(--chart-1)"
              />
              <StatCard
                icon={<FileText className="h-4 w-4" />}
                label="모은 기사"
                value={totalCollected}
                change={formatChange(totalCollected, prevTotalCollected)}
                color="var(--chart-2)"
              />
              <StatCard
                icon={<Sparkles className="h-4 w-4" />}
                label="AI 요약"
                value={totalSummarized}
                change={formatChange(totalSummarized, prevTotalSummarized)}
                color="var(--chart-4)"
              />
            </div>
          )}

          {/* 주제별 분석 */}
          {stats.length > 0 && (
            <div className="rounded-xl border bg-card p-5 space-y-4">
              <h3 className="text-sm font-semibold">주제별 분석</h3>
              <div className="space-y-4">
                {stats.map((stat, idx) => {
                  const pct = totalSent > 0 ? Math.round((stat.itemsSent / totalSent) * 100) : 0;
                  const color = CATEGORY_COLORS[idx % CATEGORY_COLORS.length];
                  return (
                    <div key={stat.id} className="space-y-2">
                      <div className="flex items-center justify-between">
                        <p className="text-sm font-medium">{stat.categoryName}</p>
                        <span className="text-xs text-muted-foreground">
                          {stat.itemsSent}건 ({pct}%)
                        </span>
                      </div>
                      <div className="h-1.5 bg-muted rounded-full overflow-hidden">
                        <div
                          className="h-full rounded-full transition-all"
                          style={{ width: `${pct}%`, backgroundColor: color }}
                        />
                      </div>
                      {stat.topKeywords.length > 0 && (
                        <div className="flex flex-wrap gap-1.5">
                          {stat.topKeywords.slice(0, 8).map((kw, i) => (
                            <span key={i} className="text-xs bg-muted rounded-full px-2.5 py-0.5 text-muted-foreground">
                              {kw}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

/* ── 통계 카드 ── */

function StatCard({
  icon,
  label,
  value,
  change,
  color,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  change: { label: string; type: "up" | "down" | "flat" };
  color: string;
}) {
  return (
    <div className="rounded-xl border bg-card p-5 space-y-2 border-l-4" style={{ borderLeftColor: color }}>
      <div className="flex items-center gap-2">
        <span className="text-muted-foreground">{icon}</span>
        <p className="text-sm text-muted-foreground">{label}</p>
      </div>
      <div className="flex items-end justify-between">
        <p className="text-3xl font-bold" style={{ color }}>
          {value.toLocaleString()}
          <span className="text-sm font-normal text-muted-foreground ml-1">건</span>
        </p>
        <span className={cn(
          "text-xs font-medium",
          change.type === "up" && "text-[var(--status-success-text)]",
          change.type === "down" && "text-[var(--status-danger-text)]",
          change.type === "flat" && "text-muted-foreground"
        )}>
          {change.type === "up" && "↑ "}
          {change.type === "down" && "↓ "}
          전월 {change.label}
        </span>
      </div>
    </div>
  );
}
