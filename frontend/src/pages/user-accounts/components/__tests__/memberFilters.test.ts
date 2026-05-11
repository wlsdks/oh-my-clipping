import { describe, it, expect } from "vitest";
import {
  getActivityStatus,
  matchesFilters,
  isDefaultFilters,
  extractDepartments,
  type MemberFilters,
  DEFAULT_FILTERS,
} from "../memberFilters";
import type { UserAccountApproval } from "@/types/user";

// 테스트용 회원 데이터 팩토리
function makeMember(overrides: Partial<UserAccountApproval> = {}): UserAccountApproval {
  return {
    id: "1",
    username: "testuser",
    displayName: "테스트",
    department: "개발",
    isActive: true,
    approvalStatus: "APPROVED",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    lastLoginAt: null,
    subscriptionCount: 0,
    role: "USER",
    ...overrides,
  };
}

describe("getActivityStatus", () => {
  it("lastLoginAt이 null이면 NEVER를 반환한다", () => {
    expect(getActivityStatus(null)).toBe("NEVER");
  });

  it("lastLoginAt이 undefined이면 NEVER를 반환한다", () => {
    expect(getActivityStatus(undefined)).toBe("NEVER");
  });

  it("유효하지 않은 날짜 문자열이면 NEVER를 반환한다", () => {
    expect(getActivityStatus("not-a-date")).toBe("NEVER");
  });

  it("7일 이내 로그인이면 ACTIVE_7D를 반환한다", () => {
    const now = new Date();
    const recent = new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000).toISOString();
    expect(getActivityStatus(recent)).toBe("ACTIVE_7D");
  });

  it("정확히 7일 전 로그인이면 SEMI_ACTIVE_30D를 반환한다", () => {
    const now = new Date();
    const exactly7d = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString();
    expect(getActivityStatus(exactly7d)).toBe("SEMI_ACTIVE_30D");
  });

  it("7~30일 전 로그인이면 SEMI_ACTIVE_30D를 반환한다", () => {
    const now = new Date();
    const twoWeeksAgo = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000).toISOString();
    expect(getActivityStatus(twoWeeksAgo)).toBe("SEMI_ACTIVE_30D");
  });

  it("정확히 30일 전 로그인이면 INACTIVE_30D를 반환한다", () => {
    const now = new Date();
    const exactly30d = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString();
    expect(getActivityStatus(exactly30d)).toBe("INACTIVE_30D");
  });

  it("30일 이상 미로그인이면 INACTIVE_30D를 반환한다", () => {
    const now = new Date();
    const old = new Date(now.getTime() - 45 * 24 * 60 * 60 * 1000).toISOString();
    expect(getActivityStatus(old)).toBe("INACTIVE_30D");
  });
});

describe("matchesFilters", () => {
  it("DEFAULT_FILTERS이면 모든 회원이 통과한다", () => {
    const member = makeMember();
    expect(matchesFilters(member, DEFAULT_FILTERS)).toBe(true);
  });

  it("부서 필터가 설정되면 해당 부서만 통과한다", () => {
    const member = makeMember({ department: "마케팅" });
    expect(matchesFilters(member, { ...DEFAULT_FILTERS, department: "개발" })).toBe(false);
    expect(matchesFilters(member, { ...DEFAULT_FILTERS, department: "마케팅" })).toBe(true);
  });

  it("부서가 null인 회원은 '__none__' 필터에 통과한다", () => {
    const member = makeMember({ department: null });
    expect(matchesFilters(member, { ...DEFAULT_FILTERS, department: "__none__" })).toBe(true);
    expect(matchesFilters(member, { ...DEFAULT_FILTERS, department: "개발" })).toBe(false);
  });

  it("역할 필터가 USER이면 USER만 통과한다", () => {
    const admin = makeMember({ role: "ADMIN" });
    const user = makeMember({ role: "USER" });
    const filters = { ...DEFAULT_FILTERS, role: "USER" as const };
    expect(matchesFilters(admin, filters)).toBe(false);
    expect(matchesFilters(user, filters)).toBe(true);
  });

  it("구독 필터 HAS이면 구독 1건 이상만 통과한다", () => {
    const noSub = makeMember({ subscriptionCount: 0 });
    const hasSub = makeMember({ subscriptionCount: 3 });
    const filters = { ...DEFAULT_FILTERS, subscription: "HAS" as const };
    expect(matchesFilters(noSub, filters)).toBe(false);
    expect(matchesFilters(hasSub, filters)).toBe(true);
  });

  it("활동 상태 필터 ACTIVE이면 30일 이내 로그인만 통과한다", () => {
    const now = new Date();
    const recent = makeMember({
      lastLoginAt: new Date(now.getTime() - 2 * 24 * 60 * 60 * 1000).toISOString(),
    });
    const old = makeMember({
      lastLoginAt: new Date(now.getTime() - 40 * 24 * 60 * 60 * 1000).toISOString(),
    });
    const neverLoggedIn = makeMember({ lastLoginAt: null });
    const filters = { ...DEFAULT_FILTERS, activityStatus: "ACTIVE" as const };
    expect(matchesFilters(recent, filters)).toBe(true);
    expect(matchesFilters(old, filters)).toBe(false);
    expect(matchesFilters(neverLoggedIn, filters)).toBe(false);
  });

  it("활동 상태 필터 INACTIVE이면 30일 이상 미로그인 또는 미로그인 회원만 통과한다", () => {
    const now = new Date();
    const recent = makeMember({
      lastLoginAt: new Date(now.getTime() - 2 * 24 * 60 * 60 * 1000).toISOString(),
    });
    const old = makeMember({
      lastLoginAt: new Date(now.getTime() - 40 * 24 * 60 * 60 * 1000).toISOString(),
    });
    const neverLoggedIn = makeMember({ lastLoginAt: null });
    const filters = { ...DEFAULT_FILTERS, activityStatus: "INACTIVE" as const };
    expect(matchesFilters(recent, filters)).toBe(false);
    expect(matchesFilters(old, filters)).toBe(true);
    expect(matchesFilters(neverLoggedIn, filters)).toBe(true);
  });

  it("복수 필터를 AND 조건으로 적용한다", () => {
    const now = new Date();
    const member = makeMember({
      department: "개발",
      role: "USER",
      subscriptionCount: 2,
      lastLoginAt: new Date(now.getTime() - 2 * 24 * 60 * 60 * 1000).toISOString(),
    });
    const filters: MemberFilters = {
      department: "개발",
      role: "USER",
      activityStatus: "ACTIVE",
      subscription: "HAS",
    };
    expect(matchesFilters(member, filters)).toBe(true);
  });
});

describe("isDefaultFilters", () => {
  it("DEFAULT_FILTERS이면 true를 반환한다", () => {
    expect(isDefaultFilters(DEFAULT_FILTERS)).toBe(true);
  });

  it("어떤 필터라도 변경되면 false를 반환한다", () => {
    expect(isDefaultFilters({ ...DEFAULT_FILTERS, role: "ADMIN" })).toBe(false);
    expect(isDefaultFilters({ ...DEFAULT_FILTERS, department: "개발" })).toBe(false);
    expect(isDefaultFilters({ ...DEFAULT_FILTERS, activityStatus: "ACTIVE" })).toBe(false);
    expect(isDefaultFilters({ ...DEFAULT_FILTERS, subscription: "HAS" })).toBe(false);
  });
});

describe("extractDepartments", () => {
  it("빈 배열이면 빈 배열을 반환한다", () => {
    expect(extractDepartments([])).toEqual([]);
  });

  it("중복 부서를 제거하고 정렬된 목록을 반환한다", () => {
    const members = [
      makeMember({ department: "마케팅" }),
      makeMember({ department: "개발" }),
      makeMember({ department: "마케팅" }),
      makeMember({ department: "인사" }),
    ];
    expect(extractDepartments(members)).toEqual(["개발", "마케팅", "인사"]);
  });

  it("department가 null인 회원은 목록에 포함하지 않는다", () => {
    const members = [
      makeMember({ department: "개발" }),
      makeMember({ department: null }),
      makeMember({ department: undefined }),
    ];
    expect(extractDepartments(members)).toEqual(["개발"]);
  });
});
