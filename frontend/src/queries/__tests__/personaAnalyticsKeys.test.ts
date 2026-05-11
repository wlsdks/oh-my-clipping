// @vitest-environment node
import { describe, it, expect } from "vitest";
import { personaAnalyticsKeys } from "../personaAnalyticsKeys";

describe("personaAnalyticsKeys", () => {
  it('all 은 ["persona-analytics"] 고정', () => {
    expect(personaAnalyticsKeys.all).toEqual(["persona-analytics"]);
  });

  it('live() 는 [...all, "live"] 로 파생된다', () => {
    expect(personaAnalyticsKeys.live()).toEqual(["persona-analytics", "live"]);
  });

  it("동일 호출은 동일 키 시퀀스를 반환한다 (안정성)", () => {
    expect(personaAnalyticsKeys.live()).toEqual(personaAnalyticsKeys.live());
  });

  it('trends(12) 는 [...all, "trends", 12] 로 파생된다', () => {
    expect(personaAnalyticsKeys.trends(12)).toEqual(["persona-analytics", "trends", 12]);
  });

  it("trends() 는 weeks 값에 따라 다른 키를 반환한다", () => {
    expect(personaAnalyticsKeys.trends(4)).not.toEqual(personaAnalyticsKeys.trends(12));
    expect(personaAnalyticsKeys.trends(26)).not.toEqual(personaAnalyticsKeys.trends(52));
  });

  it('batchRuns() 는 [...all, "batch-runs"] 로 파생된다', () => {
    expect(personaAnalyticsKeys.batchRuns()).toEqual(["persona-analytics", "batch-runs"]);
  });

  it("모든 키는 all 을 prefix 로 공유한다", () => {
    const all = personaAnalyticsKeys.all;
    expect(personaAnalyticsKeys.live().slice(0, all.length)).toEqual([...all]);
    expect(personaAnalyticsKeys.trends(12).slice(0, all.length)).toEqual([...all]);
    expect(personaAnalyticsKeys.batchRuns().slice(0, all.length)).toEqual([...all]);
  });
});
