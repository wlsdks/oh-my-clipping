import { describe, expect, it } from "vitest";
import {
  getPeriodRange,
  getPreviousPeriodRange,
  periodToDays,
  formatPeriodLabel,
} from "@/utils/periodUtils";

/* ── getPeriodRange ── */

describe("getPeriodRange", () => {
  describe("this-week", () => {
    it("월요일에는 from === to === 오늘", () => {
      // 2026-03-16 is Monday
      const monday = new Date(2026, 2, 16);
      const result = getPeriodRange("this-week", monday);
      expect(result).toEqual({ from: "2026-03-16", to: "2026-03-16" });
    });

    it("수요일에는 from === 월요일, to === 수요일", () => {
      const wed = new Date(2026, 2, 18);
      const result = getPeriodRange("this-week", wed);
      expect(result).toEqual({ from: "2026-03-16", to: "2026-03-18" });
    });

    it("일요일에는 from === 이전 월요일, to === 일요일", () => {
      const sun = new Date(2026, 2, 22);
      const result = getPeriodRange("this-week", sun);
      expect(result).toEqual({ from: "2026-03-16", to: "2026-03-22" });
    });

    it("토요일 경우에도 올바른 월요일을 찾는다", () => {
      const sat = new Date(2026, 2, 21);
      const result = getPeriodRange("this-week", sat);
      expect(result).toEqual({ from: "2026-03-16", to: "2026-03-21" });
    });
  });

  describe("last-week", () => {
    it("이번주 수요일 기준으로 지난주 월~일을 반환한다", () => {
      const wed = new Date(2026, 2, 18);
      const result = getPeriodRange("last-week", wed);
      expect(result).toEqual({ from: "2026-03-09", to: "2026-03-15" });
    });

    it("이번주 월요일 기준으로도 정확한 지난주를 반환한다", () => {
      const monday = new Date(2026, 2, 16);
      const result = getPeriodRange("last-week", monday);
      expect(result).toEqual({ from: "2026-03-09", to: "2026-03-15" });
    });

    it("일요일 기준으로도 정확한 지난주를 반환한다", () => {
      const sun = new Date(2026, 2, 22);
      const result = getPeriodRange("last-week", sun);
      expect(result).toEqual({ from: "2026-03-09", to: "2026-03-15" });
    });
  });

  describe("this-month", () => {
    it("3월 18일 기준으로 3/1 ~ 3/18을 반환한다", () => {
      const now = new Date(2026, 2, 18);
      const result = getPeriodRange("this-month", now);
      expect(result).toEqual({ from: "2026-03-01", to: "2026-03-18" });
    });

    it("1일인 경우 from === to === 1일", () => {
      const first = new Date(2026, 2, 1);
      const result = getPeriodRange("this-month", first);
      expect(result).toEqual({ from: "2026-03-01", to: "2026-03-01" });
    });

    it("월말(31일) 기준에도 올바른 범위를 반환한다", () => {
      const end = new Date(2026, 0, 31);
      const result = getPeriodRange("this-month", end);
      expect(result).toEqual({ from: "2026-01-01", to: "2026-01-31" });
    });
  });

  describe("last-month", () => {
    it("3월 기준으로 2월 전체를 반환한다", () => {
      const now = new Date(2026, 2, 18);
      const result = getPeriodRange("last-month", now);
      expect(result).toEqual({ from: "2026-02-01", to: "2026-02-28" });
    });

    it("1월 기준으로 12월 전체를 반환한다 (연도 경계)", () => {
      const jan = new Date(2026, 0, 15);
      const result = getPeriodRange("last-month", jan);
      expect(result).toEqual({ from: "2025-12-01", to: "2025-12-31" });
    });

    it("3월 기준 윤년 2월(29일)을 올바르게 처리한다", () => {
      // 2024년은 윤년
      const mar = new Date(2024, 2, 10);
      const result = getPeriodRange("last-month", mar);
      expect(result).toEqual({ from: "2024-02-01", to: "2024-02-29" });
    });

    it("비윤년 3월 기준으로 2월(28일)을 반환한다", () => {
      const mar = new Date(2025, 2, 10);
      const result = getPeriodRange("last-month", mar);
      expect(result).toEqual({ from: "2025-02-01", to: "2025-02-28" });
    });
  });

  describe("날짜 포맷", () => {
    it("한 자리 월/일에 0을 패딩한다", () => {
      const now = new Date(2026, 0, 5); // 1월 5일
      const result = getPeriodRange("this-month", now);
      expect(result.from).toBe("2026-01-01");
      expect(result.to).toBe("2026-01-05");
    });
  });
});

/* ── getPreviousPeriodRange ── */

describe("getPreviousPeriodRange", () => {
  describe("this-week → 지난주 동일 요일 범위", () => {
    it("수요일 기준으로 지난주 월~수를 반환한다", () => {
      const wed = new Date(2026, 2, 18);
      const result = getPreviousPeriodRange("this-week", wed);
      expect(result).toEqual({ from: "2026-03-09", to: "2026-03-11" });
    });

    it("월요일 기준으로 지난주 월~월을 반환한다", () => {
      const monday = new Date(2026, 2, 16);
      const result = getPreviousPeriodRange("this-week", monday);
      expect(result).toEqual({ from: "2026-03-09", to: "2026-03-09" });
    });
  });

  describe("last-week → 2주 전 월~일", () => {
    it("수요일 기준으로 2주 전 월~일을 반환한다", () => {
      const wed = new Date(2026, 2, 18);
      const result = getPreviousPeriodRange("last-week", wed);
      expect(result).toEqual({ from: "2026-03-02", to: "2026-03-08" });
    });
  });

  describe("this-month → 전월 동일 날짜 범위", () => {
    it("3월 18일 기준으로 2/1 ~ 2/18을 반환한다", () => {
      const now = new Date(2026, 2, 18);
      const result = getPreviousPeriodRange("this-month", now);
      expect(result).toEqual({ from: "2026-02-01", to: "2026-02-18" });
    });

    it("3월 31일 기준으로 2/1 ~ 2/28을 반환한다 (전월 일수 부족)", () => {
      const end = new Date(2026, 2, 31);
      const result = getPreviousPeriodRange("this-month", end);
      expect(result).toEqual({ from: "2026-02-01", to: "2026-02-28" });
    });

    it("윤년 3월 31일 기준으로 2/1 ~ 2/29을 반환한다", () => {
      const end = new Date(2024, 2, 31);
      const result = getPreviousPeriodRange("this-month", end);
      expect(result).toEqual({ from: "2024-02-01", to: "2024-02-29" });
    });

    it("1월 기준으로 12월 범위를 반환한다 (연도 경계)", () => {
      const jan15 = new Date(2026, 0, 15);
      const result = getPreviousPeriodRange("this-month", jan15);
      expect(result).toEqual({ from: "2025-12-01", to: "2025-12-15" });
    });
  });

  describe("last-month → 2개월 전 전체", () => {
    it("3월 기준으로 1월 전체를 반환한다", () => {
      const now = new Date(2026, 2, 18);
      const result = getPreviousPeriodRange("last-month", now);
      expect(result).toEqual({ from: "2026-01-01", to: "2026-01-31" });
    });

    it("2월 기준으로 12월을 반환한다 (연도 경계)", () => {
      const feb = new Date(2026, 1, 10);
      const result = getPreviousPeriodRange("last-month", feb);
      expect(result).toEqual({ from: "2025-12-01", to: "2025-12-31" });
    });
  });
});

/* ── periodToDays ── */

describe("periodToDays", () => {
  it("this-week: 1~7일 범위를 반환한다", () => {
    const result = periodToDays("this-week");
    expect(result).toBeGreaterThanOrEqual(1);
    expect(result).toBeLessThanOrEqual(7);
  });

  it("last-week: 항상 7일을 반환한다", () => {
    const result = periodToDays("last-week");
    expect(result).toBe(7);
  });

  it("this-month: 1일 이상 31일 이하", () => {
    const result = periodToDays("this-month");
    expect(result).toBeGreaterThanOrEqual(1);
    expect(result).toBeLessThanOrEqual(31);
  });

  it("last-month: 28~31일 범위", () => {
    const result = periodToDays("last-month");
    expect(result).toBeGreaterThanOrEqual(28);
    expect(result).toBeLessThanOrEqual(31);
  });
});

/* ── formatPeriodLabel ── */

describe("formatPeriodLabel", () => {
  it("같은 달 범위를 올바르게 포맷한다", () => {
    const result = formatPeriodLabel("2026-03-01", "2026-03-18");
    expect(result).toBe("3/1 ~ 3/18");
  });

  it("다른 달에 걸친 범위를 포맷한다", () => {
    const result = formatPeriodLabel("2026-02-25", "2026-03-05");
    expect(result).toBe("2/25 ~ 3/5");
  });

  it("연도가 달라도 월/일만 표시한다", () => {
    const result = formatPeriodLabel("2025-12-29", "2026-01-04");
    expect(result).toBe("12/29 ~ 1/4");
  });

  it("from === to 인 경우에도 정상 동작한다", () => {
    const result = formatPeriodLabel("2026-03-16", "2026-03-16");
    expect(result).toBe("3/16 ~ 3/16");
  });
});
