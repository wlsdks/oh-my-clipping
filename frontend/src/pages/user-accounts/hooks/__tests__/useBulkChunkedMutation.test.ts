import { describe, it, expect } from "vitest";
import { chunkArray, aggregateResults } from "../useBulkChunkedMutation";
import type { BulkReviewResult } from "@/types/user";

describe("chunkArray", () => {
  it("빈 배열은 빈 결과를 반환한다", () => {
    expect(chunkArray([], 50)).toEqual([]);
  });

  it("chunkSize보다 작은 배열은 1개 청크로 반환한다", () => {
    expect(chunkArray(["a", "b", "c"], 50)).toEqual([["a", "b", "c"]]);
  });

  it("150건을 50건씩 3개 청크로 분할한다", () => {
    const ids = Array.from({ length: 150 }, (_, i) => `id-${i}`);
    const chunks = chunkArray(ids, 50);
    expect(chunks).toHaveLength(3);
    expect(chunks[0]).toHaveLength(50);
    expect(chunks[1]).toHaveLength(50);
    expect(chunks[2]).toHaveLength(50);
  });

  it("나머지가 있으면 마지막 청크에 포함한다", () => {
    const ids = Array.from({ length: 75 }, (_, i) => `id-${i}`);
    const chunks = chunkArray(ids, 50);
    expect(chunks).toHaveLength(2);
    expect(chunks[0]).toHaveLength(50);
    expect(chunks[1]).toHaveLength(25);
  });
});

describe("aggregateResults", () => {
  it("여러 결과를 하나로 합친다", () => {
    const results: BulkReviewResult[] = [
      { succeeded: ["a", "b"], failed: [] },
      { succeeded: ["c"], failed: [{ id: "d", reason: "err", code: "UNKNOWN" }] },
    ];
    const agg = aggregateResults(results);
    expect(agg.succeeded).toEqual(["a", "b", "c"]);
    expect(agg.failed).toEqual([{ id: "d", reason: "err", code: "UNKNOWN" }]);
  });

  it("빈 결과 배열은 빈 집계를 반환한다", () => {
    const agg = aggregateResults([]);
    expect(agg.succeeded).toEqual([]);
    expect(agg.failed).toEqual([]);
  });
});
