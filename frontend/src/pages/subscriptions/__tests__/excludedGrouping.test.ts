import { describe, it, expect } from "vitest";
import { groupByKeyword } from "../model/excludedGrouping";
import type { ExcludedItem } from "@/types/category";

function makeItem(overrides: Partial<ExcludedItem>): ExcludedItem {
  return {
    title: "기사 제목",
    reason: "excluded",
    matchedKeyword: "키워드",
    score: 0.8,
    excludedAt: "2026-04-08T10:00:00Z",
    ...overrides,
  };
}

describe("groupByKeyword", () => {
  it("groups items by matchedKeyword", () => {
    const items = [
      makeItem({ title: "A", matchedKeyword: "돌파", excludedAt: "2026-04-08T10:00:00Z" }),
      makeItem({ title: "B", matchedKeyword: "돌파", excludedAt: "2026-04-08T11:00:00Z" }),
      makeItem({ title: "C", matchedKeyword: "협상", excludedAt: "2026-04-08T09:00:00Z" }),
    ];
    const groups = groupByKeyword(items);
    expect(groups).toHaveLength(2);
    const dolpa = groups.find((g) => g.keyword === "돌파");
    expect(dolpa?.count).toBe(2);
    expect(dolpa?.samples).toHaveLength(2);
  });

  it("sorts groups by count desc", () => {
    const items = [
      makeItem({ matchedKeyword: "A" }),
      makeItem({ matchedKeyword: "B" }),
      makeItem({ matchedKeyword: "B" }),
      makeItem({ matchedKeyword: "B" }),
      makeItem({ matchedKeyword: "C" }),
      makeItem({ matchedKeyword: "C" }),
    ];
    const groups = groupByKeyword(items);
    expect(groups.map((g) => g.keyword)).toEqual(["B", "C", "A"]);
  });

  it("latestExcludedAt is the most recent excludedAt in the group", () => {
    const items = [
      makeItem({ matchedKeyword: "X", excludedAt: "2026-04-08T09:00:00Z" }),
      makeItem({ matchedKeyword: "X", excludedAt: "2026-04-08T12:00:00Z" }),
      makeItem({ matchedKeyword: "X", excludedAt: "2026-04-08T10:00:00Z" }),
    ];
    const groups = groupByKeyword(items);
    expect(groups[0].latestExcludedAt).toBe("2026-04-08T12:00:00Z");
  });

  it("ignores items with null matchedKeyword", () => {
    const items = [
      makeItem({ matchedKeyword: null }),
      makeItem({ matchedKeyword: "X" }),
    ];
    const groups = groupByKeyword(items);
    expect(groups).toHaveLength(1);
    expect(groups[0].keyword).toBe("X");
  });

  it("keeps only first SAMPLES_PER_GROUP samples per group", () => {
    const items = [
      makeItem({ matchedKeyword: "A", excludedAt: "2026-04-08T01:00:00Z", title: "1" }),
      makeItem({ matchedKeyword: "A", excludedAt: "2026-04-08T02:00:00Z", title: "2" }),
      makeItem({ matchedKeyword: "A", excludedAt: "2026-04-08T03:00:00Z", title: "3" }),
      makeItem({ matchedKeyword: "A", excludedAt: "2026-04-08T04:00:00Z", title: "4" }),
      makeItem({ matchedKeyword: "A", excludedAt: "2026-04-08T05:00:00Z", title: "5" }),
    ];
    const groups = groupByKeyword(items);
    expect(groups[0].samples).toHaveLength(3);
    expect(groups[0].samples.map((s) => s.title)).toEqual(["5", "4", "3"]);
    expect(groups[0].count).toBe(5);
  });
});
