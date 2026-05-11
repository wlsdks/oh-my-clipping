import type { Source } from "@/types/source";

/**
 * 수집 건강 상태 뱃지를 `crawlFailCount` 기반으로 결정한다.
 * `lastSuccessAt` 이 null 이면 아직 한 번도 수집한 적 없는 신규 소스.
 */
export function healthBadge(source: Source) {
  // 수집 이력 없는 신규 소스
  if (!source.lastSuccessAt && source.crawlFailCount === 0) {
    return {
      label: "수집 대기중",
      className: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]"
    };
  }
  if (source.crawlFailCount === 0) {
    return {
      label: "정상",
      className: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]"
    };
  }
  if (source.crawlFailCount < 10) {
    return {
      label: "주의",
      className: "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]"
    };
  }
  return {
    label: "오류",
    className: "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]"
  };
}

/** 신뢰도 점수 구간에 대응하는 시맨틱 텍스트 색상 클래스를 돌려준다. */
export function reliabilityScoreColor(score: number): string {
  if (score >= 80) return "text-[var(--status-success-text)]";
  if (score >= 50) return "text-[var(--status-warning-text)]";
  return "text-[var(--status-danger-text)]";
}

/** 저작권 검토일이 없거나 180일 이상 경과했는지 판별한다. */
export function isComplianceExpired(termsReviewedAt: string | null | undefined): boolean {
  if (!termsReviewedAt) return true;
  const reviewed = new Date(termsReviewedAt);
  const sixMonthsAgo = new Date();
  sixMonthsAgo.setDate(sixMonthsAgo.getDate() - 180);
  return reviewed < sixMonthsAgo;
}
