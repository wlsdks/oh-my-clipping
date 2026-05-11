import { useState, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Bookmark, BookmarkCheck, Link as LinkIcon, ExternalLink } from "lucide-react";
import { userHistoryKeys } from "@/queries/userHistoryKeys";
import { newsReportKeys } from "@/queries/newsReportKeys";
import { userHistoryService } from "@/services/userHistoryService";
import { userIntelligenceService } from "@/services/userIntelligenceService";
import { formatKoreanDateTime } from "@/utils/date";
import { getEventTypeLabel } from "@/utils/eventTypeLabel";
import { tracker } from "@/shared/lib/tracker";
import type { ArticleDetail } from "@/types/insight";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface ArticleDetailModalProps {
  variant: "subscription" | "competitor";
  articleId: string | null;
  initialTitle?: string;
  onClose: () => void;
}

export function ArticleDetailModal({
  variant,
  articleId,
  initialTitle,
  onClose,
}: ArticleDetailModalProps) {
  const [expanded, setExpanded] = useState(false);
  const qc = useQueryClient();
  const isOpen = articleId !== null;

  // 구독뉴스 상세 조회
  const subscriptionQuery = useQuery({
    queryKey: userHistoryKeys.detail(articleId!),
    queryFn: () => userHistoryService.getArticleDetail(articleId!),
    enabled: isOpen && variant === "subscription",
  });

  // 경쟁사 뉴스 상세 조회
  const competitorQuery = useQuery({
    queryKey: newsReportKeys.competitorArticleDetail(articleId!),
    queryFn: () => userIntelligenceService.getCompetitorArticleDetail(articleId!),
    enabled: isOpen && variant === "competitor",
  });

  const query = variant === "subscription" ? subscriptionQuery : competitorQuery;
  const article = query.data;
  const isLoading = query.isLoading;

  // 모달 열린 시점에 노출 이벤트 트래킹
  useEffect(() => {
    if (isOpen && articleId) {
      tracker.track("article_impression", {
        summaryId: articleId,
        variant,
        source: "web_modal",
      });
    }
  }, [isOpen, articleId, variant]);

  // 북마크 토글
  const bookmarkMutation = useMutation({
    mutationFn: () => userHistoryService.toggleArticleBookmark(articleId!),
    onSuccess: (result) => {
      qc.setQueryData<ArticleDetail>(userHistoryKeys.detail(articleId!), (prev) =>
        prev ? { ...prev, isBookmarked: result.isBookmarked } : prev
      );
      qc.setQueryData<ArticleDetail>(
        newsReportKeys.competitorArticleDetail(articleId!),
        (prev) => (prev ? { ...prev, isBookmarked: result.isBookmarked } : prev)
      );
      toast.success(result.isBookmarked ? "북마크에 저장했어요" : "북마크를 해제했어요");
    },
  });

  // 모달 닫기
  const handleOpenChange = (open: boolean) => {
    if (!open) {
      setExpanded(false);
      onClose();
    }
  };

  // 원문 보기 클릭 트래킹
  const handleViewOriginal = () => {
    if (article?.sourceLink) {
      tracker.track("article_click", {
        summaryId: articleId,
        variant,
        source: "web_modal",
      });
      window.open(article.sourceLink, "_blank", "noreferrer");
    }
  };

  // 링크 복사
  const handleCopyLink = async () => {
    if (article?.sourceLink) {
      await navigator.clipboard.writeText(article.sourceLink);
      toast.success("링크가 복사됐어요");
    }
  };

  // 메타 라인 라벨
  const contextLabel =
    variant === "subscription" ? article?.categoryName : (article?.competitorName ?? null);
  const eventLabel = article?.eventType ? getEventTypeLabel(article.eventType) : null;

  return (
    <Dialog open={isOpen} onOpenChange={handleOpenChange}>
      <DialogContent className="flex flex-col max-w-2xl max-h-[80vh] p-0">
        <DialogDescription className="sr-only">기사 상세 정보를 확인합니다</DialogDescription>

        <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-4">
          {/* 제목 + 액션 아이콘 */}
          <DialogHeader className="flex-row items-start justify-between gap-3">
            <DialogTitle className="text-lg font-semibold leading-snug flex-1">
              {isLoading ? (initialTitle ?? "불러오는 중...") : article?.title}
            </DialogTitle>
            {article && (
              <div className="flex items-center gap-1 shrink-0">
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8"
                  onClick={() => bookmarkMutation.mutate()}
                  disabled={bookmarkMutation.isPending}
                  aria-label={article.isBookmarked ? "북마크 해제" : "북마크 저장"}
                >
                  {article.isBookmarked ? (
                    <BookmarkCheck className="h-4 w-4 text-primary" />
                  ) : (
                    <Bookmark className="h-4 w-4" />
                  )}
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8"
                  onClick={handleCopyLink}
                  aria-label="링크 복사"
                >
                  <LinkIcon className="h-4 w-4" />
                </Button>
              </div>
            )}
          </DialogHeader>

          {/* 메타 + 콘텐츠 */}
          {article && (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                {contextLabel && <span>{contextLabel}</span>}
                {contextLabel && <span>·</span>}
                <span>{formatKoreanDateTime(article.createdAt)}</span>
              </div>

              {variant === "competitor" && eventLabel && (
                <Badge variant="outline" className="text-xs">
                  {eventLabel}
                </Badge>
              )}

              <Button
                variant="outline"
                size="sm"
                className="text-primary"
                onClick={handleViewOriginal}
              >
                <ExternalLink className="h-3.5 w-3.5 mr-1.5" />
                원문 보기
              </Button>

              <div className="border-t" />

              {article.summary && (
                <div>
                  <p className={`text-sm leading-relaxed ${!expanded ? "line-clamp-3" : ""}`}>
                    {article.summary}
                  </p>
                  {article.summary.length > 150 && !expanded && (
                    <button
                      type="button"
                      className="text-xs text-primary mt-1 hover:underline"
                      onClick={() => setExpanded(true)}
                    >
                      더 보기
                    </button>
                  )}
                </div>
              )}

              {article.keywords.length > 0 && (
                <div className="flex flex-wrap gap-1">
                  {article.keywords.map((kw, i) => (
                    <Badge key={i} variant="outline" className="text-xs">
                      {kw}
                    </Badge>
                  ))}
                </div>
              )}

              {article.insights && (
                <div className="rounded-lg bg-muted/50 p-4">
                  <p className="text-xs font-medium text-muted-foreground mb-1.5">인사이트</p>
                  <p className="text-sm leading-relaxed">{article.insights}</p>
                </div>
              )}
            </div>
          )}

          {/* 로딩 스켈레톤 */}
          {isLoading && (
            <div className="space-y-3 animate-pulse">
              <div className="h-3 bg-muted rounded w-1/3" />
              <div className="h-8 bg-muted rounded w-24" />
              <div className="border-t" />
              <div className="space-y-2">
                <div className="h-3 bg-muted rounded w-full" />
                <div className="h-3 bg-muted rounded w-full" />
                <div className="h-3 bg-muted rounded w-2/3" />
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
