import { describe, expect, it } from "vitest";
import { oldestIsoDate, isBadgeKind } from "../sidebarBadges";

describe("oldestIsoDate", () => {
  it("빈 배열은 null", () => {
    expect(oldestIsoDate([])).toBeNull();
  });

  it("단일 유효 값은 그대로 반환", () => {
    expect(oldestIsoDate(["2026-04-18T00:00:00Z"])).toBe("2026-04-18T00:00:00Z");
  });

  it("여러 값 중 가장 오래된 ISO 문자열 반환", () => {
    expect(
      oldestIsoDate([
        "2026-04-18T10:00:00Z",
        "2026-04-17T09:00:00Z",
        "2026-04-18T05:00:00Z",
      ])
    ).toBe("2026-04-17T09:00:00Z");
  });

  it("null / undefined / 빈 문자열 섞여도 무시", () => {
    expect(
      oldestIsoDate([null, undefined, "", "2026-04-18T09:00:00Z"])
    ).toBe("2026-04-18T09:00:00Z");
  });

  it("모두 null/undefined 이면 null", () => {
    expect(oldestIsoDate([null, undefined, ""])).toBeNull();
  });

  it("첫 원소가 malformed 여도 poisoning 되지 않는다 (NaN 가드)", () => {
    expect(
      oldestIsoDate(["not-a-date", "2026-04-18T09:00:00Z", "2026-04-17T09:00:00Z"])
    ).toBe("2026-04-17T09:00:00Z");
  });

  it("모두 malformed 이면 null", () => {
    expect(oldestIsoDate(["garbage", "also-bad"])).toBeNull();
  });
});

describe("isBadgeKind", () => {
  it("valid BadgeKind 문자열은 true", () => {
    expect(isBadgeKind("userAccounts")).toBe(true);
    expect(isBadgeKind("reviewQueue")).toBe(true);
    expect(isBadgeKind("subscriptions")).toBe(true);
    expect(isBadgeKind("delivery")).toBe(true);
    expect(isBadgeKind("pipeline")).toBe(true);
  });
  it("다른 문자열은 false", () => {
    expect(isBadgeKind("dashboard")).toBe(false);
    expect(isBadgeKind("auditLog")).toBe(false);
    expect(isBadgeKind("")).toBe(false);
  });
});
