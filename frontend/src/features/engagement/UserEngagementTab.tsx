import { useQuery } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Link } from "react-router-dom";
import { InfoTooltip } from "@/components/shared/InfoTooltip";
import { analyticsKeys } from "@/queries/analyticsKeys";
import { analyticsService } from "@/services/analyticsService";
import { Button } from "@/components/ui/button";
import { ArticleRankingList } from "./ui/ArticleRankingList";
import { CategoryCtrDonut } from "./ui/CategoryCtrDonut";
import { HotFeedbackList } from "./ui/HotFeedbackList";
import { WizardFunnel } from "./ui/WizardFunnel";

interface UserEngagementTabProps {
  categoryId?: string;
  from: string;
  to: string;
  days: number;
}

export function UserEngagementTab({
  categoryId,
  from,
  to,
  days,
}: UserEngagementTabProps) {
  // 기사 랭킹
  const rankingQuery = useQuery({
    queryKey: analyticsKeys.articleRanking(from, to, "clicks", 10),
    queryFn: () => analyticsService.getArticleRanking(from, to, "clicks", 10),
  });

  // 카테고리 통계
  const categoryQuery = useQuery({
    queryKey: analyticsKeys.categoryStats(from, to),
    queryFn: () => analyticsService.getCategoryStats(from, to),
  });

  // 인기 피드백
  const feedbackQuery = useQuery({
    queryKey: analyticsKeys.hotFeedback({ categoryId, limit: 10, days }),
    queryFn: () =>
      analyticsService.getHotFeedback({ categoryId, limit: 10, days }),
  });

  const isLoading =
    rankingQuery.isLoading ||
    categoryQuery.isLoading ||
    feedbackQuery.isLoading;

  const hasError =
    rankingQuery.isError ||
    categoryQuery.isError ||
    feedbackQuery.isError;

  // 에러 재시도
  const handleRetry = async () => {
    try {
      await Promise.all([
        rankingQuery.refetch(),
        categoryQuery.refetch(),
        feedbackQuery.refetch(),
      ]);
    } catch {
      toast.error("데이터를 불러오지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  // 에러 화면
  if (hasError && !isLoading) {
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
          disabled={
            rankingQuery.isFetching ||
            categoryQuery.isFetching ||
            feedbackQuery.isFetching
          }
        >
          <RefreshCw
            size={14}
            className={
              rankingQuery.isFetching ||
              categoryQuery.isFetching ||
              feedbackQuery.isFetching
                ? "animate-spin"
                : ""
            }
          />
          {rankingQuery.isFetching ||
          categoryQuery.isFetching ||
          feedbackQuery.isFetching
            ? "시도 중..."
            : "다시 시도"}
        </Button>
      </div>
    );
  }

  const ranking = rankingQuery.data?.data ?? [];
  const categoryStats = categoryQuery.data?.data ?? [];

  return (
    <div className="space-y-6">
      {/* 기사 랭킹 + CTR 도넛: 2열 */}
      <div className="grid grid-cols-1 md:grid-cols-5 gap-6">
        <div className="md:col-span-3">
          <div className="flex items-center gap-1.5 mb-3">
            <h3 className="text-sm font-semibold">인기 기사 Top 10</h3>
            <InfoTooltip
              ariaLabel="인기 기사 설명"
              content="실제 독자 클릭 수 순위입니다. '콘텐츠 인사이트' 탭의 주요 기사(AI 중요도 기반)와 다릅니다."
            />
          </div>
          <ArticleRankingList
            items={ranking}
            loading={rankingQuery.isLoading}
          />
        </div>
        <div className="md:col-span-2">
          <div className="flex items-center gap-1.5 mb-3">
            <h3 className="text-sm font-semibold">카테고리별 클릭 분포</h3>
            <InfoTooltip
              ariaLabel="카테고리별 CTR 설명"
              content="카테고리별 (클릭 수 / 발송 수) 비율입니다."
            />
          </div>
          <CategoryCtrDonut
            stats={categoryStats}
            loading={categoryQuery.isLoading}
          />
        </div>
      </div>

      {/* 인기 피드백 */}
      <div className="rounded-xl border border-border bg-card p-4">
        <div className="flex items-center gap-1.5 mb-3">
          <h3 className="text-sm font-semibold">인기 피드백</h3>
          <InfoTooltip
            ariaLabel="인기 피드백 설명"
            content="👍/👎 반응이 많았던 기사 또는 채널 순위입니다."
          />
        </div>
        <HotFeedbackList
          result={feedbackQuery.data}
          loading={feedbackQuery.isLoading}
        />
      </div>

      {/* 온보딩 퍼널 (자체 fetch) */}
      <div className="rounded-xl border border-border bg-card p-4">
        <h3 className="text-sm font-semibold mb-3">온보딩 퍼널</h3>
        <WizardFunnel days={days} />
      </div>

      {/* 요약 스타일 통계 — 별도 페이지로 이동 */}
      <div className="rounded-xl border border-border bg-card p-4 flex items-center gap-2">
        <p className="text-sm text-muted-foreground">
          요약스타일 사용 현황은
        </p>
        <Link
          to="/admin/personas?tab=stats"
          className="text-sm font-medium text-primary underline underline-offset-2 hover:opacity-80"
        >
          요약스타일 &gt; 사용 통계 탭
        </Link>
        <p className="text-sm text-muted-foreground">에서 확인하세요.</p>
      </div>
    </div>
  );
}
