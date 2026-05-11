import { describe, it, expect } from "vitest";
import { groupSources } from "../model/groupSources";
import type { Source } from "@/types/source";

function makeSource(overrides: Partial<Source>): Source {
  return {
    id: "s1",
    name: "Test",
    url: "https://example.com/rss",
    sourceRegion: "DOMESTIC",
    emoji: null,
    isActive: true,
    crawlApproved: true,
    approvedBy: null,
    approvedAt: null,
    legalBasis: "UNKNOWN",
    summaryAllowed: true,
    fulltextAllowed: false,
    termsReviewedAt: null,
    reviewNotes: null,
    verificationStatus: "VERIFIED",
    reliabilityScore: 90,
    lastCrawlError: null,
    crawlFailCount: 0,
    curated: false,
    categoryId: "c1",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-02T00:00:00Z",
    ...overrides,
  };
}

describe("groupSources", () => {
  it("sorts ACTIVE source into active bucket", () => {
    const src = makeSource({ id: "a1" });
    const result = groupSources([src]);
    expect(result.active).toHaveLength(1);
    expect(result.active[0].id).toBe("a1");
    expect(result.connectionError).toEqual([]);
    expect(result.pendingApproval).toEqual([]);
    expect(result.archived).toEqual([]);
  });

  it("sorts WARNING source (crawlFailCount 1-9) into active", () => {
    const src = makeSource({ id: "w1", crawlFailCount: 3 });
    const result = groupSources([src]);
    expect(result.active).toHaveLength(1);
    expect(result.active[0].id).toBe("w1");
    expect(result.connectionError).toEqual([]);
  });

  it("sorts ERROR source (crawlFailCount >= 10) into connectionError", () => {
    const src = makeSource({ id: "e1", crawlFailCount: 10 });
    const result = groupSources([src]);
    expect(result.connectionError).toHaveLength(1);
    expect(result.connectionError[0].id).toBe("e1");
  });

  it("sorts ERROR source (verificationStatus FAILED) into connectionError", () => {
    const src = makeSource({ id: "e2", verificationStatus: "FAILED" });
    const result = groupSources([src]);
    expect(result.connectionError[0].id).toBe("e2");
  });

  it("sorts UNAPPROVED source into pendingApproval", () => {
    const src = makeSource({ id: "p1", crawlApproved: false });
    const result = groupSources([src]);
    expect(result.pendingApproval[0].id).toBe("p1");
  });

  it("sorts INACTIVE source into archived", () => {
    const src = makeSource({ id: "i1", isActive: false });
    const result = groupSources([src]);
    expect(result.archived[0].id).toBe("i1");
  });

  it("error (>=10) takes priority over unapproved", () => {
    const src = makeSource({ id: "e3", crawlFailCount: 10, crawlApproved: false });
    const result = groupSources([src]);
    expect(result.connectionError).toHaveLength(1);
    expect(result.pendingApproval).toEqual([]);
  });

  it("warning (1-9) with unapproved goes to pendingApproval", () => {
    const src = makeSource({ id: "w2", crawlFailCount: 1, crawlApproved: false });
    const result = groupSources([src]);
    expect(result.pendingApproval).toHaveLength(1);
    expect(result.connectionError).toEqual([]);
  });

  it("groups mixed list correctly", () => {
    const sources = [
      makeSource({ id: "a1" }),
      makeSource({ id: "w1", crawlFailCount: 2 }),  // warning → active
      makeSource({ id: "e1", crawlFailCount: 10 }), // error → connectionError
      makeSource({ id: "p1", crawlApproved: false }),
      makeSource({ id: "i1", isActive: false }),
      makeSource({ id: "a2" }),
    ];
    const result = groupSources(sources);
    expect(result.active).toHaveLength(3);          // a1 + w1 + a2
    expect(result.connectionError).toHaveLength(1);  // e1
    expect(result.pendingApproval).toHaveLength(1);  // p1
    expect(result.archived).toHaveLength(1);         // i1
  });

  it("empty input returns empty buckets", () => {
    const result = groupSources([]);
    expect(result.active).toEqual([]);
    expect(result.connectionError).toEqual([]);
    expect(result.pendingApproval).toEqual([]);
    expect(result.archived).toEqual([]);
  });
});
