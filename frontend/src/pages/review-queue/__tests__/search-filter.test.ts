import { describe, it, expect } from "vitest";
import { matchesKoreanSearch } from "@/utils/search";

describe("review queue search filter", () => {
  it("matches title substring", () => {
    expect(matchesKoreanSearch("AI 반도체 수출 규제 강화", "반도체")).toBe(true);
  });

  it("matches Korean initial consonants in title", () => {
    expect(matchesKoreanSearch("MegaCorp 실적 발표", "ㅅㅅ")).toBe(true);
  });

  it("does not match unrelated query", () => {
    expect(matchesKoreanSearch("AI 반도체 뉴스", "금융")).toBe(false);
  });

  it("empty query matches everything", () => {
    expect(matchesKoreanSearch("anything", "")).toBe(true);
  });

  it("keyword array search pattern", () => {
    const keywords = ["반도체", "수출규제", "MegaCorp"];
    const query = "MegaCorp";
    const matches = keywords.some((kw) => matchesKoreanSearch(kw, query));
    expect(matches).toBe(true);
  });

  it("keyword initial consonant search", () => {
    const keywords = ["클라우드", "비용최적화"];
    const query = "ㅋㄹ";
    const matches = keywords.some((kw) => matchesKoreanSearch(kw, query));
    expect(matches).toBe(true);
  });
});
