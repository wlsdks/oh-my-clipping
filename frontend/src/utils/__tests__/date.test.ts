import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import {
  formatKoreanDateTime,
  formatKoreanDate,
  relativeTime,
  currentYearMonthKst,
  prevYearMonth,
  generatePastMonthsKst,
  getMonthRangeKst,
} from "@/utils/date";

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
    const result = formatKoreanDate("2024-06-15T15:00:00Z");
    expect(result).toMatch(/2024\. 06\. 16/);
  });
});

describe("relativeTime", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-03-19T12:00:00Z"));
  });
  afterEach(() => vi.useRealTimers());

  it("returns '방금 전' for less than 1 minute ago", () => {
    expect(relativeTime("2026-03-19T11:59:30Z")).toBe("방금 전");
  });
  it("returns 'N분 전' for minutes", () => {
    expect(relativeTime("2026-03-19T11:30:00Z")).toBe("30분 전");
  });
  it("returns 'N시간 전' for hours", () => {
    expect(relativeTime("2026-03-19T09:00:00Z")).toBe("3시간 전");
  });
  it("returns 'N일 전' for days", () => {
    expect(relativeTime("2026-03-17T12:00:00Z")).toBe("2일 전");
  });
  it("returns 'N개월 전' for months", () => {
    expect(relativeTime("2026-01-15T12:00:00Z")).toBe("2개월 전");
  });
  it("returns '-' for null/undefined", () => {
    expect(relativeTime(null)).toBe("-");
    expect(relativeTime(undefined)).toBe("-");
  });
  it("returns value as-is for invalid date", () => {
    expect(relativeTime("not-a-date")).toBe("not-a-date");
  });
});

describe("prevYearMonth", () => {
  it("같은 연도 내에서는 월만 1 감소", () => {
    expect(prevYearMonth("2026-04")).toBe("2026-03");
  });

  it("1월 → 전년 12월로 넘어간다 (연도 경계)", () => {
    expect(prevYearMonth("2026-01")).toBe("2025-12");
  });

  it("한 자리 월도 두 자리 패딩으로 반환한다", () => {
    expect(prevYearMonth("2026-09")).toBe("2026-08");
    expect(prevYearMonth("2026-10")).toBe("2026-09");
  });
});

describe("currentYearMonthKst", () => {
  it("KST 기준으로 연-월을 반환한다 (UTC 15:30 → KST 다음날)", () => {
    const utcJan31 = new Date("2026-01-31T15:30:00Z"); // KST 2026-02-01 00:30
    expect(currentYearMonthKst(utcJan31)).toBe("2026-02");
  });

  it("UTC 정오는 KST 21시 같은 달", () => {
    const utcMidDay = new Date("2026-04-15T12:00:00Z");
    expect(currentYearMonthKst(utcMidDay)).toBe("2026-04");
  });
});

describe("generatePastMonthsKst", () => {
  it("count=3이면 현재월 + 이전 2개월, 최신 순", () => {
    const anchor = new Date("2026-04-17T12:00:00Z");
    const result = generatePastMonthsKst(3, anchor);
    expect(result.map((r) => r.value)).toEqual(["2026-04", "2026-03", "2026-02"]);
    expect(result[0].label).toBe("2026년 4월");
  });

  it("연도 경계를 넘어 과거로 이동해도 정확하다", () => {
    const anchor = new Date("2026-02-01T12:00:00Z");
    const result = generatePastMonthsKst(4, anchor);
    expect(result.map((r) => r.value)).toEqual(["2026-02", "2026-01", "2025-12", "2025-11"]);
  });

  it("count=1이면 현재월만", () => {
    const anchor = new Date("2026-04-01T00:00:00Z");
    expect(generatePastMonthsKst(1, anchor)).toEqual([{ value: "2026-04", label: "2026년 4월" }]);
  });
});

describe("getMonthRangeKst", () => {
  it("이번 달이면 to는 오늘(KST)", () => {
    const anchor = new Date("2026-04-17T12:00:00Z");
    expect(getMonthRangeKst("2026-04", anchor)).toEqual({
      from: "2026-04-01",
      to: "2026-04-17",
    });
  });

  it("과거 달이면 to는 그 달의 마지막 날", () => {
    const anchor = new Date("2026-04-17T12:00:00Z");
    expect(getMonthRangeKst("2026-03", anchor)).toEqual({
      from: "2026-03-01",
      to: "2026-03-31",
    });
  });

  it("2월의 마지막 날을 정확히 계산한다 (윤년/평년)", () => {
    const anchor = new Date("2026-04-01T12:00:00Z");
    expect(getMonthRangeKst("2024-02", anchor).to).toBe("2024-02-29");
    expect(getMonthRangeKst("2025-02", anchor).to).toBe("2025-02-28");
  });

  it("UTC 자정 근방 KST 경계 — UTC로는 다른 날이지만 KST로 같은 날", () => {
    const beforeKstMidnight = new Date("2026-04-17T14:50:00Z");
    expect(getMonthRangeKst("2026-04", beforeKstMidnight).to).toBe("2026-04-17");
    const afterKstMidnight = new Date("2026-04-17T15:10:00Z");
    expect(getMonthRangeKst("2026-04", afterKstMidnight).to).toBe("2026-04-18");
  });
});
