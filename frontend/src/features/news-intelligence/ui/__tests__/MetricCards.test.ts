import { describe, it, expect } from "vitest";
import { calcChangePercent, aggregateSentiment, splitPeriod } from "../MetricCards";
import type { TopArticleItem } from "../../../../shared/types/admin";

describe("calcChangePercent", () => {
  it("(112, 100) → 12", () => {
    expect(calcChangePercent(112, 100)).toBe(12);
  });

  it("(10, 0) → null (division by zero)", () => {
    expect(calcChangePercent(10, 0)).toBeNull();
  });

  it("(80, 100) → -20", () => {
    expect(calcChangePercent(80, 100)).toBe(-20);
  });

  it("(100, 100) → 0", () => {
    expect(calcChangePercent(100, 100)).toBe(0);
  });
});

describe("aggregateSentiment", () => {
  it("mixed sentiments + null → correct counts and rates (null excluded)", () => {
    const articles: Pick<TopArticleItem, "sentiment">[] = [
      { sentiment: "POSITIVE" },
      { sentiment: "POSITIVE" },
      { sentiment: "NEUTRAL" },
      { sentiment: "NEGATIVE" },
      { sentiment: null }
    ];
    const result = aggregateSentiment(articles as TopArticleItem[]);
    expect(result.positive).toBe(2);
    expect(result.neutral).toBe(1);
    expect(result.negative).toBe(1);
    expect(result.total).toBe(4); // null excluded
    expect(result.positiveRate).toBeCloseTo(50);
    expect(result.neutralRate).toBeCloseTo(25);
    expect(result.negativeRate).toBeCloseTo(25);
  });

  it("empty array → all zeros", () => {
    const result = aggregateSentiment([]);
    expect(result.positive).toBe(0);
    expect(result.neutral).toBe(0);
    expect(result.negative).toBe(0);
    expect(result.total).toBe(0);
    expect(result.positiveRate).toBe(0);
    expect(result.neutralRate).toBe(0);
    expect(result.negativeRate).toBe(0);
  });
});

describe("splitPeriod", () => {
  it("articles spanning 14 days split at day 7 boundary", () => {
    const now = Date.now();
    const msPerDay = 86_400_000;
    // 14-day period: articles clearly in prev half (older) and curr half (newer)
    // midpoint = now - 7 days
    // Place 6 articles well before midpoint and 8 articles well after midpoint
    const articles: TopArticleItem[] = Array.from({ length: 14 }, (_, i) => ({
      summaryId: `s${i}`,
      title: `Article ${i}`,
      sourceLink: `https://example.com/${i}`,
      importanceScore: 0.5,
      keywords: [],
      sentiment: null,
      eventType: null,
      // i=0: now-14d, i=13: now-1d; midpoint ≈ now-7d
      createdAt: new Date(now - (14 - i) * msPerDay).toISOString()
    }));

    const { prev, curr } = splitPeriod(articles, 14);
    // Due to sub-ms timing differences between test's `now` and splitPeriod's `now`,
    // the exact split may vary by ±1 at the boundary. Verify total is preserved
    // and each half is roughly correct.
    expect(prev.length + curr.length).toBe(14);
    expect(prev.length).toBeGreaterThanOrEqual(6);
    expect(prev.length).toBeLessThanOrEqual(8);
    expect(curr.length).toBeGreaterThanOrEqual(6);
    expect(curr.length).toBeLessThanOrEqual(8);
  });
});
