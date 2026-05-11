import { describe, it, expect } from "vitest";
import { buildCategoryLabelMap, formatCategoryOptionLabel, formatCategorySummary } from "../categoryLabels";
import type { Category } from "@/types/category";

function makeCategory(overrides: Partial<Category> & { id: string; name: string }): Category {
  return {
    isActive: true,
    isPublic: true,
    maxItems: 10,
    sourceCount: 3,
    subscriberCount: 1,
    errorSourceCount: 0,
    status: "ACTIVE",
    createdAt: "2025-01-15T09:00:00Z",
    updatedAt: "2025-01-15T09:00:00Z",
    ...overrides
  };
}

describe("buildCategoryLabelMap", () => {
  it("maps category id to name for unique names", () => {
    const categories = [
      makeCategory({ id: "cat-1", name: "경제" }),
      makeCategory({ id: "cat-2", name: "기술" })
    ];

    const result = buildCategoryLabelMap(categories);

    expect(result["cat-1"]).toBe("경제");
    expect(result["cat-2"]).toBe("기술");
  });

  it("disambiguates duplicate names with Slack channel and creation date", () => {
    const categories = [
      makeCategory({
        id: "cat-1",
        name: "경제",
        slackChannelId: "C12345ABC",
        createdAt: "2025-01-15T09:00:00Z"
      }),
      makeCategory({
        id: "cat-2",
        name: "경제",
        slackChannelId: "C99999XYZ",
        createdAt: "2025-06-20T14:30:00Z"
      })
    ];

    const result = buildCategoryLabelMap(categories);

    expect(result["cat-1"]).toContain("경제");
    expect(result["cat-1"]).toContain("#C12345ABC");
    expect(result["cat-2"]).toContain("경제");
    expect(result["cat-2"]).toContain("#C99999XYZ");
  });

  it("returns empty object for empty input", () => {
    expect(buildCategoryLabelMap([])).toEqual({});
  });
});

describe("formatCategoryOptionLabel", () => {
  it("returns just the name when no duplicates", () => {
    const category = makeCategory({ id: "cat-1", name: "기술" });
    const nameCounts = { "기술": 1 };

    expect(formatCategoryOptionLabel(category, nameCounts)).toBe("기술");
  });

  it("returns just the name when nameCounts is undefined", () => {
    const category = makeCategory({ id: "cat-1", name: "기술" });

    expect(formatCategoryOptionLabel(category)).toBe("기술");
  });

  it("appends Slack channel and date for duplicates", () => {
    const category = makeCategory({
      id: "cat-1",
      name: "경제",
      slackChannelId: "C12345ABC",
      createdAt: "2025-01-15T09:00:00Z"
    });
    const nameCounts = { "경제": 2 };

    const result = formatCategoryOptionLabel(category, nameCounts);

    expect(result).toContain("경제");
    expect(result).toContain("#C12345ABC");
    expect(result).toContain(" · ");
  });

  it('shows "Slack DM" for blank Slack channel on duplicates', () => {
    const category = makeCategory({
      id: "cat-1",
      name: "경제",
      slackChannelId: null,
      createdAt: "2025-01-15T09:00:00Z"
    });
    const nameCounts = { "경제": 2 };

    const result = formatCategoryOptionLabel(category, nameCounts);

    expect(result).toContain("Slack DM");
  });
});

describe("formatCategorySummary", () => {
  it("formats summary with Slack channel, source count, and max items", () => {
    const category = makeCategory({
      id: "cat-1",
      name: "경제",
      slackChannelId: "C12345ABC",
      sourceCount: 5,
      maxItems: 20
    });

    const result = formatCategorySummary(category);

    expect(result).toContain("알림 #C12345ABC");
    expect(result).toContain("소스 5개");
    expect(result).toContain("최대 20건");
    expect(result).toBe("알림 #C12345ABC · 소스 5개 · 최대 20건");
  });

  it('shows "Slack DM" when slackChannelId is null', () => {
    const category = makeCategory({
      id: "cat-1",
      name: "경제",
      slackChannelId: null,
      sourceCount: 0,
      maxItems: 10
    });

    const result = formatCategorySummary(category);

    expect(result).toContain("알림 Slack DM");
    expect(result).toBe("알림 Slack DM · 소스 0개 · 최대 10건");
  });

  it("uses dot separator between all parts", () => {
    const category = makeCategory({
      id: "cat-1",
      name: "기술",
      sourceCount: 3,
      maxItems: 15
    });

    const parts = formatCategorySummary(category).split(" · ");
    expect(parts).toHaveLength(3);
  });
});
