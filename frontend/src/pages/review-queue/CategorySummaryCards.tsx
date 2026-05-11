import { useState } from "react";
import { Button } from "@/components/ui/button";
import { ChevronDown, ChevronUp } from "lucide-react";
import type { CategorySummary } from "@/types/review";

interface CategorySummaryCardsProps {
  categories: CategorySummary[];
  onSelectCategory: (categoryId: string) => void;
}

/**
 * 카테고리별 검토 요약 카드 묶음 (단건 모드).
 * 사용자가 전체 필터 상태일 때 노출되어 카테고리 단위로 시각적 triage를 돕는다.
 * bulk 승인 버튼은 노출하지 않는다 — rubber-stamping 방지를 위해 단건 검토 흐름으로 유도.
 */
export function CategorySummaryCards({
  categories,
  onSelectCategory,
}: CategorySummaryCardsProps) {
  const [expanded, setExpanded] = useState(false);

  const activeCategories = categories.filter((c) => c.reviewCount > 0 || c.includeCount > 0);
  if (activeCategories.length === 0) return null;

  const ROW_SIZE = 4;
  const showToggle = activeCategories.length > ROW_SIZE;
  const visible = expanded ? activeCategories : activeCategories.slice(0, ROW_SIZE);

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-3">
        {visible.map((cat) => (
          <div
            key={cat.categoryId}
            className="flex min-w-[180px] flex-1 flex-col gap-2 rounded-xl border bg-card p-4"
          >
            <div className="font-medium text-sm">{cat.categoryName}</div>
            <div className="text-xs text-muted-foreground space-y-0.5">
              <div>전체 {cat.totalCount}건</div>
              <div className="text-[var(--status-success-text)]">
                AI 보내기: {cat.suggestedIncludeCount}건
              </div>
              <div className="text-[var(--status-warning-text)]">
                확인 필요: {cat.reviewCount}건
              </div>
            </div>
            <div className="mt-auto flex pt-2">
              <Button
                size="sm"
                variant="ghost"
                className="flex-1 text-xs"
                onClick={() => onSelectCategory(cat.categoryId)}
              >
                이 주제로 보기 →
              </Button>
            </div>
          </div>
        ))}
      </div>
      {showToggle && (
        <button
          type="button"
          className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
          onClick={() => setExpanded(!expanded)}
        >
          {expanded ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
          {expanded ? "접기" : `${activeCategories.length - ROW_SIZE}개 더보기`}
        </button>
      )}
    </div>
  );
}
