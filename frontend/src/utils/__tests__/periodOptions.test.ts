import { describe, expect, it, vi, afterEach } from "vitest";
import { getCompetitorPeriodOptions, getPeriodDays } from "@/utils/periodOptions";

/* ── getCompetitorPeriodOptions ── */

describe("getCompetitorPeriodOptions", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("항상 3개의 옵션을 반환한다", () => {
    const options = getCompetitorPeriodOptions();
    expect(options).toHaveLength(3);
  });

  it("키가 today, this-week, this-month 순서다", () => {
    const options = getCompetitorPeriodOptions();
    expect(options.map((o) => o.key)).toEqual(["today", "this-week", "this-month"]);
  });

  it("라벨이 오늘, 이번 주, 이번 달이다", () => {
    const options = getCompetitorPeriodOptions();
    expect(options.map((o) => o.label)).toEqual(["오늘", "이번 주", "이번 달"]);
  });

  it("today는 항상 days === 1이다", () => {
    const options = getCompetitorPeriodOptions();
    expect(options[0].days).toBe(1);
  });

  it("월요일에는 this-week days === 1이다", () => {
    // 2026-03-16 is Monday
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 16));

    const options = getCompetitorPeriodOptions();
    expect(options[1].days).toBe(1);
  });

  it("수요일에는 this-week days === 3이다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 18)); // Wednesday

    const options = getCompetitorPeriodOptions();
    expect(options[1].days).toBe(3);
  });

  it("일요일에는 this-week days === 7이다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 22)); // Sunday

    const options = getCompetitorPeriodOptions();
    expect(options[1].days).toBe(7);
  });

  it("토요일에는 this-week days === 6이다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 21)); // Saturday

    const options = getCompetitorPeriodOptions();
    expect(options[1].days).toBe(6);
  });

  it("3월 1일에는 this-month days === 1이다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 1));

    const options = getCompetitorPeriodOptions();
    expect(options[2].days).toBe(1);
  });

  it("3월 15일에는 this-month days === 15이다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 15));

    const options = getCompetitorPeriodOptions();
    expect(options[2].days).toBe(15);
  });

  it("1월 31일에는 this-month days === 31이다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 31));

    const options = getCompetitorPeriodOptions();
    expect(options[2].days).toBe(31);
  });
});

/* ── getPeriodDays ── */

describe("getPeriodDays", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("today는 항상 1을 반환한다", () => {
    expect(getPeriodDays("today")).toBe(1);
  });

  it("this-week: 월요일에 1을 반환한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 16)); // Monday

    expect(getPeriodDays("this-week")).toBe(1);
  });

  it("this-week: 금요일에 5를 반환한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 20)); // Friday

    expect(getPeriodDays("this-week")).toBe(5);
  });

  it("this-week: 일요일에 7을 반환한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 22)); // Sunday

    expect(getPeriodDays("this-week")).toBe(7);
  });

  it("this-month: 15일에 15를 반환한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 15));

    expect(getPeriodDays("this-month")).toBe(15);
  });

  it("this-month: 1일에 1을 반환한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 1));

    expect(getPeriodDays("this-month")).toBe(1);
  });

  it("this-month: 28일에 28을 반환한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 1, 28)); // Feb 28

    expect(getPeriodDays("this-month")).toBe(28);
  });
});
