import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { newsIntelligenceService } from "@/services/newsIntelligenceService";
import { newsIntelligenceKeys } from "@/queries/newsIntelligenceKeys";
import { cn } from "@/utils/cn";
import type { TopArticleItem } from "../../../shared/types/admin";

/* ── Sentiment/Importance badge classes ── */

const SENTIMENT_CLASSES: Record<string, { className: string; label: string }> = {
  POSITIVE: { className: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]", label: "\uAE0D\uC815" },
  NEUTRAL: { className: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]", label: "\uC911\uB9BD" },
  NEGATIVE: { className: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]", label: "\uBD80\uC815" },
};

function importanceBadgeClass(score: number): { className: string; label: string } | null {
  if (score >= 0.85) return { className: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]", label: "\uC8FC\uBAA9" };
  if (score >= 0.7) return { className: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]", label: "\uAD00\uC2EC" };
  return null;
}

/* ── Props ── */

interface DrilldownModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  filters: {
    days?: number;
    sentiment?: string;
    eventType?: string;
    keyword?: string;
    date?: string;
    categoryId?: string;
  };
}

/* ── Component ── */

export function DrilldownModal({ open, onClose, title, filters }: DrilldownModalProps) {
  // Escape 키로 닫기
  useEffect(() => {
    if (!open) return;
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [open, onClose]);

  const { data, isLoading, error } = useQuery({
    queryKey: newsIntelligenceKeys.topArticles({ ...filters, limit: 50 }),
    queryFn: () => newsIntelligenceService.getTopArticles({ ...filters, limit: 50 }),
    enabled: open,
  });

  const articles = data?.items ?? [];

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      {/* 모달 카드 */}
      <div
        className="bg-card rounded-[20px] max-w-[560px] w-[92vw] max-h-[min(70vh,640px)] p-0 flex flex-col shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 (sticky) */}
        <div className="flex items-center justify-between px-5 pt-[18px] pb-3.5 border-b border-border shrink-0">
          <h3 className="m-0 text-[17px] font-semibold text-foreground">{title}</h3>
          <button
            onClick={onClose}
            className="bg-transparent border-none cursor-pointer p-1 rounded-lg text-muted-foreground text-xl leading-none hover:text-foreground transition-colors"
            aria-label="\uB2EB\uAE30"
          >
            &#x2715;
          </button>
        </div>

        {/* 스크롤 영역 */}
        <div className="overflow-y-auto flex-1 min-h-0 px-4 pt-3 pb-5">
          {isLoading && (
            <div className="text-center py-12 text-muted-foreground">
              <div className="spinner mx-auto mb-3" />
              불러오는 중...
            </div>
          )}

          {!isLoading && error && (
            <div className="text-center py-12 text-destructive">
              기사를 불러오지 못했어요
            </div>
          )}

          {!isLoading && !error && articles.length === 0 && (
            <div className="text-center py-12 text-muted-foreground text-sm">
              조건에 맞는 기사가 없어요
            </div>
          )}

          {!isLoading && !error && articles.map((article) => (
            <ArticleRow key={article.summaryId} article={article} />
          ))}
        </div>
      </div>
    </div>
  );
}

/* ── ArticleRow ── */

function ArticleRow({ article }: { article: TopArticleItem }) {
  const [hovered, setHovered] = useState(false);
  const sentimentKey = article.sentiment ?? "NEUTRAL";
  const sentiment = SENTIMENT_CLASSES[sentimentKey] ?? SENTIMENT_CLASSES.NEUTRAL;
  const badge = importanceBadgeClass(article.importanceScore);
  const dateStr = article.createdAt.slice(0, 10);

  return (
    <a
      href={article.sourceLink}
      target="_blank"
      rel="noopener noreferrer"
      className={cn(
        "block px-4 py-3.5 rounded-xl mb-2 no-underline text-inherit transition-colors duration-150",
        hovered ? "bg-muted/70" : "bg-muted"
      )}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {/* 제목 */}
      <div className="text-sm font-semibold text-foreground mb-2 leading-[1.45] line-clamp-2">
        {article.title}
      </div>

      {/* 메타 라인: 날짜 + 뱃지 */}
      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="text-xs text-muted-foreground">{dateStr}</span>

        {/* 감성 뱃지 */}
        <span className={cn("text-[10px] font-semibold px-2 py-0.5 rounded-full", sentiment.className)}>
          {sentiment.label}
        </span>

        {/* 중요도 뱃지 */}
        {badge && (
          <span className={cn("text-[10px] font-semibold px-2 py-0.5 rounded-full", badge.className)}>
            {badge.label}
          </span>
        )}

        {/* 키워드 */}
        {article.keywords.slice(0, 3).map((kw) => (
          <span
            key={kw}
            className="text-[10px] px-2 py-0.5 rounded-full bg-secondary text-secondary-foreground"
          >
            {kw}
          </span>
        ))}
      </div>
    </a>
  );
}
