import { describe, expect, it } from "vitest";
import { toInt, toFloat, currentYearMonth } from "../number";

describe("toInt", () => {
  it("parses normal integer string", () => {
    expect(toInt("42", 0)).toBe(42);
  });

  it("returns fallback for NaN", () => {
    expect(toInt("abc", -1)).toBe(-1);
  });

  it("returns fallback for null", () => {
    expect(toInt(null, 0)).toBe(0);
  });

  it("truncates float string to integer", () => {
    expect(toInt("3.7", 0)).toBe(3);
  });

  it("returns fallback for Infinity", () => {
    expect(toInt(Infinity, 99)).toBe(99);
  });

  it("returns fallback for undefined", () => {
    expect(toInt(undefined, 7)).toBe(7);
  });

  it("parses negative string correctly", () => {
    expect(toInt("-10", 0)).toBe(-10);
  });

  it("returns fallback for whitespace-only string", () => {
    expect(toInt("   ", 0)).toBe(0);
  });
});

describe("toFloat", () => {
  it("parses float string", () => {
    expect(toFloat("3.14", 0)).toBeCloseTo(3.14);
  });

  it("returns fallback for non-numeric", () => {
    expect(toFloat("xyz", 5)).toBe(5);
  });

  it("returns fallback for null", () => {
    expect(toFloat(null, 2.5)).toBe(2.5);
  });

  it("returns fallback for undefined", () => {
    expect(toFloat(undefined, 1.1)).toBe(1.1);
  });

  it("returns fallback for Infinity", () => {
    expect(toFloat(Infinity, 0)).toBe(0);
  });

  it("returns fallback for -Infinity", () => {
    expect(toFloat(-Infinity, 99)).toBe(99);
  });

  it("parses integer string as float (no decimal part)", () => {
    expect(toFloat("42", 0)).toBe(42);
  });
});

describe("currentYearMonth", () => {
  it("returns YYYY-MM format", () => {
    const result = currentYearMonth();
    expect(result).toMatch(/^\d{4}-\d{2}$/);
  });
});
