import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  LabelList,
} from "recharts";
import { RefreshCw, ExternalLink } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/shared/EmptyState";
import { competitorKeys } from "@/queries/competitorKeys";
import { competitorService } from "@/services/competitorService";
import type { CompetitorTimelineItem } from "@/types/competitor";

// 기간 필터 옵션 정의
const PERIOD_OPTIONS = [
  { label: "이번 주", days: 7 },
  { label: "이번 달", days: 30 },
  { label: "최근 3개월", days: 90 },
] as const;

// 날짜 포맷: YYYY-MM-DD → M/D
function formatShortDate(dateStr: string): string {
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

// --- 기사 카드 ---
interface ArticleCardProps {
  item: CompetitorTimelineItem;
}

function ArticleCard({ item }: ArticleCardProps) {
  return (
    <button
      type="button"
      className="w-full text-left rounded-xl border border-border bg-card p-4 hover:bg-muted/40 transition-colors"
      onClick={() => window.open(item.sourceLink, "_blank", "noopener,noreferrer")}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1 space-y-1">
          {/* 제목 */}
          <p className="font-medium text-foreground line-clamp-1 text-sm">{item.title}</p>
          {/* 요약 1줄 */}
          <p className="text-xs text-muted-foreground line-clamp-1">{item.summary}</p>
          {/* 메타 정보 */}
          <div className="flex flex-wrap items-center gap-2 pt-1">
            {/* 경쟁사 배지 */}
            <span className="rounded-full bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)] px-2 py-0.5 text-xs font-medium">
              {item.competitorName}
            </span>
            {/* 날짜 */}
            <span className="text-xs text-muted-foreground">{formatShortDate(item.createdAt)}</span>
          </div>
        </div>
        <ExternalLink size={14} className="mt-0.5 shrink-0 text-muted-foreground" />
      </div>
    </button>
  );
}

// --- SOV 차트 ---
interface SovChartProps {
  totalArticles: number;
  shares: { competitorId: string; competitorName: string; articleCount: number; sharePercent: number }[];
}

function SovChart({ totalArticles, shares }: SovChartProps) {
  if (shares.length === 0) {
    return (
      <EmptyState
        title="집계된 SOV 데이터가 없어요"
        description="경쟁사를 추가하고 파이프라인을 실행해 보세요"
      />
    );
  }

  const chartData = shares.map((s) => ({
    name: s.competitorName,
    articleCount: s.articleCount,
  }));

  return (
    <div className="rounded-xl border border-border bg-card p-5 space-y-3">
      <div>
        <h3 className="text-sm font-semibold text-foreground">점유율 (SOV)</h3>
        <p className="text-xs text-muted-foreground mt-0.5">
          전체 {totalArticles.toLocaleString()}건
        </p>
      </div>
      <ResponsiveContainer width="100%" height={Math.max(160, shares.length * 48)}>
        <BarChart
          data={chartData}
          layout="vertical"
          margin={{ top: 4, right: 56, bottom: 4, left: 8 }}
        >
          <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="var(--border)" />
          <XAxis type="number" tick={{ fontSize: 11 }} stroke="var(--muted-foreground)" />
          <YAxis
            type="category"
            dataKey="name"
            tick={{ fontSize: 12 }}
            width={80}
            stroke="var(--muted-foreground)"
          />
          <Tooltip
            formatter={(value) => [`${Number(value).toLocaleString()}건`, "기사 수"]}
            contentStyle={{
              borderRadius: 8,
              border: "1px solid var(--border)",
              background: "var(--background)",
              color: "var(--foreground)",
              fontSize: 12,
            }}
          />
          <Bar dataKey="articleCount" fill="var(--color-primary)" radius={[0, 4, 4, 0]}>
            <LabelList
              dataKey="articleCount"
              position="right"
              formatter={(v: unknown) => `${v}건`}
              style={{ fontSize: 11, fill: "var(--muted-foreground)" }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

// --- 메인 컴포넌트 ---
export function CompetitorAnalysisTab() {
  const [days, setDays] = useState<7 | 30 | 90>(30);
  // 타임라인 경쟁사 필터 ("전체" or competitorId)
  const [selectedCompetitorId, setSelectedCompetitorId] = useState<string>("all");

  // SOV 쿼리
  const sovQuery = useQuery({
    queryKey: competitorKeys.sov({ days }),
    queryFn: () => competitorService.getSov(days),
  });

  // 타임라인 쿼리
  const timelineQuery = useQuery({
    queryKey: competitorKeys.timeline({ days }),
    queryFn: () => competitorService.getTimeline({ days }),
  });

  const isLoading = sovQuery.isLoading || timelineQuery.isLoading;
  const isError = sovQuery.isError || timelineQuery.isError;
  const isFetching = sovQuery.isFetching || timelineQuery.isFetching;

  // 에러 재시도: 에러 화면 유지 + 백그라운드 refetch
  const handleRetry = async () => {
    try {
      await Promise.all([sovQuery.refetch(), timelineQuery.refetch()]);
    } catch {
      toast.error("데이터를 불러오지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  // 로딩 스켈레톤
  if (isLoading) {
    return (
      <div className="space-y-5">
        {/* 기간 필터 스켈레톤 */}
        <div className="flex gap-2">
          {PERIOD_OPTIONS.map((o) => (
            <div key={o.days} className="h-8 w-20 rounded-md bg-muted/40 animate-pulse" />
          ))}
        </div>
        {/* SOV 차트 스켈레톤 */}
        <div className="h-52 rounded-xl bg-muted/30 border border-border animate-pulse" />
        {/* 타임라인 스켈레톤 */}
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-20 rounded-xl bg-muted/30 border border-border animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  // 에러 화면
  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <p className="text-lg font-medium text-foreground">데이터를 불러오지 못했어요</p>
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

  const sovData = sovQuery.data;
  const timelineItems = timelineQuery.data?.items ?? [];

  // 타임라인 경쟁사 칩 필터용 고유 경쟁사 목록 추출
  const competitorList = Array.from(
    new Map(timelineItems.map((item) => [item.competitorId, item.competitorName])).entries()
  ).map(([id, name]) => ({ id, name }));

  // 선택된 경쟁사로 필터링
  const filteredItems =
    selectedCompetitorId === "all"
      ? timelineItems
      : timelineItems.filter((item) => item.competitorId === selectedCompetitorId);

  return (
    <div className="space-y-6">
      {/* 기간 필터 버튼 */}
      <div className="flex gap-2">
        {PERIOD_OPTIONS.map((option) => (
          <Button
            key={option.days}
            variant={days === option.days ? "default" : "outline"}
            size="sm"
            onClick={() => {
              setDays(option.days as 7 | 30 | 90);
              // 기간이 바뀌면 경쟁사 필터 초기화
              setSelectedCompetitorId("all");
            }}
          >
            {option.label}
          </Button>
        ))}
      </div>

      {/* SOV 바 차트 */}
      {sovData ? (
        <SovChart totalArticles={sovData.totalArticles} shares={sovData.shares} />
      ) : (
        <EmptyState title="SOV 데이터가 없어요" />
      )}

      {/* 타임라인 섹션 */}
      <div className="space-y-4">
        <div>
          <h3 className="text-sm font-semibold text-foreground">기사 타임라인</h3>
          <p className="text-xs text-muted-foreground mt-0.5">
            총 {timelineItems.length.toLocaleString()}건
          </p>
        </div>

        {/* 경쟁사 칩 필터 */}
        {competitorList.length > 0 && (
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                selectedCompetitorId === "all"
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:bg-muted/70"
              }`}
              onClick={() => setSelectedCompetitorId("all")}
            >
              전체
            </button>
            {competitorList.map((c) => (
              <button
                key={c.id}
                type="button"
                className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                  selectedCompetitorId === c.id
                    ? "bg-primary text-primary-foreground"
                    : "bg-muted text-muted-foreground hover:bg-muted/70"
                }`}
                onClick={() => setSelectedCompetitorId(c.id)}
              >
                {c.name}
              </button>
            ))}
          </div>
        )}

        {/* 기사 카드 목록 */}
        {filteredItems.length === 0 ? (
          <EmptyState
            title="기사가 없어요"
            description="선택한 기간 또는 경쟁사에 수집된 기사가 없어요"
          />
        ) : (
          <div className="space-y-3">
            {filteredItems.map((item) => (
              <ArticleCard key={item.summaryId} item={item} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
