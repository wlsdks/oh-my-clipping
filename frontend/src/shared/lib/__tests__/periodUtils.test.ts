import { describe, expect, it } from "vitest";
import { getPeriodRange, getPreviousPeriodRange, formatPeriodLabel } from "../periodUtils";

// 고정 날짜: 2026-03-12 (목요일)
const THURSDAY = new Date(2026, 2, 12);
// 고정 날짜: 2026-03-15 (일요일)
const SUNDAY = new Date(2026, 2, 15);
// 고정 날짜: 2026-03-09 (월요일)
const MONDAY = new Date(2026, 2, 9);
// 고정 날짜: 2026-03-01 (일요일)
const MARCH_FIRST = new Date(2026, 2, 1);
// 고정 날짜: 2026-01-15 (목요일) — 1월 31일 → 2월 28일 경계 테스트
const JAN_15 = new Date(2026, 0, 15);

describe("getPeriodRange", () => {
  describe("this-week", () => {
    it("목요일 기준: 이번주 월요일 ~ 오늘", () => {
      const result = getPeriodRange("this-week", THURSDAY);
      expect(result).toEqual({ from: "2026-03-09", to: "2026-03-12" });
    });

    it("일요일 기준: 이번주 월요일 ~ 오늘(일요일)", () => {
      const result = getPeriodRange("this-week", SUNDAY);
      expect(result).toEqual({ from: "2026-03-09", to: "2026-03-15" });
    });

    it("월요일 기준: from과 to가 같은 날", () => {
      const result = getPeriodRange("this-week", MONDAY);
      expect(result).toEqual({ from: "2026-03-09", to: "2026-03-09" });
    });
  });

  describe("last-week", () => {
    it("목요일 기준: 지난주 월~일", () => {
      const result = getPeriodRange("last-week", THURSDAY);
      expect(result).toEqual({ from: "2026-03-02", to: "2026-03-08" });
    });

    it("월요일 기준: 지난주 월~일", () => {
      const result = getPeriodRange("last-week", MONDAY);
      expect(result).toEqual({ from: "2026-03-02", to: "2026-03-08" });
    });
  });

  describe("this-month", () => {
    it("3월 12일 기준: 3/1 ~ 3/12", () => {
      const result = getPeriodRange("this-month", THURSDAY);
      expect(result).toEqual({ from: "2026-03-01", to: "2026-03-12" });
    });

    it("3월 1일 기준: from과 to가 같은 날", () => {
      const result = getPeriodRange("this-month", MARCH_FIRST);
      expect(result).toEqual({ from: "2026-03-01", to: "2026-03-01" });
    });
  });

  describe("last-month", () => {
    it("3월 기준: 2월 전체 (2026년은 평년이므로 28일)", () => {
      const result = getPeriodRange("last-month", THURSDAY);
      expect(result).toEqual({ from: "2026-02-01", to: "2026-02-28" });
    });

    it("1월 기준: 지난해 12월 전체", () => {
      const result = getPeriodRange("last-month", JAN_15);
      expect(result).toEqual({ from: "2025-12-01", to: "2025-12-31" });
    });
  });
});

describe("getPreviousPeriodRange", () => {
  describe("this-week", () => {
    it("목요일 기준: 지난주 월~목 (동일 길이)", () => {
      const result = getPreviousPeriodRange("this-week", THURSDAY);
      // this-week = 3/9(월) ~ 3/12(목) → 이전 = 3/2(월) ~ 3/5(목)
      expect(result).toEqual({ from: "2026-03-02", to: "2026-03-05" });
    });

    it("월요일 기준: 지난주 월~월 (하루)", () => {
      const result = getPreviousPeriodRange("this-week", MONDAY);
      // this-week = 3/9 ~ 3/9 → 이전 = 3/2 ~ 3/2
      expect(result).toEqual({ from: "2026-03-02", to: "2026-03-02" });
    });
  });

  describe("last-week", () => {
    it("목요일 기준: 지지난주 월~일", () => {
      const result = getPreviousPeriodRange("last-week", THURSDAY);
      // last-week = 3/2~3/8 → 이전 = 2/23~3/1
      expect(result).toEqual({ from: "2026-02-23", to: "2026-03-01" });
    });
  });

  describe("this-month", () => {
    it("3월 12일 기준: 지난달 1~12일 (동일 길이)", () => {
      const result = getPreviousPeriodRange("this-month", THURSDAY);
      // this-month = 3/1~3/12 → 이전 = 2/1~2/12
      expect(result).toEqual({ from: "2026-02-01", to: "2026-02-12" });
    });

    it("3월 31일 기준: 2월은 28일까지만 → 2/1~2/28로 클램핑", () => {
      const march31 = new Date(2026, 2, 31);
      const result = getPreviousPeriodRange("this-month", march31);
      expect(result).toEqual({ from: "2026-02-01", to: "2026-02-28" });
    });
  });

  describe("last-month", () => {
    it("3월 기준: 지지난달(1월) 전체", () => {
      const result = getPreviousPeriodRange("last-month", THURSDAY);
      expect(result).toEqual({ from: "2026-01-01", to: "2026-01-31" });
    });

    it("1월 기준: 지지난달(11월) 전체", () => {
      const result = getPreviousPeriodRange("last-month", JAN_15);
      expect(result).toEqual({ from: "2025-11-01", to: "2025-11-30" });
    });
  });
});

describe("formatPeriodLabel", () => {
  it("같은 달 범위: 3/3 ~ 3/9", () => {
    expect(formatPeriodLabel("2026-03-03", "2026-03-09")).toBe("3/3 ~ 3/9");
  });

  it("다른 달 범위: 2/23 ~ 3/1", () => {
    expect(formatPeriodLabel("2026-02-23", "2026-03-01")).toBe("2/23 ~ 3/1");
  });

  it("같은 날: 3/1 ~ 3/1", () => {
    expect(formatPeriodLabel("2026-03-01", "2026-03-01")).toBe("3/1 ~ 3/1");
  });

  it("연도 경계: 12/25 ~ 1/5", () => {
    expect(formatPeriodLabel("2025-12-25", "2026-01-05")).toBe("12/25 ~ 1/5");
  });
});
