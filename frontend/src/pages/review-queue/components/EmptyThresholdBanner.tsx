import { AlertTriangle } from "lucide-react";
import type { ReviewPolicyStatus } from "@/types/reviewPolicy";

interface EmptyThresholdBannerProps {
  categories: ReviewPolicyStatus[];
}

/**
 * 자동 승인 임계값(`autoApproveThreshold`)이 설정되지 않은 카테고리를
 * 경고 배너로 안내한다.
 *
 * - 대상(= null 카테고리)이 0건이면 **아무것도 렌더하지 않는다**.
 * - 대상 카테고리 이름은 pill 로 노출, 클릭 시 같은 페이지의 해당 카드로 스크롤.
 *   (편집은 아래 `PolicyStatusGrid` 의 `ThresholdInlineEditor` 에서 직접 수행)
 * - 설계 의도: 빠른 설정(0.5 기본값 등) 버튼은 제공하지 않는다 — 관리자가
 *   카테고리별로 의도적으로 값을 설정하도록 유도한다.
 */
export function EmptyThresholdBanner({ categories }: EmptyThresholdBannerProps) {
  // threshold 미설정 카테고리만 필터링
  const missing = categories.filter((cat) => cat.autoApproveThreshold === null);

  if (missing.length === 0) {
    return null;
  }

  // pill 클릭 시 해당 카드로 스크롤 + 포커스 이동 (키보드 사용자 친화)
  const scrollToCard = (categoryId: string) => {
    const el = document.getElementById(`policy-card-${categoryId}`);
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "center" });
      el.focus({ preventScroll: true });
    }
  };

  return (
    <div
      role="alert"
      data-testid="empty-threshold-banner"
      className="rounded-xl border p-4 bg-[var(--status-warning-bg)] border-[var(--status-warning-text)]/30 text-[var(--status-warning-text)]"
    >
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
        <div className="flex-1 space-y-2">
          <h3 className="text-sm font-semibold">임계값 설정이 필요합니다</h3>
          <p className="text-sm leading-relaxed">
            아래 카테고리는 자동 승인 임계값이 비어있어 모든 기사가 REVIEW 로 쌓입니다.
            카드의 "자동 승인 임계값" 숫자를 클릭해 값을 설정하세요.
          </p>
          <ul className="flex flex-wrap gap-2 pt-1">
            {missing.map((cat) => (
              <li key={cat.categoryId}>
                <button
                  type="button"
                  onClick={() => scrollToCard(cat.categoryId)}
                  className="inline-flex items-center rounded-full border border-[var(--status-warning-text)]/40 bg-card px-3 py-1 text-xs font-medium text-[var(--status-warning-text)] transition hover:bg-[var(--status-warning-bg)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--status-warning-text)]"
                >
                  {cat.categoryName}
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
