export type ReviewStatusFilter = "ALL" | "INCLUDE" | "REVIEW" | "EXCLUDE";

/**
 * 프론트에서 강제하는 일괄 처리 상한.
 * 백엔드는 1~100을 허용하지만(`BulkRequest.validate`), UX/안전 가드레일로 20건으로 제한한다.
 * - 20건 이상은 rubber-stamping 위험이 급격히 증가
 * - 이전 PR #185 크래시와 동일한 대량 액션 부작용 차단
 * - 샘플 확인 카드가 효과적으로 보조할 수 있는 상한
 */
export const MAX_BULK_SELECT = 20;

/**
 * AI 제안 신뢰도 임계값. 이 값 미만은 `BulkApproveDialog`에서 경고 배너를 띄운다.
 * `importanceScore`는 0.0~1.0 범위이며, 0.7을 고신뢰 기준으로 사용한다.
 */
export const HIGH_CONFIDENCE_SCORE = 0.7;

/**
 * 벌크 실패 코드 → 한국어 사용자 메시지.
 * 백엔드 `BulkActionFailure.code`와 1:1 매핑.
 */
export const BULK_FAILURE_MESSAGES: Record<string, string> = {
  NOT_FOUND: "삭제된 항목이에요",
  ALREADY_PROCESSED: "이미 다른 사람이 처리했어요",
  INVALID_STATUS: "상태 정보가 잘못됐어요",
  UNKNOWN: "처리에 실패했어요"
};

export function bulkFailureMessage(code: string): string {
  return BULK_FAILURE_MESSAGES[code] ?? BULK_FAILURE_MESSAGES.UNKNOWN;
}

export const STATUS_LABEL: Record<string, string> = {
  REVIEW: "확인 필요",
  INCLUDE: "보내기",
  EXCLUDE: "건너뛰기"
};

export const STATUS_FILTER_OPTIONS = [
  { value: "REVIEW", label: "확인 필요" },
  { value: "INCLUDE", label: "보내기" },
  { value: "EXCLUDE", label: "건너뛰기" },
  { value: "ALL", label: "전체" }
] as const;

export type ImportanceLevel = "high" | "medium" | "low";

export function getImportanceLevel(score: number): ImportanceLevel {
  if (score >= 0.7) return "high";
  if (score >= 0.4) return "medium";
  return "low";
}

export const IMPORTANCE_STYLES: Record<ImportanceLevel, string> = {
  high: "bg-[var(--accent-primary)]",
  medium: "bg-[var(--text-tertiary)]",
  low: "bg-[var(--border-default)]"
};

type ButtonVariant = "default" | "outline" | "ghost";

interface ActionButton {
  label: string;
  action: "approve" | "exclude" | "review";
  variant: ButtonVariant;
  title: string;
}

export function getActionButtons(currentStatus: "INCLUDE" | "REVIEW" | "EXCLUDE") {
  switch (currentStatus) {
    case "REVIEW":
      return [
        {
          label: "건너뛰기",
          action: "exclude" as const,
          variant: "outline" as const,
          title: "이 기사를 발송 대상에서 제외합니다"
        },
        {
          label: "보내기",
          action: "approve" as const,
          variant: "default" as const,
          title: "다음 예정 발송에 포함돼요 (즉시 발송 아님)"
        }
      ];
    case "INCLUDE":
      return [
        {
          label: "재검토",
          action: "review" as const,
          variant: "outline" as const,
          title: "검토 상태로 되돌립니다"
        },
        {
          label: "건너뛰기",
          action: "exclude" as const,
          variant: "outline" as const,
          title: "이 기사를 발송 대상에서 제외합니다"
        }
      ];
    case "EXCLUDE":
      return [
        {
          label: "재검토",
          action: "review" as const,
          variant: "outline" as const,
          title: "검토 상태로 되돌립니다"
        },
        {
          label: "보내기",
          action: "approve" as const,
          variant: "default" as const,
          title: "다음 예정 발송에 포함돼요 (즉시 발송 아님)"
        }
      ];
  }
}

/**
 * AI 추천에 따라 버튼 강조를 조정한다.
 * REVIEW 상태에서만 AI 가중치 적용. 다른 상태는 기본 버튼 반환.
 */
export function getWeightedActionButtons(
  currentStatus: "INCLUDE" | "REVIEW" | "EXCLUDE",
  suggestedStatus: "INCLUDE" | "REVIEW" | "EXCLUDE"
): ActionButton[] {
  const base = getActionButtons(currentStatus);
  if (currentStatus !== "REVIEW") return base;

  if (suggestedStatus === "INCLUDE") {
    return base.map((btn) => ({
      ...btn,
      variant: btn.action === "approve" ? ("default" as const) : ("ghost" as const)
    }));
  }

  if (suggestedStatus === "EXCLUDE") {
    return base.map((btn) => ({
      ...btn,
      variant: btn.action === "exclude" ? ("default" as const) : ("ghost" as const)
    }));
  }

  return base;
}
