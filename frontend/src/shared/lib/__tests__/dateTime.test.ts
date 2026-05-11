import { describe, expect, it } from "vitest";
import { formatKoreanDateTime, formatKoreanDate } from "../dateTime";

describe("formatKoreanDateTime", () => {
  it("returns '-' for null/undefined", () => {
    expect(formatKoreanDateTime(null)).toBe("-");
    expect(formatKoreanDateTime(undefined)).toBe("-");
  });

  it("returns original value for invalid date string", () => {
    expect(formatKoreanDateTime("not-a-date")).toBe("not-a-date");
  });

  it("formats ISO string to KST datetime", () => {
    const result = formatKoreanDateTime("2024-06-15T03:30:00Z");
    // KST = UTC+9, so 03:30 UTC => 12:30 KST
    expect(result).toMatch(/2024-06-15 12:30:00/);
  });

  it("returns '-' for empty string", () => {
    expect(formatKoreanDateTime("")).toBe("-");
  });
});

describe("formatKoreanDate", () => {
  it("returns '-' for null", () => {
    expect(formatKoreanDate(null)).toBe("-");
  });

  it("formats ISO string to date only", () => {
    const result = formatKoreanDate("2024-06-15T03:30:00Z");
    expect(result).toMatch(/2024\. 06\. 15/);
  });

  it("returns '-' for undefined", () => {
    expect(formatKoreanDate(undefined)).toBe("-");
  });

  it("returns '-' for empty string", () => {
    expect(formatKoreanDate("")).toBe("-");
  });

  it("returns original string for invalid date", () => {
    expect(formatKoreanDate("not-a-date")).toBe("not-a-date");
  });

  it("midnight boundary: UTC 15:00 appears as next day in KST", () => {
    // 2024-06-15 15:00 UTC = 2024-06-16 00:00 KST
    const result = formatKoreanDate("2024-06-15T15:00:00Z");
    expect(result).toMatch(/2024\. 06\. 16/);
  });
});
