import { cn } from "@/utils/cn";
import { formatRelativeDate } from "@/utils/date";
import { getCategoryColor } from "./model/categoryColors";
import type { ReviewQueueItem } from "@/types/review";

const DOT_STYLES: Record<string, string> = {
  INCLUDE: "bg-[var(--status-success-text)]",
  REVIEW: "bg-[var(--status-warning-text)]",
  EXCLUDE: "bg-[var(--status-neutral-text)]"
};

const DOT_LABELS: Record<string, string> = {
  INCLUDE: "포함 추천",
  REVIEW: "검토 필요",
  EXCLUDE: "제외 추천"
};

interface ReviewListItemProps {
  item: ReviewQueueItem;
  isSelected: boolean;
  showCategory: boolean;
  onClick: () => void;
}

export function ReviewListItem({ item, isSelected, showCategory, onClick }: ReviewListItemProps) {
  const categoryColor = getCategoryColor(item.categoryId);
  const dotStyle = DOT_STYLES[item.suggestedStatus] ?? DOT_STYLES.REVIEW;
  const dotLabel = DOT_LABELS[item.suggestedStatus] ?? "검토";

  return (
    <button
      type="button"
      role="option"
      aria-selected={isSelected}
      onClick={onClick}
      className={cn(
        "w-full text-left rounded-lg border bg-card px-3 py-2.5 transition-colors hover:bg-accent/30 relative overflow-hidden",
        isSelected && "border-primary bg-accent/30 ring-1 ring-primary/20"
      )}
    >
      {/* 카테고리 컬러 바 */}
      <div
        className="absolute left-0 top-0 bottom-0 w-1 rounded-l-xl"
        style={{ backgroundColor: categoryColor }}
      />

      <div className="pl-2">
        {/* 제목 + AI dot */}
        <div className="flex items-start gap-2">
          <span
            className={cn("mt-1.5 w-2 h-2 rounded-full shrink-0", dotStyle)}
            title={dotLabel}
            aria-label={dotLabel}
          />
          <h3 className="text-sm font-semibold text-foreground line-clamp-2 leading-snug">
            {item.title}
          </h3>
        </div>

        {/* 메타: 카테고리 · 날짜 */}
        <p className="text-xs text-muted-foreground mt-1.5 ml-4">
          {showCategory && <>{item.categoryName} · </>}
          {formatRelativeDate(item.createdAt)}
        </p>

        {/* 요약 1줄 미리보기 */}
        {item.summary && (
          <p className="text-xs text-muted-foreground mt-1 ml-4 line-clamp-1">
            {item.summary?.replace(/<[^>]*>/g, "")}
          </p>
        )}
      </div>
    </button>
  );
}
