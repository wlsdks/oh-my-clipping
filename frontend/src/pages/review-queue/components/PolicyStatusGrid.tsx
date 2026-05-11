import { cn } from "@/utils/cn";
import type { ReviewPolicyStatus } from "@/types/reviewPolicy";
import { ThresholdInlineEditor } from "./ThresholdInlineEditor";

interface PolicyStatusGridProps {
  categories: ReviewPolicyStatus[];
  onCategoryClick: (categoryId: string) => void;
  onThresholdChange: (categoryId: string, value: number | null) => Promise<void>;
}

// 검토 대기 건수가 이 값을 초과하면 warning 색으로 강조한다.
const PENDING_WARNING_THRESHOLD = 50;

/**
 * 카테고리별 리뷰 정책 현황 카드 그리드.
 *
 * - threshold 미설정 카테고리는 "임계값 미설정" danger pill 노출
 * - pendingReviewCount > {@link PENDING_WARNING_THRESHOLD} 이면 warning 색 강조
 * - 카드 전체가 클릭 가능 — 클릭 시 onCategoryClick(categoryId) 호출
 * - 카드 하단에 `ThresholdInlineEditor` 로 자동 승인 임계값을 인라인 편집
 *   (편집 영역은 stopPropagation 으로 카드 클릭과 분리)
 */
export function PolicyStatusGrid({
  categories,
  onCategoryClick,
  onThresholdChange,
}: PolicyStatusGridProps) {
  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
      {categories.map((cat) => {
        const thresholdMissing = cat.autoApproveThreshold === null;
        const pendingHigh = cat.pendingReviewCount > PENDING_WARNING_THRESHOLD;

        const handleCardClick = () => onCategoryClick(cat.categoryId);
        const handleCardKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            handleCardClick();
          }
        };

        return (
          <div
            key={cat.categoryId}
            id={`policy-card-${cat.categoryId}`}
            role="button"
            tabIndex={0}
            onClick={handleCardClick}
            onKeyDown={handleCardKeyDown}
            data-testid="category-card"
            className="rounded-2xl border bg-card p-4 text-left transition hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary cursor-pointer"
          >
            {/* 상단: 카테고리명 + 임계값 미설정 pill */}
            <div className="flex items-start justify-between gap-2">
              {/* 카테고리명은 풀네임 노출 — 말줄임 금지 (내부 관리자 툴, §8.3 규칙 9) */}
              <span className="text-sm font-medium text-foreground break-keep">
                {cat.categoryName}
              </span>
              {thresholdMissing && (
                <span className="shrink-0 rounded-full bg-[var(--status-danger-bg)] px-2 py-0.5 text-[11px] font-medium text-[var(--status-danger-text)]">
                  임계값 미설정
                </span>
              )}
            </div>

            {/* 중앙: pending 대기 건수 (큰 숫자) */}
            <div className="mt-3 flex items-baseline gap-2">
              <span
                className={cn(
                  "text-2xl font-semibold tabular-nums",
                  pendingHigh ? "text-[var(--status-warning-text)]" : "text-foreground",
                )}
              >
                {cat.pendingReviewCount.toLocaleString()}
              </span>
              <span className="text-xs text-muted-foreground">검토 대기</span>
            </div>

            {/* 하단: 최근 7일 + 자동 승인 임계값 인라인 편집 */}
            <div className="mt-3 space-y-1 text-xs text-muted-foreground">
              <div>최근 7일: {cat.last7DaysProcessed.toLocaleString()}건</div>
              {/*
                인라인 편집 영역은 카드 클릭(필터 전환)과 분리되어야 한다.
                pointerDown/click 을 stopPropagation 해 editor 상호작용이 카드 onClick 을 트리거하지 않게 한다.
              */}
              <div
                className="flex items-center gap-1"
                onClick={(e) => e.stopPropagation()}
                onKeyDown={(e) => e.stopPropagation()}
                onPointerDown={(e) => e.stopPropagation()}
              >
                <span>자동 승인 임계값:</span>
                <ThresholdInlineEditor
                  categoryId={cat.categoryId}
                  currentValue={cat.autoApproveThreshold}
                  onSave={(value) => onThresholdChange(cat.categoryId, value)}
                />
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
