interface ReviewProgressHeaderProps {
  totalCount: number;
  reviewCount: number;
  includeCount: number;
  excludeCount: number;
  isLoading?: boolean;
}

/**
 * 검토 진행률 요약 헤더 (단건 모드).
 * 상단 `뉴스 검토 · counts 텍스트` 문장을 보조하는 시각적 프로그레스 바와 "남은 확인" 강조를 제공한다.
 * bulk 흐름과 무관하게 항상 의미 있는 표시 컴포넌트.
 */
export function ReviewProgressHeader({
  totalCount,
  reviewCount,
  includeCount,
  excludeCount,
  isLoading = false,
}: ReviewProgressHeaderProps) {
  if (isLoading) {
    return (
      <div className="flex items-center gap-3 px-1 py-2">
        <div className="h-4 w-48 animate-pulse rounded bg-muted" />
        <div className="h-2 flex-1 animate-pulse rounded-full bg-muted" />
      </div>
    );
  }

  const completedCount = includeCount + excludeCount;
  const progress = totalCount > 0 ? (completedCount / totalCount) * 100 : 0;
  const isComplete = reviewCount === 0 && totalCount > 0;

  return (
    <div className="flex flex-col gap-2 px-1 py-2">
      <div className="flex items-center justify-between text-xs">
        <span className="font-medium text-foreground">
          {isComplete ? "모든 검토 완료" : "오늘의 검토"}
        </span>
        <div className="flex items-center gap-3 text-muted-foreground">
          <span>
            검토 완료 {completedCount}/{totalCount}건
          </span>
          {reviewCount > 0 && (
            <span className="font-medium text-[var(--status-warning-text)]">
              남은 확인: {reviewCount}건
            </span>
          )}
        </div>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-primary transition-all duration-300"
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  );
}
