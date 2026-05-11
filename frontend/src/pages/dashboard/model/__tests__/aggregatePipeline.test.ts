import { describe, it, expect } from "vitest";
import { aggregateTodayPipeline } from "../aggregatePipeline";

const makeRun = (overrides: Partial<Parameters<typeof aggregateTodayPipeline>[0][0]> = {}) => ({
  startedAt: "2026-04-08T09:00:00Z",
  totalCollected: 10,
  totalSummarized: 6,
  postedToSlack: true,
  status: "SUCCEEDED",
  ...overrides,
});

describe("aggregateTodayPipeline", () => {
  it("오늘 날짜의 runs만 집계한다", () => {
    const runs = [
      makeRun({ startedAt: "2026-04-08T09:00:00Z", totalCollected: 10, totalSummarized: 6 }),
      makeRun({ startedAt: "2026-04-08T12:00:00Z", totalCollected: 5, totalSummarized: 3 }),
      makeRun({ startedAt: "2026-04-07T09:00:00Z", totalCollected: 99, totalSummarized: 99 }),
    ];
    const result = aggregateTodayPipeline(runs, "2026-04-08");
    expect(result.collected).toBe(15);
    expect(result.summarized).toBe(9);
    expect(result.sent).toBe(2);
  });

  it("빈 배열이면 모두 0이고 lastSuccessAt은 null", () => {
    const result = aggregateTodayPipeline([], "2026-04-08");
    expect(result).toEqual({ collected: 0, summarized: 0, sent: 0, lastSuccessAt: null });
  });

  it("postedToSlack이 false인 run은 sent에 포함하지 않는다", () => {
    const runs = [
      makeRun({ postedToSlack: true }),
      makeRun({ postedToSlack: false }),
    ];
    const result = aggregateTodayPipeline(runs, "2026-04-08");
    expect(result.sent).toBe(1);
  });

  it("가장 최근 SUCCEEDED run의 startedAt을 반환한다", () => {
    const runs = [
      makeRun({ startedAt: "2026-04-07T18:00:00Z", status: "SUCCEEDED" }),
      makeRun({ startedAt: "2026-04-07T12:00:00Z", status: "SUCCEEDED" }),
      makeRun({ startedAt: "2026-04-08T09:00:00Z", status: "FAILED" }),
    ];
    const result = aggregateTodayPipeline(runs, "2026-04-08");
    expect(result.lastSuccessAt).toBe("2026-04-07T18:00:00Z");
  });

  it("SUCCEEDED가 없으면 lastSuccessAt은 null", () => {
    const runs = [makeRun({ status: "FAILED" })];
    const result = aggregateTodayPipeline(runs, "2026-04-08");
    expect(result.lastSuccessAt).toBeNull();
  });
});
