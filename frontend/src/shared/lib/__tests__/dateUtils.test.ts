import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { formatRelativeTime } from "../dateUtils";

describe("formatRelativeTime", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    // 기준 시각: 2026-03-13T12:00:00Z
    vi.setSystemTime(new Date("2026-03-13T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("1분 미만이면 '방금'을 반환한다", () => {
    expect(formatRelativeTime("2026-03-13T11:59:30Z")).toBe("방금");
    expect(formatRelativeTime("2026-03-13T12:00:00Z")).toBe("방금");
  });

  it("분 단위 차이를 'N분 전'으로 표시한다", () => {
    expect(formatRelativeTime("2026-03-13T11:55:00Z")).toBe("5분 전");
    expect(formatRelativeTime("2026-03-13T11:01:00Z")).toBe("59분 전");
  });

  it("시간 단위 차이를 'N시간 전'으로 표시한다", () => {
    expect(formatRelativeTime("2026-03-13T11:00:00Z")).toBe("1시간 전");
    expect(formatRelativeTime("2026-03-13T00:00:00Z")).toBe("12시간 전");
  });

  it("일 단위 차이를 'N일 전'으로 표시한다", () => {
    expect(formatRelativeTime("2026-03-12T12:00:00Z")).toBe("1일 전");
    expect(formatRelativeTime("2026-03-06T12:00:00Z")).toBe("7일 전");
  });

  it("미래 타임스탬프(시계 오차)에는 '방금'을 반환한다", () => {
    expect(formatRelativeTime("2026-03-13T12:05:00Z")).toBe("방금");
    expect(formatRelativeTime("2026-03-14T00:00:00Z")).toBe("방금");
  });
});
