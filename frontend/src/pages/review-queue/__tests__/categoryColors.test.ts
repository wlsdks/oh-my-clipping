import { describe, it, expect } from "vitest";
import { getCategoryColor, CATEGORY_PALETTE } from "../model/categoryColors";

describe("getCategoryColor", () => {
  it("returns a color from the palette", () => {
    const color = getCategoryColor("some-category-id");
    expect(CATEGORY_PALETTE).toContain(color);
  });

  it("returns same color for same categoryId", () => {
    expect(getCategoryColor("abc-123")).toBe(getCategoryColor("abc-123"));
  });

  it("different categoryIds can get different colors", () => {
    const colors = new Set(
      ["cat-1", "cat-2", "cat-3", "cat-4", "cat-5"].map(getCategoryColor)
    );
    expect(colors.size).toBeGreaterThan(1);
  });

  it("handles empty string", () => {
    const color = getCategoryColor("");
    expect(CATEGORY_PALETTE).toContain(color);
  });
});
