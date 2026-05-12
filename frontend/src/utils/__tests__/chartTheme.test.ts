import { describe, it, expect } from "vitest";
import { chartColor, CHART_COLORS } from "../chartTheme";

describe("chartColor", () => {
  it("returns the first color at index 0", () => {
    expect(chartColor(0)).toBe(CHART_COLORS[0]);
  });

  it("returns correct color for each valid index", () => {
    for (let i = 0; i < CHART_COLORS.length; i++) {
      expect(chartColor(i)).toBe(CHART_COLORS[i]);
    }
  });

  it("returns the last color at the final index", () => {
    const lastIndex = CHART_COLORS.length - 1;
    expect(chartColor(lastIndex)).toBe(CHART_COLORS[lastIndex]);
  });

  it("wraps around when index exceeds array length", () => {
    expect(chartColor(CHART_COLORS.length)).toBe(CHART_COLORS[0]);
    expect(chartColor(CHART_COLORS.length + 1)).toBe(CHART_COLORS[1]);
  });

  it("wraps correctly for index = 2x array length", () => {
    const doubleLen = CHART_COLORS.length * 2;
    expect(chartColor(doubleLen)).toBe(CHART_COLORS[0]);
    expect(chartColor(doubleLen + 3)).toBe(CHART_COLORS[3]);
  });

  it("returns the first color again after a full cycle", () => {
    expect(chartColor(0)).toBe(chartColor(CHART_COLORS.length));
  });

  it("returns a string CSS chart token for any index", () => {
    expect(typeof chartColor(0)).toBe("string");
    expect(chartColor(0)).toMatch(/^var\(--chart-\d+\)$/);
    expect(chartColor(99)).toMatch(/^var\(--chart-\d+\)$/);
  });
});
