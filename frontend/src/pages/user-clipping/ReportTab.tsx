import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { TrendingUp, TrendingDown, Minus, Newspaper, FileText, Sparkles, ArrowUpRight, AlertCircle, RefreshCw, Info } from "lucide-react";
import { userHistoryKeys } from "@/queries/userHistoryKeys";
import { newsIntelligenceKeys } from "@/queries/newsIntelligenceKeys";
import { userHistoryService } from "@/services/userHistoryService";
import { newsIntelligenceService } from "@/services/newsIntelligenceService";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { EmptyState } from "@/components/shared/EmptyState";
import { cn } from "@/utils/cn";
import {
  currentYearMonthKst,
  prevYearMonth,
  generatePastMonthsKst,
  getMonthRangeKst,
} from "@/utils/date";
import {
  formatChange,
  aggregateByCategory,
  pickTopRisingKeyword,
  type ChangeBadge,
  type TrendKeyword,
  type CategoryAggregate,
} from "@/utils/stats";

/** 한 번에 조회 가능한 과거 개월 수 */
const MONTHS_TO_SHOW = 12;
/** 키워드 트렌드 상위 N */
const KEYWORD_TREND_TOP = 10;
/** 주제별 분석에 노출할 카테고리별 키워드 칩 최대 개수 */
const CATEGORY_KEYWORD_LIMIT = 8;
/** 상위 강조 순위 */
const TOP_RANK_HIGHLIGHT = 3;
/** 키워드 섹션 앵커. AI 인사이트 배너 클릭 시 스크롤 타겟. */
const KEYWORD_TREND_ANCHOR = "keyword-trend-section";

export function ReportTab({ approvedCategoryIds }: { approvedCategoryIds: string[] }) {
  const [selectedMonth, setSelectedMonth] = useState(currentYearMonthKst());
  const monthOptions = generatePastMonthsKst(MONTHS_TO_SHOW);
  const enabled = approvedCategoryIds.length > 0;

  // 현재 월 통계
  const statsQuery = useQuery({
    queryKey: userHistoryKeys.monthlyStats(selectedMonth),
    queryFn: () => userHistoryService.getUserMonthlyStats(selectedMonth),
    enabled,
  });
  const stats = statsQuery.data ?? [];

  // 전월 통계 (비교용) — isSuccess 전엔 변화율을 노출하지 않는다 (깜빡임 방지)
  const prevMonth = prevYearMonth(selectedMonth);
  const prevStatsQuery = useQuery({
    queryKey: userHistoryKeys.monthlyStats(prevMonth),
    queryFn: () => userHistoryService.getUserMonthlyStats(prevMonth),
    enabled,
  });
  const prevStats = prevStatsQuery.data ?? [];
  const prevLoaded = prevStatsQuery.isSuccess;

  // 키워드 트렌드
  const { from, to } = getMonthRangeKst(selectedMonth);
  const keywordTrendQuery = useQuery({
    queryKey: newsIntelligenceKeys.userKeywordTrend({ from, to, top: KEYWORD_TREND_TOP }),
    queryFn: () => newsIntelligenceService.getUserKeywordTrend({ from, to, top: KEYWORD_TREND_TOP }),
    enabled,
  });
  const keywordTrend = keywordTrendQuery.data;

  const totalSent = stats.reduce((sum, s) => sum + s.itemsSent, 0);
  const totalCollected = stats.reduce((sum, s) => sum + s.itemsCollected, 0);
  const totalSummarized = stats.reduce((sum, s) => sum + s.itemsSummarized, 0);
  const prevTotalSent = prevStats.reduce((sum, s) => sum + s.itemsSent, 0);
  const prevTotalCollected = prevStats.reduce((sum, s) => sum + s.itemsCollected, 0);
  const prevTotalSummarized = prevStats.reduce((sum, s) => sum + s.itemsSummarized, 0);

  const sentChange = formatChange(totalSent, prevTotalSent, prevLoaded);
  const collectedChange = formatChange(totalCollected, prevTotalCollected, prevLoaded);
  const summarizedChange = formatChange(totalSummarized, prevTotalSummarized, prevLoaded);

  const topRisingKeyword = pickTopRisingKeyword(keywordTrend?.keywords ?? []);
  const categoryAggregates = aggregateByCategory(stats);

  if (!enabled) {
    return <EmptyState title="구독 중인 주제가 없어요" description="구독이 승인되면 월별 리포트를 확인할 수 있어요" />;
  }

  // 에러 상태 — 재시도 UX: 화면 유지 + 수동 재시도 (스켈레톤으로 되돌아가지 않는다)
  if (statsQuery.isError) {
    return (
      <TooltipProvider delayDuration={200}>
        <div className="space-y-6">
          <MonthSelector value={selectedMonth} options={monthOptions} onChange={setSelectedMonth} />
          <div
            role="alert"
            className="rounded-xl border border-destructive/20 bg-destructive/5 p-5 flex items-start gap-3"
          >
            <AlertCircle className="h-5 w-5 text-destructive shrink-0 mt-0.5" />
            <div className="space-y-2 flex-1">
              <p className="text-sm font-medium text-foreground">월간 리포트를 불러오지 못했어요</p>
              <p className="text-xs text-muted-foreground">잠시 후 다시 시도하거나 아래 버튼을 눌러주세요.</p>
              <button
                type="button"
                onClick={() => statsQuery.refetch()}
                disabled={statsQuery.isFetching}
                className="inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:underline disabled:opacity-60"
              >
                <RefreshCw className={cn("h-3.5 w-3.5", statsQuery.isFetching && "animate-spin")} />
                {statsQuery.isFetching ? "다시 불러오는 중…" : "다시 시도"}
              </button>
            </div>
          </div>
        </div>
      </TooltipProvider>
    );
  }

  if (statsQuery.isLoading) {
    return <ReportSkeleton />;
  }

  return (
    <TooltipProvider delayDuration={200}>
      <div className="space-y-6">
        <MonthSelector value={selectedMonth} options={monthOptions} onChange={setSelectedMonth} />

        {/* 통계 카드 — 수집 → 요약 → 받음 깔때기 순서.
            "받은 뉴스"만 의미 색상. 수집/요약은 파이프라인 볼륨이라 변화 색상을 muted로 둔다. */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <StatCard
            icon={<FileText className="h-4 w-4" />}
            label="모은 기사"
            value={totalCollected}
            change={collectedChange}
            semanticColor={false}
            tooltip="구독 중인 카테고리에서 수집·탐지된 전체 기사 수예요. 개인별 수치가 아니라 카테고리 파이프라인 처리량이에요."
          />
          <StatCard
            icon={<Sparkles className="h-4 w-4" />}
            label="AI 요약"
            value={totalSummarized}
            change={summarizedChange}
            semanticColor={false}
            tooltip="AI가 요약 처리한 기사 수예요. 수집된 기사 중 중요도 기준을 통과한 것만 요약되므로 모은 기사보다 적어요."
          />
          <StatCard
            icon={<Newspaper className="h-4 w-4" />}
            label="받은 뉴스"
            value={totalSent}
            change={sentChange}
            tooltip="이 달에 Slack으로 실제 전달이 완료된 기사 수예요. 발송 실패나 스킵된 건은 포함되지 않아요."
          />
        </div>

        {/* AI 인사이트 — 키워드 트렌드 섹션 바로 위에 배치해 헤드라인과 본문을 붙인다. */}
        {topRisingKeyword && (
          <a
            href={`#${KEYWORD_TREND_ANCHOR}`}
            className="block rounded-xl border border-primary/20 bg-primary/5 px-4 py-3 hover:bg-primary/10 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <div className="flex items-center gap-2.5">
              <Sparkles className="h-4 w-4 text-primary shrink-0" />
              <p className="text-sm text-foreground">
                이번 달 <span className="font-semibold text-primary">'{topRisingKeyword.keyword}'</span> 키워드가
                전월 대비 <span className="font-semibold">
                  +{Math.round(topRisingKeyword.changeRate * 100)}%
                </span> 늘었어요
              </p>
              <span className="ml-auto text-xs text-primary font-medium">자세히 →</span>
            </div>
          </a>
        )}

        {/* 키워드 트렌드 TOP 10 */}
        {keywordTrend && keywordTrend.keywords.length > 0 && (
          <KeywordTrendSection keywords={keywordTrend.keywords} />
        )}

        {/* 주제별 분석 또는 발송 없음 안내 */}
        {categoryAggregates.length > 0 && totalSent > 0 && (
          <CategoryBreakdown categories={categoryAggregates} totalSent={totalSent} />
        )}
        {categoryAggregates.length > 0 && totalSent === 0 && <NoDeliveryYetCard />}

        {/* CSV 다운로드 */}
        <div className="flex justify-end">
          <a
            href={`/api/user/reports/monthly.csv?yearMonth=${selectedMonth}`}
            className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
            aria-label={`${selectedMonth} 월간 리포트 CSV 다운로드`}
          >
            <ArrowUpRight className="h-3.5 w-3.5" />
            CSV 다운로드
          </a>
        </div>
      </div>
    </TooltipProvider>
  );
}

/* ── 월 선택 ── */

function MonthSelector({
  value,
  options,
  onChange,
}: {
  value: string;
  options: { value: string; label: string }[];
  onChange: (v: string) => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="w-44">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((opt) => (
            <SelectItem key={opt.value} value={opt.value}>
              {opt.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

/* ── 스켈레톤 — 실제 렌더 레이아웃과 블록 구조를 맞춰 layout shift를 줄인다 ── */

function ReportSkeleton() {
  return (
    <div className="space-y-6">
      <div className="h-10 w-44 rounded-lg bg-muted animate-pulse" />
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-24 rounded-xl bg-muted animate-pulse" />
        ))}
      </div>
      <div className="h-12 rounded-xl bg-muted animate-pulse" />
      <div className="h-80 rounded-xl bg-muted animate-pulse" />
      <div className="h-48 rounded-xl bg-muted animate-pulse" />
    </div>
  );
}

/* ── 키워드 트렌드 섹션 ── */

function KeywordTrendSection({ keywords }: { keywords: TrendKeyword[] }) {
  return (
    <div id={KEYWORD_TREND_ANCHOR} className="space-y-3 scroll-mt-6">
      <h3 className="text-sm font-medium">키워드 트렌드</h3>
      <div className="rounded-xl border bg-card divide-y divide-border">
        {keywords.map((kw, i) => (
          <KeywordTrendRow key={kw.keyword} rank={i + 1} keyword={kw} />
        ))}
      </div>
    </div>
  );
}

/**
 * 키워드 트렌드 변화율 뱃지의 색상은 "빨강=상승"이 아니라 "의미 대비"로 처리한다.
 * 뉴스 볼륨 상승이 사용자에게 위험 신호로 읽히지 않게, 주목(primary)과 약화(muted)로만 구분한다.
 */
function KeywordTrendRow({ rank, keyword }: { rank: number; keyword: TrendKeyword }) {
  const changePct = Math.round(keyword.changeRate * 100);
  const isUp = changePct > 0;
  const isDown = changePct < 0;
  return (
    <div className="flex items-center gap-3 px-4 py-2.5">
      <span className={cn(
        "text-sm font-bold w-6 text-center",
        rank <= TOP_RANK_HIGHLIGHT ? "text-primary" : "text-muted-foreground"
      )}>
        {rank}
      </span>
      <span className="text-sm flex-1">{keyword.keyword}</span>
      <span className="text-xs text-muted-foreground">{keyword.totalCount}건</span>
      {isUp && (
        <span className="inline-flex items-center gap-0.5 text-xs font-medium rounded-full px-2 py-0.5 bg-primary/10 text-primary">
          <TrendingUp className="h-3 w-3" />
          +{changePct}%
        </span>
      )}
      {isDown && (
        <span className="inline-flex items-center gap-0.5 text-xs font-medium rounded-full px-2 py-0.5 bg-muted text-muted-foreground">
          <TrendingDown className="h-3 w-3" />
          {changePct}%
        </span>
      )}
      {!isUp && !isDown && (
        <span className="inline-flex items-center gap-0.5 text-xs text-muted-foreground rounded-full px-2 py-0.5 bg-muted">
          <Minus className="h-3 w-3" />
          유지
        </span>
      )}
    </div>
  );
}

/* ── 주제별 분석 ── */

function CategoryBreakdown({
  categories,
  totalSent,
}: {
  categories: CategoryAggregate[];
  totalSent: number;
}) {
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-medium">주제별 분석</h3>
      <div className="rounded-xl border bg-card divide-y divide-border">
        {categories.map((cat) => {
          const pct = Math.round((cat.itemsSent / totalSent) * 100);
          return (
            <div key={cat.categoryId} className="p-4 space-y-2">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium">{cat.categoryName}</p>
                <span className="text-xs text-muted-foreground">
                  {cat.itemsSent}건 ({pct}%)
                </span>
              </div>
              <div className="h-1.5 bg-muted rounded-full overflow-hidden">
                <div className="h-full bg-primary rounded-full transition-all" style={{ width: `${pct}%` }} />
              </div>
              {cat.topKeywords.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {cat.topKeywords.slice(0, CATEGORY_KEYWORD_LIMIT).map((kw) => (
                    <span key={kw} className="text-xs bg-muted rounded-full px-2.5 py-0.5 text-muted-foreground">
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
  );
}

/* ── 발송 없음 안내 ── */

function NoDeliveryYetCard() {
  return (
    <div className="rounded-xl border bg-muted/30 p-5 text-center space-y-1">
      <p className="text-sm font-medium">이번 달 아직 발송된 뉴스가 없어요</p>
      <p className="text-xs text-muted-foreground">
        설정된 요일·시간이 되면 Slack으로 자동 전달돼요.
      </p>
    </div>
  );
}

/* ── 통계 카드 ── */

function StatCard({
  icon,
  label,
  value,
  change,
  tooltip,
  semanticColor = true,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  change: ChangeBadge;
  tooltip?: string;
  /**
   * 전월 대비 변화에 success/danger 의미 색상을 입힐지 여부.
   * `false`면 up/down 모두 muted로 표시 — 파이프라인 볼륨 지표(수집/요약)용.
   */
  semanticColor?: boolean;
}) {
  return (
    <div className="rounded-xl border bg-card p-4 space-y-2">
      <div className="flex items-center gap-2">
        <span className="text-muted-foreground">{icon}</span>
        <p className="text-xs text-muted-foreground">{label}</p>
        {tooltip && (
          <Tooltip>
            <TooltipTrigger asChild>
              <button type="button" aria-label={`${label} 설명`} className="text-muted-foreground hover:text-foreground transition-colors">
                <Info className="h-3 w-3" />
              </button>
            </TooltipTrigger>
            <TooltipContent side="top" className="max-w-xs text-xs">
              {tooltip}
            </TooltipContent>
          </Tooltip>
        )}
      </div>
      <div className="flex items-end justify-between">
        <p className="text-2xl font-bold">
          {value.toLocaleString()}
          <span className="text-sm font-normal text-muted-foreground ml-1">건</span>
        </p>
        <span className={cn(
          "text-xs font-medium",
          semanticColor && change.type === "up" && "text-[var(--status-success-text)]",
          semanticColor && change.type === "down" && "text-[var(--status-danger-text)]",
          !semanticColor && (change.type === "up" || change.type === "down") && "text-muted-foreground",
          change.type === "flat" && "text-muted-foreground",
          change.type === "none" && "text-muted-foreground"
        )}>
          {change.type === "up" && "↑ "}
          {change.type === "down" && "↓ "}
          {change.label}
        </span>
      </div>
    </div>
  );
}
