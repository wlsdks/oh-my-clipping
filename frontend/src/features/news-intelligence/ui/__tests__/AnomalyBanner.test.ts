import { describe, it, expect } from "vitest";
import { detectAnomalies } from "../AnomalyBanner";
import type { KeywordTrendItem } from "../../../../shared/types/admin";

function makeKeyword(keyword: string, changeRate: number): KeywordTrendItem {
  return { keyword, changeRate, totalCount: 10, dailyCounts: [] };
}

describe("detectAnomalies", () => {
  it("changeRate=3.4 → keyword_surge, 메시지에 340%와 키워드명 포함", () => {
    const alerts = detectAnomalies([makeKeyword("AI", 3.4)]);
    expect(alerts).toHaveLength(1);
    expect(alerts[0].type).toBe("keyword_surge");
    expect(alerts[0].message).toContain("340%");
    expect(alerts[0].message).toContain("AI");
    expect(alerts[0].keyword).toBe("AI");
  });

  it("changeRate=-0.6 → keyword_drop, 메시지에 60% 포함", () => {
    const alerts = detectAnomalies([makeKeyword("반도체", -0.6)]);
    expect(alerts).toHaveLength(1);
    expect(alerts[0].type).toBe("keyword_drop");
    expect(alerts[0].message).toContain("60%");
    expect(alerts[0].keyword).toBe("반도체");
  });

  it("changeRate=0.2 → 정상 범위, 알림 없음", () => {
    const alerts = detectAnomalies([makeKeyword("일반", 0.2)]);
    expect(alerts).toHaveLength(0);
  });

  it("빈 키워드 배열 → 빈 알림 배열", () => {
    const alerts = detectAnomalies([]);
    expect(alerts).toHaveLength(0);
  });

  it("최대 5개까지만 알림 생성", () => {
    const keywords = Array.from({ length: 10 }, (_, i) => makeKeyword(`kw${i}`, 3.0));
    const alerts = detectAnomalies(keywords);
    expect(alerts.length).toBeLessThanOrEqual(5);
  });
});
