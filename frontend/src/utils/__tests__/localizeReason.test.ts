import { describe, it, expect } from "vitest";
import { localizeReason } from "../localizeReason";

describe("localizeReason", () => {
  it("COMPETITOR_WATCHLIST_CONFLICT → 경쟁사 안내", () => {
    expect(localizeReason("COMPETITOR_WATCHLIST_CONFLICT")).toContain("경쟁사 워치리스트");
  });
  it("INVALID_STOCK_CODE → 종목코드 안내", () => {
    expect(localizeReason("INVALID_STOCK_CODE")).toContain("종목코드");
  });
  it("DUPLICATE_IN_REQUEST → 중복 안내", () => {
    expect(localizeReason("DUPLICATE_IN_REQUEST")).toContain("중복");
  });
  it("RATE_LIMITED → 다시 시도 안내", () => {
    expect(localizeReason("RATE_LIMITED")).toContain("다시 시도");
  });
  it("VALIDATION_FAILED → 입력값 확인", () => {
    expect(localizeReason("VALIDATION_FAILED")).toContain("입력값");
  });
  it("unknown → 한국어 fallback (영어 노출 금지)", () => {
    expect(localizeReason("COMPLETELY_UNKNOWN_REASON")).toBe("항목을 저장할 수 없었어요");
  });
  it("빈 문자열도 한국어 fallback", () => {
    expect(localizeReason("")).toBe("항목을 저장할 수 없었어요");
  });
});
