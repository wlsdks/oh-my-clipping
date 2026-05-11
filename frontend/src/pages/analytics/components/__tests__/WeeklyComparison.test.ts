import { describe, it, expect } from "vitest";
import { pct, trendDir } from "../trendUtils";

describe("pct", () => {
  it("양의 변화율을 계산해야 한다", () => {
    expect(pct(150, 100)).toBe(50);
  });

  it("음의 변화율을 계산해야 한다", () => {
    expect(pct(80, 100)).toBe(-20);
  });

  it("이전 값이 0이고 현재 값이 양수이면 100을 반환해야 한다", () => {
    expect(pct(50, 0)).toBe(100);
  });

  it("이전 값과 현재 값 모두 0이면 0을 반환해야 한다", () => {
    expect(pct(0, 0)).toBe(0);
  });

  it("변화 없으면 0을 반환해야 한다", () => {
    expect(pct(100, 100)).toBe(0);
  });
});

describe("trendDir", () => {
  it("current가 크면 up을 반환해야 한다", () => {
    expect(trendDir(200, 100)).toBe("up");
  });

  it("current가 작으면 down을 반환해야 한다", () => {
    expect(trendDir(50, 100)).toBe("down");
  });

  it("같으면 neutral을 반환해야 한다", () => {
    expect(trendDir(100, 100)).toBe("neutral");
  });

  it("0과 0은 neutral을 반환해야 한다", () => {
    expect(trendDir(0, 0)).toBe("neutral");
  });
});
