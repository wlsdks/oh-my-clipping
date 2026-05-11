import type { ArticleRankItem } from "@/services/analyticsService";

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

interface ArticleRankingListProps {
  items: ArticleRankItem[];
  loading?: boolean;
}

export function ArticleRankingList({ items, loading }: ArticleRankingListProps) {
  if (loading) {
    return (
      <div className="space-y-3">
        {[1, 2, 3, 4, 5].map((i) => (
          <div
            key={i}
            className="animate-pulse rounded-xl border border-border h-20 bg-muted/30"
          />
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <p className="text-sm text-muted-foreground text-center py-8">
        표시할 기사 데이터가 없어요
      </p>
    );
  }

  return (
    <div className="space-y-3">
      {items.map((item) => (
        <div
          key={item.summaryId}
          className="flex items-start gap-3 rounded-xl border border-border bg-card p-4"
        >
          <span
            className={`shrink-0 text-lg font-bold w-8 text-center ${
              item.rank <= 3
                ? "text-foreground/70"
                : "text-muted-foreground"
            }`}
          >
            {item.rank}
          </span>
          <div className="min-w-0 flex-1 space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              {item.categoryName && (
                <span className="text-xs bg-muted text-foreground/70 border border-border rounded-full px-2 py-0.5">
                  {item.categoryName}
                </span>
              )}
              <p className="text-sm font-medium truncate">
                {item.title ?? "(제목 없음)"}
              </p>
            </div>
            <p className="text-xs text-muted-foreground">
              {[
                item.sourceName,
                item.publishedAt ? formatDate(item.publishedAt) : null,
              ]
                .filter(Boolean)
                .join(" · ")}
            </p>
            <div className="flex gap-3 text-xs text-muted-foreground">
              <span>클릭 {item.clicks.toLocaleString()}</span>
              <span>클릭률 {item.ctr.toFixed(1)}%</span>
              <span>북마크 {item.bookmarks.toLocaleString()}</span>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
