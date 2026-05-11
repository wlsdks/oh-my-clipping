import { describe, it, expect } from "vitest";
import { LEGAL_BASIS_OPTIONS, findLegalBasisOption } from "../legalBasisOptions";

describe("legalBasisOptions", () => {
  it("4가지 옵션을 정의한다", () => {
    expect(LEGAL_BASIS_OPTIONS.length).toBe(4);
  });

  it("첫 번째 옵션은 권장 배지가 있다", () => {
    expect(LEGAL_BASIS_OPTIONS[0].value).toBe("QUOTATION_ONLY");
    expect(LEGAL_BASIS_OPTIONS[0].badge).toBe("권장");
  });

  it("QUOTATION_ONLY는 전문 보존이 false 기본", () => {
    const opt = findLegalBasisOption("QUOTATION_ONLY");
    expect(opt.defaultPolicy.fulltextAllowed).toBe(false);
    expect(opt.defaultPolicy.summaryAllowed).toBe(true);
  });

  it("OPEN_LICENSE는 전문 보존이 true 기본", () => {
    const opt = findLegalBasisOption("OPEN_LICENSE");
    expect(opt.defaultPolicy.fulltextAllowed).toBe(true);
  });

  it("LICENSED는 전문 보존이 true 기본", () => {
    const opt = findLegalBasisOption("LICENSED");
    expect(opt.defaultPolicy.fulltextAllowed).toBe(true);
  });

  it("PROHIBITED는 요약과 전문 모두 false 기본", () => {
    const opt = findLegalBasisOption("PROHIBITED");
    expect(opt.defaultPolicy.summaryAllowed).toBe(false);
    expect(opt.defaultPolicy.fulltextAllowed).toBe(false);
  });

  it("findLegalBasisOption은 유효한 값을 찾는다", () => {
    expect(findLegalBasisOption("LICENSED").label).toBe("라이선스 계약");
  });
});
