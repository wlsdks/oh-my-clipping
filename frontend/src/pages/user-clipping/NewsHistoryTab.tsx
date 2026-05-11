import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userHistoryKeys } from "@/queries/userHistoryKeys";
import { userHistoryService } from "@/services/userHistoryService";
import type { ArticleHistoryItem } from "@/types/insight";
import { formatKoreanDateTime } from "@/utils/date";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState } from "@/components/shared/EmptyState";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { ArticleDetailModal } from "@/components/shared/ArticleDetailModal";

interface UserCategory {
  id: string;
  name: string;
}

function articleDateKey(iso: string): string {
  return iso.slice(0, 10);
}

function articleDateLabel(dateStr: string): string {
  const todayStr = new Date().toISOString().slice(0, 10);
  const yesterdayStr = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
  if (dateStr === todayStr) return "오늘";
  if (dateStr === yesterdayStr) return "어제";
  const d = new Date(dateStr);
  return d.toLocaleDateString("ko-KR", { month: "long", day: "numeric" });
}

function groupArticlesByDate(
  items: ArticleHistoryItem[]
): { date: string; label: string; items: ArticleHistoryItem[] }[] {
  const map = new Map<string, ArticleHistoryItem[]>();
  for (const item of items) {
    const key = articleDateKey(item.createdAt);
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(item);
  }
  return Array.from(map.entries()).map(([date, groupItems]) => ({
    date,
    label: articleDateLabel(date),
    items: groupItems
  }));
}

export function NewsHistoryTab({ userCategories }: { userCategories: UserCategory[] }) {
  const qc = useQueryClient();
  const [keyword, setKeyword] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [bookmarkedOnly, setBookmarkedOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [selectedArticleId, setSelectedArticleId] = useState<string | null>(null);

  const categories = userCategories;

  const { data: historyPage, isLoading } = useQuery({
    queryKey: userHistoryKeys.articles({ keyword, categoryId, bookmarkedOnly, page }),
    queryFn: () =>
      userHistoryService.searchArticleHistory({
        keyword: keyword || undefined,
        categoryId: categoryId || undefined,
        bookmarkedOnly: bookmarkedOnly || undefined,
        page,
        size: 20
      })
  });

  const { mutate: toggleBookmark } = useMutation({
    mutationFn: (summaryId: string) => userHistoryService.toggleArticleBookmark(summaryId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: userHistoryKeys.all });
      toast.success("북마크를 변경했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "북마크에 실패했어요"))
  });

  const items: ArticleHistoryItem[] = historyPage?.items ?? [];
  const totalPages = historyPage?.totalPages ?? 0;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex-1 min-w-48">
          <Input
            placeholder="키워드 검색"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <Select
          value={categoryId || "ALL"}
          onValueChange={(v) => {
            setCategoryId(v === "ALL" ? "" : v);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-44">
            <SelectValue placeholder="전체 주제" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">전체 주제</SelectItem>
            {categories.map((c) => (
              <SelectItem key={c.id} value={c.id}>
                {c.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          variant={bookmarkedOnly ? "default" : "outline"}
          size="sm"
          onClick={() => {
            setBookmarkedOnly(!bookmarkedOnly);
            setPage(0);
          }}
        >
          북마크만
        </Button>
      </div>

      {isLoading ? (
        <div className="py-8 text-center text-sm text-muted-foreground">불러오는 중...</div>
      ) : items.length === 0 ? (
        <EmptyState title="뉴스 수신 이력이 없어요" description="뉴스가 발송되면 여기서 확인할 수 있어요" />
      ) : (
        <div className="space-y-4">
          {groupArticlesByDate(items).map((group) => (
            <div key={group.date}>
              <p className="text-xs font-semibold text-muted-foreground mb-2 px-1">{group.label}</p>
              <div className="rounded-md border divide-y">
                {group.items.map((item) => (
                  <div key={item.id} className="p-4 space-y-1">
                    <div className="flex items-start justify-between gap-2">
                      <button
                        type="button"
                        className="text-sm font-medium text-left hover:underline line-clamp-2"
                        onClick={() => setSelectedArticleId(item.id)}
                      >
                        {item.title}
                      </button>
                      <button
                        type="button"
                        onClick={() => toggleBookmark(item.id)}
                        className={`shrink-0 text-sm ${item.isBookmarked ? "text-[var(--status-warning-text)]" : "text-muted-foreground"}`}
                        aria-label="북마크 토글"
                      >
                        {item.isBookmarked ? "\u2605" : "\u2606"}
                      </button>
                    </div>
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <span>{item.categoryName}</span>
                      <span>·</span>
                      <span>{formatKoreanDateTime(item.createdAt)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
            이전
          </Button>
          <span className="text-sm text-muted-foreground">
            {page + 1} / {totalPages}
          </span>
          <Button variant="outline" size="sm" onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages - 1}>
            다음
          </Button>
        </div>
      )}

      <ArticleDetailModal
        variant="subscription"
        articleId={selectedArticleId}
        onClose={() => setSelectedArticleId(null)}
      />
    </div>
  );
}
