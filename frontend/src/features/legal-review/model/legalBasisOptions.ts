export type LegalBasis = "QUOTATION_ONLY" | "OPEN_LICENSE" | "LICENSED" | "PROHIBITED";

export interface LegalBasisOption {
  value: LegalBasis;
  label: string;
  badge?: string;
  description: string;
  defaultPolicy: {
    summaryAllowed: boolean;
    fulltextAllowed: boolean;
  };
}

/**
 * 법적 근거 옵션 목록.
 * 권장 옵션은 첫 번째 항목으로 둔다 (UI에서 기본 선택).
 */
export const LEGAL_BASIS_OPTIONS: readonly LegalBasisOption[] = [
  {
    value: "QUOTATION_ONLY",
    label: "인용만",
    badge: "권장",
    description: "대부분의 뉴스 사이트의 최소 허용 범위",
    defaultPolicy: { summaryAllowed: true, fulltextAllowed: false },
  },
  {
    value: "OPEN_LICENSE",
    label: "오픈 라이선스",
    description: "CC, Public Domain 등 자유 사용 가능",
    defaultPolicy: { summaryAllowed: true, fulltextAllowed: true },
  },
  {
    value: "LICENSED",
    label: "라이선스 계약",
    description: "사이트와 계약/협업 관계가 있는 경우",
    defaultPolicy: { summaryAllowed: true, fulltextAllowed: true },
  },
  {
    value: "PROHIBITED",
    label: "사용 금지",
    description: "저작권 사유로 수집·요약이 금지된 소스",
    defaultPolicy: { summaryAllowed: false, fulltextAllowed: false },
  },
] as const;

/** value로 옵션 찾기 */
export function findLegalBasisOption(value: LegalBasis): LegalBasisOption {
  const option = LEGAL_BASIS_OPTIONS.find((o) => o.value === value);
  if (!option) throw new Error(`Unknown legal basis: ${value}`);
  return option;
}
