import { describe, it, expect } from "vitest";
import {
  formatChange,
  aggregateByCategory,
  pickTopRisingKeyword,
  RISING_CHANGE_RATE_THRESHOLD,
  RISING_MIN_TOTAL_COUNT,
  type TrendKeyword,
} from "../stats";
import type { UserMonthlyStatRow } from "@/types/insight";

function row(partial: Partial<UserMonthlyStatRow> & Pick<UserMonthlyStatRow, "categoryId" | "categoryName">): UserMonthlyStatRow {
  return {
    id: `${partial.categoryId}-${partial.statDate ?? "2026-04-01"}`,
    categoryId: partial.categoryId,
    categoryName: partial.categoryName,
    statDate: partial.statDate ?? "2026-04-01",
    itemsCollected: partial.itemsCollected ?? 0,
    itemsSummarized: partial.itemsSummarized ?? 0,
    itemsSent: partial.itemsSent ?? 0,
    topKeywords: partial.topKeywords ?? [],
    avgImportanceScore: partial.avgImportanceScore ?? 0,
  };
}

describe("formatChange", () => {
  it("prevLoaded=false면 '-'와 none type을 반환해 색상/화살표를 숨긴다", () => {
    expect(formatChange(100, 0, false)).toEqual({ label: "-", type: "none" });
    expect(formatChange(0, 50, false)).toEqual({ label: "-", type: "none" });
  });

  it("이전달=0이고 현재>0이면 '신규' + up (첫 달로 표시)", () => {
    expect(formatChange(10, 0, true)).toEqual({ label: "신규", type: "up" });
  });

  it("이전달=0이고 현재=0이면 '-' + none (데이터 없음)", () => {
    expect(formatChange(0, 0, true)).toEqual({ label: "-", type: "none" });
  });

  it("양수 변화는 '+N%' + up으로 포맷", () => {
    expect(formatChange(150, 100, true)).toEqual({ label: "+50%", type: "up" });
  });

  it("반올림 결과 0%면 '동일' + flat", () => {
    expect(formatChange(301, 300, true)).toEqual({ label: "동일", type: "flat" });
    expect(formatChange(100, 100, true)).toEqual({ label: "동일", type: "flat" });
  });

  it("음수 변화는 '-N%' + down으로 포맷", () => {
    expect(formatChange(50, 100, true)).toEqual({ label: "-50%", type: "down" });
  });
});

describe("aggregateByCategory", () => {
  it("빈 배열은 빈 배열을 반환한다", () => {
    expect(aggregateByCategory([])).toEqual([]);
  });

  it("같은 카테고리의 여러 일자 행을 하나로 합산한다 (렌더링 버그 회귀 방지)", () => {
    const result = aggregateByCategory([
      row({ categoryId: "cat-A", categoryName: "마케팅", statDate: "2026-04-01", itemsSent: 3 }),
      row({ categoryId: "cat-A", categoryName: "마케팅", statDate: "2026-04-02", itemsSent: 5 }),
      row({ categoryId: "cat-A", categoryName: "마케팅", statDate: "2026-04-03", itemsSent: 2 }),
    ]);
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      categoryId: "cat-A",
      categoryName: "마케팅",
      itemsSent: 10,
    });
  });

  it("itemsSent 내림차순으로 정렬한다", () => {
    const result = aggregateByCategory([
      row({ categoryId: "small", categoryName: "작은것", itemsSent: 2 }),
      row({ categoryId: "big", categoryName: "큰것", itemsSent: 20 }),
      row({ categoryId: "medium", categoryName: "중간", itemsSent: 10 }),
    ]);
    expect(result.map((c) => c.categoryId)).toEqual(["big", "medium", "small"]);
  });

  it("키워드를 빈도 순으로 합집합 정렬한다", () => {
    const result = aggregateByCategory([
      row({ categoryId: "cat-A", categoryName: "A", topKeywords: ["AI", "마케팅"] }),
      row({ categoryId: "cat-A", categoryName: "A", topKeywords: ["AI", "광고"] }),
      row({ categoryId: "cat-A", categoryName: "A", topKeywords: ["AI"] }),
    ]);
    expect(result[0].topKeywords[0]).toBe("AI");
    expect(result[0].topKeywords).toContain("마케팅");
    expect(result[0].topKeywords).toContain("광고");
  });

  it("빈 topKeywords도 문제 없이 처리한다", () => {
    const result = aggregateByCategory([
      row({ categoryId: "cat-A", categoryName: "A", itemsSent: 5 }),
    ]);
    expect(result[0].topKeywords).toEqual([]);
  });

  it("서로 다른 카테고리는 각각 독립 집계된다", () => {
    const result = aggregateByCategory([
      row({ categoryId: "cat-A", categoryName: "A", itemsSent: 3, topKeywords: ["x"] }),
      row({ categoryId: "cat-B", categoryName: "B", itemsSent: 7, topKeywords: ["y"] }),
      row({ categoryId: "cat-A", categoryName: "A", itemsSent: 2, topKeywords: ["x", "z"] }),
    ]);
    expect(result).toHaveLength(2);
    const a = result.find((c) => c.categoryId === "cat-A")!;
    expect(a.itemsSent).toBe(5);
  });
});

describe("pickTopRisingKeyword", () => {
  const kw = (partial: Partial<TrendKeyword> & Pick<TrendKeyword, "keyword">): TrendKeyword => ({
    keyword: partial.keyword,
    totalCount: partial.totalCount ?? 10,
    changeRate: partial.changeRate ?? 0,
  });

  it("빈 배열이면 null", () => {
    expect(pickTopRisingKeyword([])).toBeNull();
  });

  it("하락/정체 키워드만 있으면 null", () => {
    expect(
      pickTopRisingKeyword([
        kw({ keyword: "down", changeRate: -0.5, totalCount: 20 }),
        kw({ keyword: "flat", changeRate: 0, totalCount: 30 }),
      ])
    ).toBeNull();
  });

  it("변화율 임계값 미만은 배너로 올라오지 않는다 (+5% = 급상승 과장 방지)", () => {
    expect(
      pickTopRisingKeyword([
        kw({ keyword: "slight", changeRate: 0.05, totalCount: 50 }),
        kw({ keyword: "mild", changeRate: 0.2, totalCount: 100 }),
      ])
    ).toBeNull();
  });

  it("등장 빈도 임계값 미만은 배너로 올라오지 않는다 (3건짜리 +200% 과장 방지)", () => {
    expect(
      pickTopRisingKeyword([
        kw({ keyword: "rare", changeRate: 2.0, totalCount: 2 }),
      ])
    ).toBeNull();
  });

  it("둘 다 통과하는 후보 중 changeRate 내림차순 1위를 반환한다", () => {
    const picked = pickTopRisingKeyword([
      kw({ keyword: "A", changeRate: 0.35, totalCount: 10 }),
      kw({ keyword: "B", changeRate: 0.8, totalCount: 50 }),
      kw({ keyword: "C", changeRate: 0.5, totalCount: 20 }),
      kw({ keyword: "tooRare", changeRate: 5.0, totalCount: 3 }),
      kw({ keyword: "tooMild", changeRate: 0.1, totalCount: 100 }),
    ]);
    expect(picked?.keyword).toBe("B");
  });

  it("경계값도 포함된다 (>= 30%, >= 5건)", () => {
    const picked = pickTopRisingKeyword([
      kw({ keyword: "edge", changeRate: RISING_CHANGE_RATE_THRESHOLD, totalCount: RISING_MIN_TOTAL_COUNT }),
    ]);
    expect(picked?.keyword).toBe("edge");
  });
});
