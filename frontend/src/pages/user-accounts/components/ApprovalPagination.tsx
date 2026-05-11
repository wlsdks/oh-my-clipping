import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * 승인 탭 리스트 하단의 요약 카운트와 페이지네이션 컨트롤을 담당한다.
 *
 * - 검색 상태에 따라 카운트 문구를 분기한다.
 * - `totalPages` 가 2 이상일 때만 이전/다음 버튼을 노출한다.
 * - 접근성: 컨트롤을 `<nav aria-label="페이지네이션">` 으로 감싸고
 *   현재 페이지 표시에 `aria-current="page"` 와 `role="status"` 를 부여한다.
 */
interface ApprovalPaginationProps {
  search: string;
  filteredCount: number;
  totalCount: number;
  currentPage: number;
  totalPages: number;
  onPrev: () => void;
  onNext: () => void;
}

export function ApprovalPagination({
  search,
  filteredCount,
  totalCount,
  currentPage,
  totalPages,
  onPrev,
  onNext,
}: ApprovalPaginationProps) {
  const hasMultiplePages = totalPages > 1;

  return (
    <div className="flex items-center justify-between">
      <span
        className="text-sm text-muted-foreground"
        role="status"
        aria-live="polite"
      >
        {search
          ? `검색 결과 ${filteredCount}건 / 전체 ${totalCount}건`
          : `총 ${totalCount}건`}
      </span>
      {hasMultiplePages && (
        <nav
          className="flex items-center gap-1"
          aria-label="페이지네이션"
        >
          <Button
            variant="outline"
            size="sm"
            onClick={onPrev}
            disabled={currentPage === 0}
            aria-label="이전 페이지"
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            이전
          </Button>
          <span
            className="text-sm text-muted-foreground px-1"
            aria-current="page"
          >
            {currentPage + 1} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={onNext}
            disabled={currentPage >= totalPages - 1}
            aria-label="다음 페이지"
          >
            다음
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </Button>
        </nav>
      )}
    </div>
  );
}
