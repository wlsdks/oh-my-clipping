import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { newsIntelligenceService } from "@/services/newsIntelligenceService";
import { newsIntelligenceKeys } from "@/queries/newsIntelligenceKeys";
import { userFriendlyMessage } from "../../../shared/lib/httpError";
import { cn } from "@/utils/cn";
import type { BriefingItem } from "../../../shared/types/admin";

export function BriefingPanel() {
  const [selectedCategory, setSelectedCategory] = useState<string>("all");

  const { data, isLoading, error } = useQuery({
    queryKey: newsIntelligenceKeys.briefings(),
    queryFn: () => newsIntelligenceService.getBriefings(),
  });

  const briefings: BriefingItem[] = Array.isArray(data?.briefings) ? data.briefings : [];
  const categories = Array.from(new Map(briefings.map((b) => [b.categoryId, b.categoryName])));
  const filtered = selectedCategory === "all" ? briefings : briefings.filter((b) => b.categoryId === selectedCategory);

  return (
    <section className="panel">
      <div className="panel-head">
        <h3>오늘의 뉴스 브리핑</h3>
      </div>

      {/* 카테고리 필터 탭 */}
      {categories.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-4">
          <ChipButton
            active={selectedCategory === "all"}
            onClick={() => setSelectedCategory("all")}
          >
            전체
          </ChipButton>
          {categories.map(([id, name]) => (
            <ChipButton
              key={id}
              active={selectedCategory === id}
              onClick={() => setSelectedCategory(id)}
            >
              {name}
            </ChipButton>
          ))}
        </div>
      )}

      {isLoading && <p className="text-sm text-muted-foreground">불러오는 중...</p>}
      {error && (
        <p className="text-sm text-destructive">
          {userFriendlyMessage(error, "브리핑을 불러오지 못했어요")}
        </p>
      )}

      {!isLoading && !error && filtered.length === 0 && (
        <EmptyState
          title="오늘 수집된 뉴스가 아직 없어요"
          description="뉴스가 수집되면 여기에 요약이 표시돼요."
          className="bg-muted rounded-xl py-8"
        />
      )}

      {/* 전체 탭: 카테고리 + 키워드 compact 리스트 */}
      {!isLoading && !error && filtered.length > 0 && selectedCategory === "all" && (
        <div className="grid gap-1.5">
          {filtered.map((item) => (
            <button
              key={`${item.categoryId}-${item.summaryDate}`}
              type="button"
              onClick={() => setSelectedCategory(item.categoryId)}
              aria-label={`${item.categoryName} 브리핑 보기`}
              className="flex items-center gap-3 bg-muted rounded-[10px] px-4 py-3 border-none text-left transition-colors duration-150 hover:bg-muted/70 cursor-pointer"
            >
              <strong className="text-sm font-semibold text-foreground shrink-0">
                {item.categoryName}
              </strong>
              <span className="text-xs text-primary font-semibold shrink-0">
                {item.totalItems}건
              </span>
              <div className="flex-1 flex gap-1 flex-wrap min-w-0">
                {item.topicKeywords.slice(0, 4).map((kw) => (
                  <KeywordTag key={kw}>{kw}</KeywordTag>
                ))}
              </div>
              <span className="text-xs text-muted-foreground shrink-0">&rarr;</span>
            </button>
          ))}
        </div>
      )}

      {/* 개별 카테고리 탭: 풀 브리핑 카드 */}
      {!isLoading && !error && filtered.length > 0 && selectedCategory !== "all" && (
        <div className="grid gap-3">
          {filtered.map((item) => (
            <div
              key={`${item.categoryId}-${item.summaryDate}`}
              className="p-4 bg-muted rounded-xl"
            >
              <div className="flex items-center gap-2 mb-2">
                <strong className="text-sm">{item.categoryName}</strong>
                <span className="text-[11px] text-muted-foreground">{item.totalItems}건</span>
              </div>
              <p className="mb-2.5 text-[13px] leading-relaxed text-foreground/80 dark:text-foreground/70">
                {item.overallSummary}
              </p>
              {item.topicKeywords.length > 0 && (
                <div className="flex gap-1.5 flex-wrap">
                  {item.topicKeywords.map((kw) => (
                    <KeywordTag key={kw}>{kw}</KeywordTag>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

/* ── Sub-components ── */

function ChipButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "px-3.5 py-1 rounded-full border-none text-[13px] cursor-pointer transition-all duration-150",
        active
          ? "bg-primary text-primary-foreground font-semibold"
          : "bg-secondary text-muted-foreground hover:bg-secondary/70"
      )}
    >
      {children}
    </button>
  );
}

function KeywordTag({ children }: { children: React.ReactNode }) {
  return (
    <span className="px-2 py-0.5 rounded-full bg-accent text-[11px] text-accent-foreground whitespace-nowrap">
      {children}
    </span>
  );
}
