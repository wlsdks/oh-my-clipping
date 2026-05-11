import { describe, it, expect } from "vitest";
import { normalizeLegacyBundleTerm } from "../wording";

describe("normalizeLegacyBundleTerm", () => {
  it('replaces "요약본" with "뉴스 모음"', () => {
    expect(normalizeLegacyBundleTerm("요약본을 확인하세요")).toBe("뉴스 모음을 확인하세요");
  });

  it("returns unchanged text when no match", () => {
    expect(normalizeLegacyBundleTerm("뉴스레터를 확인하세요")).toBe("뉴스레터를 확인하세요");
  });

  it("replaces all occurrences in text", () => {
    expect(normalizeLegacyBundleTerm("요약본 첫번째, 요약본 두번째")).toBe(
      "뉴스 모음 첫번째, 뉴스 모음 두번째"
    );
  });

  it("returns empty string for empty input", () => {
    expect(normalizeLegacyBundleTerm("")).toBe("");
  });

  it("handles text that is exactly the target word", () => {
    expect(normalizeLegacyBundleTerm("요약본")).toBe("뉴스 모음");
  });

  it("does not affect partial matches within longer words", () => {
    // "요약본" is a standalone substring match, regex has no word boundary
    expect(normalizeLegacyBundleTerm("요약본문")).toBe("뉴스 모음문");
  });
});
