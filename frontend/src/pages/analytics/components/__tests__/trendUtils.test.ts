import { describe, it, expect } from "vitest";
import { pct, trendDir } from "../trendUtils";

describe("pct", () => {
  it("returns positive percentage for increase", () => {
    expect(pct(150, 100)).toBe(50);
  });

  it("returns negative percentage for decrease", () => {
    expect(pct(50, 100)).toBe(-50);
  });

  it("rounds to nearest integer", () => {
    expect(pct(1, 3)).toBe(-67);
    expect(pct(2, 3)).toBe(-33);
  });

  it("returns 100 when previous is 0 and current > 0", () => {
    expect(pct(42, 0)).toBe(100);
  });

  it("returns 0 when both previous and current are 0", () => {
    expect(pct(0, 0)).toBe(0);
  });

  it("returns 0 when current is 0 and previous is 0", () => {
    expect(pct(0, 0)).toBe(0);
  });

  it("returns -100 when current is 0 and previous is positive", () => {
    expect(pct(0, 100)).toBe(-100);
  });

  it("handles large numbers correctly", () => {
    expect(pct(1_000_000, 500_000)).toBe(100);
    // (999999-1000000)/1000000 * 100 = -0.01 → rounds to -0
    expect(pct(999_999, 1_000_000)).toBe(-0);
  });

  it("returns 100 for any positive current when previous is 0", () => {
    expect(pct(1, 0)).toBe(100);
    expect(pct(999_999, 0)).toBe(100);
  });
});

describe("trendDir", () => {
  it('returns "up" when current > previous', () => {
    expect(trendDir(10, 5)).toBe("up");
  });

  it('returns "down" when current < previous', () => {
    expect(trendDir(5, 10)).toBe("down");
  });

  it('returns "neutral" when current equals previous', () => {
    expect(trendDir(10, 10)).toBe("neutral");
  });

  it('returns "neutral" when both are 0', () => {
    expect(trendDir(0, 0)).toBe("neutral");
  });

  it('returns "up" when current is positive and previous is 0', () => {
    expect(trendDir(1, 0)).toBe("up");
  });

  it('returns "down" when current is 0 and previous is positive', () => {
    expect(trendDir(0, 1)).toBe("down");
  });

  it("handles large numbers", () => {
    expect(trendDir(1_000_000, 999_999)).toBe("up");
    expect(trendDir(999_999, 1_000_000)).toBe("down");
  });
});
