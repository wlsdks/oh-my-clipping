import { describe, it, expect } from "vitest";
import { matchesKoreanSearch } from "@/utils/search";

describe("matchesKoreanSearch", () => {
  it("초성으로 검색된다", () => {
    expect(matchesKoreanSearch("라면", "ㄹㅁ")).toBe(true);
  });

  it("전체 텍스트로 검색된다", () => {
    expect(matchesKoreanSearch("라면", "라면")).toBe(true);
  });

  it("부분 텍스트로 검색된다", () => {
    expect(matchesKoreanSearch("라면", "라")).toBe(true);
  });

  it("일치하지 않는 초성은 false다", () => {
    expect(matchesKoreanSearch("라면", "ㅂㅂ")).toBe(false);
  });

  it("빈 쿼리는 항상 true다", () => {
    expect(matchesKoreanSearch("라면", "")).toBe(true);
  });

  it("영문+한글 혼합 타겟에서 초성 검색이 된다", () => {
    expect(matchesKoreanSearch("React 라면", "ㄹㅁ")).toBe(true);
  });

  it("숫자 혼합 타겟에서 초성 검색이 된다", () => {
    expect(matchesKoreanSearch("라면123", "ㄹㅁ")).toBe(true);
  });
});
