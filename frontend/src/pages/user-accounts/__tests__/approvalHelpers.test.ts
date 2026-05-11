import { describe, it, expect, vi, afterEach } from "vitest";

describe("countWeeklyProcessed", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  function countWeeklyProcessed(
    accounts: Array<{ approvedAt?: string | null }>,
  ): number {
    const now = new Date();
    const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    return accounts.filter((a) => {
      if (!a.approvedAt) return false;
      return new Date(a.approvedAt) >= sevenDaysAgo;
    }).length;
  }

  it("counts items with approvedAt within 7 days", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-03-19T12:00:00+09:00"));
    const accounts = [
      { approvedAt: "2026-03-19T10:00:00+09:00" },
      { approvedAt: "2026-03-13T10:00:00+09:00" },
      { approvedAt: "2026-03-10T10:00:00+09:00" },
      { approvedAt: null },
    ];
    expect(countWeeklyProcessed(accounts)).toBe(2);
  });

  it("returns 0 when no approvedAt within 7 days", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-03-19T12:00:00+09:00"));
    expect(
      countWeeklyProcessed([{ approvedAt: "2026-02-01T10:00:00+09:00" }]),
    ).toBe(0);
  });

  it("returns 0 for empty array", () => {
    expect(countWeeklyProcessed([])).toBe(0);
  });
});
