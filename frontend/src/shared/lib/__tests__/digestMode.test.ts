import { describe, it, expect } from "vitest";
import { resolveDigestModeClient, modeLabel, type DigestMode } from "../digestMode";

describe("resolveDigestModeClient", () => {
  it.each([
    [0, 0, null],
    [1, 0, "TOPIC_ONLY"],
    [5, 0, "TOPIC_ONLY"],
    [0, 1, "ACCOUNT_ONLY"],
    [0, 7, "ACCOUNT_ONLY"],
    [1, 1, "CROSSFILTER"],
    [1, 3, "CROSSFILTER"],
    [4, 1, "CROSSFILTER"],
    [2, 2, "DUAL_SECTION"],
    [3, 3, "DUAL_SECTION"],
  ])("(%i, %i) → %s", (k, o, expected) => {
    expect(resolveDigestModeClient(k, o)).toBe(expected);
  });
});

describe("modeLabel", () => {
  it.each([
    [null, "—"],
    ["TOPIC_ONLY", "주제 뉴스"],
    ["ACCOUNT_ONLY", "기업 동향"],
    ["CROSSFILTER", "주제×기업 교차 필터"],
    ["DUAL_SECTION", "주제+기업 듀얼 섹션"],
  ])("%s → %s", (mode, expected) => {
    expect(modeLabel(mode as DigestMode | null)).toBe(expected);
  });
});
