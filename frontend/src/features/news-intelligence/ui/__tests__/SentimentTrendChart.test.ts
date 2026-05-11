import { describe, it, expect } from "vitest";
import { generateSentimentInsights } from "../SentimentTrendChart";
import type { SentimentDailyCount } from "../../../../shared/types/admin";

describe("generateSentimentInsights", () => {
  it("부정 비율 급증일을 감지한다 (negative/total > 0.4)", () => {
    const daily: SentimentDailyCount[] = [
      { date: "2026-03-01", positive: 3, neutral: 2, negative: 5, total: 10 },
      { date: "2026-03-02", positive: 7, neutral: 2, negative: 1, total: 10 }
    ];

    const insights = generateSentimentInsights(daily);

    expect(insights.some((s) => s.includes("3/1") && s.includes("부정"))).toBe(true);
  });

  it("긍정 연속 트렌드를 감지한다 (3일 이상 positive > 50%)", () => {
    const daily: SentimentDailyCount[] = [
      { date: "2026-03-01", positive: 6, neutral: 2, negative: 2, total: 10 },
      { date: "2026-03-02", positive: 7, neutral: 2, negative: 1, total: 10 },
      { date: "2026-03-03", positive: 8, neutral: 1, negative: 1, total: 10 }
    ];

    const insights = generateSentimentInsights(daily);

    expect(insights.some((s) => s.includes("긍정") && s.includes("연속"))).toBe(true);
  });

  it("빈 데이터는 빈 배열을 반환한다", () => {
    const insights = generateSentimentInsights([]);

    expect(insights).toEqual([]);
  });

  it("total이 0인 날은 무시한다", () => {
    const daily: SentimentDailyCount[] = [
      { date: "2026-03-01", positive: 0, neutral: 0, negative: 0, total: 0 },
      { date: "2026-03-02", positive: 3, neutral: 2, negative: 5, total: 10 }
    ];

    const insights = generateSentimentInsights(daily);

    // total=0인 3/1은 부정 급증으로 감지하지 않아야 한다
    expect(insights.some((s) => s.includes("3/1"))).toBe(false);
    // total>0인 3/2는 부정 비율 50%이므로 감지해야 한다
    expect(insights.some((s) => s.includes("3/2") && s.includes("부정"))).toBe(true);
  });

  it("부정 급증과 긍정 트렌드가 동시에 존재하면 모두 반환한다", () => {
    const daily: SentimentDailyCount[] = [
      { date: "2026-03-01", positive: 6, neutral: 2, negative: 2, total: 10 },
      { date: "2026-03-02", positive: 7, neutral: 2, negative: 1, total: 10 },
      { date: "2026-03-03", positive: 8, neutral: 1, negative: 1, total: 10 },
      { date: "2026-03-04", positive: 1, neutral: 2, negative: 7, total: 10 }
    ];

    const insights = generateSentimentInsights(daily);

    expect(insights.length).toBeGreaterThanOrEqual(2);
    expect(insights.some((s) => s.includes("긍정"))).toBe(true);
    expect(insights.some((s) => s.includes("부정"))).toBe(true);
  });
});
