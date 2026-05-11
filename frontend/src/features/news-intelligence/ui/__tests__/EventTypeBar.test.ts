import { describe, it, expect } from "vitest";
import { aggregateEventTypes } from "../EventTypeBar";

describe("aggregateEventTypes", () => {
  it("혼합된 eventType 목록을 count 내림차순으로 정렬하고 올바른 label을 반환한다", () => {
    const articles = [
      { eventType: "PRODUCT_LAUNCH" },
      { eventType: "PARTNERSHIP" },
      { eventType: "PRODUCT_LAUNCH" },
      { eventType: "FUNDING" }
    ];

    const result = aggregateEventTypes(articles);

    expect(result).toHaveLength(3);
    expect(result[0]).toEqual({
      type: "PRODUCT_LAUNCH",
      label: "🚀 제품 출시",
      count: 2
    });
    expect(result[1]).toEqual({
      type: "PARTNERSHIP",
      label: "🤝 제휴·협력",
      count: 1
    });
    expect(result[2]).toEqual({
      type: "FUNDING",
      label: "💰 투자·인수",
      count: 1
    });
  });

  it("모든 eventType이 null이면 빈 배열을 반환한다", () => {
    const articles = [{ eventType: null }, { eventType: null }, { eventType: null }];

    const result = aggregateEventTypes(articles);

    expect(result).toEqual([]);
  });

  it("알 수 없는 eventType은 '기타'로 분류한다", () => {
    const articles = [{ eventType: "UNKNOWN_TYPE" }, { eventType: "SOME_NEW_EVENT" }];

    const result = aggregateEventTypes(articles);

    expect(result).toHaveLength(1);
    expect(result[0]).toEqual({
      type: "OTHER",
      label: "📰 기타",
      count: 2
    });
  });
});
