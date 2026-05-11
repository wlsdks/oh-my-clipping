import { describe, it, expect } from "vitest";
import { buildDonutData } from "../SentimentDonut";

describe("buildDonutData", () => {
  it("정상 입력 시 3개 항목(긍정/중립/부정)을 올바른 name, value, fill로 반환한다", () => {
    const sentiment = {
      positive: 10,
      neutral: 5,
      negative: 3,
      total: 18,
      positiveRate: 55.6,
      neutralRate: 27.8,
      negativeRate: 16.7
    };

    const result = buildDonutData(sentiment);

    expect(result).toEqual([
      { name: "긍정", value: 10, fill: "#10b981" },
      { name: "중립", value: 5, fill: "#94a3b8" },
      { name: "부정", value: 3, fill: "#f43f5e" }
    ]);
  });

  it("total=0이면 빈 배열을 반환한다", () => {
    const sentiment = {
      positive: 0,
      neutral: 0,
      negative: 0,
      total: 0,
      positiveRate: 0,
      neutralRate: 0,
      negativeRate: 0
    };

    const result = buildDonutData(sentiment);

    expect(result).toEqual([]);
  });
});
