/**
 * Backend EntryError.reason → 사용자 친화 한국어 메시지.
 * 알 수 없는 코드도 한국어 fallback — 영어 기술 용어 노출 금지 (AGENTS.md §8.3.5).
 */
export function localizeReason(reason: string): string {
  switch (reason) {
    case "COMPETITOR_WATCHLIST_CONFLICT":
      return "경쟁사 워치리스트에 등록되어 있어요";
    case "INVALID_STOCK_CODE":
      return "종목코드 형식이 맞지 않아요";
    case "DUPLICATE_IN_REQUEST":
      return "중복 입력 항목이에요";
    case "RATE_LIMITED":
      return "요청이 몰려 잠시 후 다시 시도해주세요";
    case "VALIDATION_FAILED":
      return "입력값을 확인해주세요";
    default:
      return "항목을 저장할 수 없었어요";
  }
}
