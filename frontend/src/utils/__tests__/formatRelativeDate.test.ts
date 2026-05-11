import { describe, it, expect, vi, afterEach } from "vitest";
import { formatRelativeDate } from "../date";

describe("formatRelativeDate", () => {
  afterEach(() => { vi.useRealTimers(); });

  function freezeAt(iso: string) {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(iso));
  }

  it("returns '—' for null/undefined", () => {
    expect(formatRelativeDate(null)).toBe("—");
    expect(formatRelativeDate(undefined)).toBe("—");
    expect(formatRelativeDate("")).toBe("—");
  });

  it("returns '오늘' for same day", () => {
    freezeAt("2026-03-19T15:00:00+09:00");
    expect(formatRelativeDate("2026-03-19T09:00:00+09:00")).toBe("오늘");
  });

  it("returns '어제' for 1 day ago", () => {
    freezeAt("2026-03-19T15:00:00+09:00");
    expect(formatRelativeDate("2026-03-18T09:00:00+09:00")).toBe("어제");
  });

  it("returns 'N일 전' for 2-6 days", () => {
    freezeAt("2026-03-19T15:00:00+09:00");
    expect(formatRelativeDate("2026-03-14T09:00:00+09:00")).toBe("5일 전");
  });

  it("returns 'N주 전' for 7-29 days", () => {
    freezeAt("2026-03-19T15:00:00+09:00");
    expect(formatRelativeDate("2026-03-05T09:00:00+09:00")).toBe("2주 전");
  });

  it("returns 'N개월 전' for 30-364 days", () => {
    freezeAt("2026-03-19T15:00:00+09:00");
    expect(formatRelativeDate("2026-01-10T09:00:00+09:00")).toBe("2개월 전");
  });

  it("returns 'N년 전' for 365+ days", () => {
    freezeAt("2026-03-19T15:00:00+09:00");
    expect(formatRelativeDate("2024-03-19T09:00:00+09:00")).toBe("2년 전");
  });

  it("returns '—' for invalid date", () => {
    expect(formatRelativeDate("not-a-date")).toBe("—");
  });

  it("returns '오늘' for future date (timezone edge case)", () => {
    freezeAt("2026-03-19T00:30:00+09:00");
    // UTC 기준으로는 아직 3/18이지만 KST 기준으로는 3/19
    expect(formatRelativeDate("2026-03-19T08:00:00+09:00")).toBe("오늘");
  });
});
