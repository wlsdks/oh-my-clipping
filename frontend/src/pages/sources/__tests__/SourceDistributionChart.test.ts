import { describe, it, expect } from "vitest";
import type { Source } from "@/types/source";
import type { Category } from "@/types/category";

// Re-implement the aggregation logic to test it independently.
// We extract this from the component since it's a pure function.
interface ChartDatum {
  name: string;
  domestic: number;
  global: number;
  total: number;
}

function aggregateDistribution(
  sources: Source[],
  categories: Category[],
): ChartDatum[] {
  const catMap = new Map(categories.map((c) => [c.id, c.name]));
  const counts = new Map<string, { domestic: number; global: number }>();

  for (const cat of categories) {
    counts.set(cat.id, { domestic: 0, global: 0 });
  }

  for (const source of sources) {
    const entry = counts.get(source.categoryId);
    if (!entry) continue;
    if (source.sourceRegion === "DOMESTIC") {
      entry.domestic += 1;
    } else {
      entry.global += 1;
    }
  }

  return Array.from(counts.entries())
    .map(([catId, { domestic, global }]) => ({
      name: catMap.get(catId) ?? catId,
      domestic,
      global,
      total: domestic + global,
    }))
    .sort((a, b) => b.total - a.total);
}

function makeSource(overrides: Partial<Source> = {}): Source {
  return {
    id: "src-1",
    name: "Test Source",
    url: "https://example.com/feed",
    sourceRegion: "DOMESTIC",
    isActive: true,
    crawlApproved: true,
    legalBasis: "QUOTATION_ONLY",
    summaryAllowed: true,
    fulltextAllowed: false,
    verificationStatus: "VERIFIED",
    reliabilityScore: 50,
    crawlFailCount: 0,
    curated: false,
    categoryId: "cat-1",
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}

function makeCategory(id: string, name: string): Category {
  return {
    id,
    name,
    isActive: true,
    isPublic: true,
    maxItems: 10,
    sourceCount: 0,
    subscriberCount: 0,
    errorSourceCount: 0,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    status: "ACTIVE",
  };
}

describe("aggregateDistribution", () => {
  it("빈 소스 목록이면 카테고리별 0 카운트를 반환한다", () => {
    const categories = [makeCategory("cat-1", "기술"), makeCategory("cat-2", "정치")];
    const result = aggregateDistribution([], categories);

    expect(result).toHaveLength(2);
    expect(result[0].domestic).toBe(0);
    expect(result[0].global).toBe(0);
  });

  it("DOMESTIC 소스를 domestic으로 집계한다", () => {
    const categories = [makeCategory("cat-1", "기술")];
    const sources = [
      makeSource({ id: "s1", categoryId: "cat-1", sourceRegion: "DOMESTIC" }),
      makeSource({ id: "s2", categoryId: "cat-1", sourceRegion: "DOMESTIC" }),
    ];

    const result = aggregateDistribution(sources, categories);

    expect(result[0].domestic).toBe(2);
    expect(result[0].global).toBe(0);
  });

  it("GLOBAL과 UNKNOWN 소스를 global로 집계한다", () => {
    const categories = [makeCategory("cat-1", "기술")];
    const sources = [
      makeSource({ id: "s1", categoryId: "cat-1", sourceRegion: "GLOBAL" }),
      makeSource({ id: "s2", categoryId: "cat-1", sourceRegion: "UNKNOWN" }),
    ];

    const result = aggregateDistribution(sources, categories);

    expect(result[0].domestic).toBe(0);
    expect(result[0].global).toBe(2);
  });

  it("total 내림차순으로 정렬한다", () => {
    const categories = [
      makeCategory("cat-1", "기술"),
      makeCategory("cat-2", "정치"),
    ];
    const sources = [
      makeSource({ id: "s1", categoryId: "cat-2", sourceRegion: "DOMESTIC" }),
      makeSource({ id: "s2", categoryId: "cat-2", sourceRegion: "GLOBAL" }),
      makeSource({ id: "s3", categoryId: "cat-1", sourceRegion: "DOMESTIC" }),
    ];

    const result = aggregateDistribution(sources, categories);

    expect(result[0].name).toBe("정치");
    expect(result[0].total).toBe(2);
    expect(result[1].name).toBe("기술");
    expect(result[1].total).toBe(1);
  });

  it("카테고리가 없는 소스는 무시한다", () => {
    const categories = [makeCategory("cat-1", "기술")];
    const sources = [
      makeSource({ id: "s1", categoryId: "unknown-cat", sourceRegion: "DOMESTIC" }),
    ];

    const result = aggregateDistribution(sources, categories);

    expect(result[0].domestic).toBe(0);
    expect(result[0].total).toBe(0);
  });
});
