export const LEGAL_BASIS_OPTIONS = [
  { value: "QUOTATION_ONLY", label: "인용만 허용" },
  { value: "LICENSED", label: "계약으로 허용" },
  { value: "OPEN_LICENSE", label: "자유 이용 라이선스" },
  { value: "PROHIBITED", label: "사용 금지" }
] as const;

export const SOURCE_REGION_OPTIONS = [
  { value: "GLOBAL", label: "해외 소스" },
  { value: "DOMESTIC", label: "국내 소스" },
  { value: "UNKNOWN", label: "미지정 (자동 추정)" }
] as const;

const VERIFICATION_STATUS_LABEL: Record<string, string> = {
  VERIFIED: "연결 확인 완료",
  PENDING: "확인 전",
  FAILED: "확인 실패",
  BLOCKED: "접근 차단",
  UNKNOWN: "확인 전",
  FEED_ERROR: "RSS 주소/형식 확인 필요",
  ROBOTS_BLOCKED: "사이트 접근 제한(robots)",
  TIMEOUT: "응답 지연(시간 초과)",
  BLOCKED_URL: "보안 정책으로 차단된 URL"
};

export function legalBasisLabel(value: string): string {
  return LEGAL_BASIS_OPTIONS.find((item) => item.value === value)?.label || value;
}

export function verificationStatusLabel(value: string): string {
  return VERIFICATION_STATUS_LABEL[value] || value;
}

export function sourceRegionLabel(value: string): string {
  return SOURCE_REGION_OPTIONS.find((item) => item.value === value)?.label || value;
}
