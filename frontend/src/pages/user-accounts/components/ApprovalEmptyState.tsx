import { CheckCircle2, FileX, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/shared/EmptyState";

/**
 * 승인 탭에서 표시되는 빈/검색 실패 상태를 책임지는 컴포넌트다.
 *
 * - `search` 가 있으면 검색 결과 없음 안내와 초기화 버튼을 노출한다.
 * - 그 외에는 현재 필터(PENDING/REJECTED)에 맞는 빈 상태 문구를 보여준다.
 * - 접근성: 컨테이너에 `role="status"` 와 `aria-live="polite"` 를 부여해
 *   스크린리더가 상태 변화를 읽을 수 있게 한다.
 */
interface ApprovalEmptyStateProps {
  search: string;
  isPending: boolean;
  onClearSearch: () => void;
}

export function ApprovalEmptyState({
  search,
  isPending,
  onClearSearch,
}: ApprovalEmptyStateProps) {
  // 검색 결과가 없을 때의 안내가 최우선
  if (search) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex flex-col items-center justify-center py-16 text-center"
      >
        <Search
          size={40}
          className="text-muted-foreground/50 mb-3"
          aria-hidden="true"
        />
        <EmptyState
          title={`'${search}'에 대한 결과가 없어요`}
          action={
            <Button variant="outline" size="sm" onClick={onClearSearch}>
              검색 초기화
            </Button>
          }
          className="py-0"
        />
      </div>
    );
  }

  // PENDING 빈 상태 — 승인 대기 없음
  if (isPending) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex flex-col items-center justify-center py-16 text-center"
      >
        <CheckCircle2
          size={40}
          className="text-muted-foreground/50 mb-3"
          aria-hidden="true"
        />
        <EmptyState
          title="모든 가입 신청을 처리했어요"
          description="새로운 신청이 들어오면 여기에 표시돼요"
          className="py-0"
        />
      </div>
    );
  }

  // REJECTED 빈 상태
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col items-center justify-center py-16 text-center"
    >
      <FileX
        size={40}
        className="text-muted-foreground/50 mb-3"
        aria-hidden="true"
      />
      <EmptyState title="반려된 신청이 없어요" className="py-0" />
    </div>
  );
}
