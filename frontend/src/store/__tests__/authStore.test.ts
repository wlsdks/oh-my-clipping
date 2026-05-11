import { describe, it, expect, beforeEach } from "vitest";
import { authStore } from "@/store/authStore";

const mockUser = { id: "1", username: "dev.admin", displayName: "관리자", role: "ADMIN" as const, approvalStatus: "APPROVED" as const };

describe("authStore", () => {
  beforeEach(() => authStore.getState().logout());

  it("초기 상태는 로그아웃이어야 한다", () => {
    expect(authStore.getState().isLoggedIn).toBe(false);
    expect(authStore.getState().user).toBe(null);
  });

  it("login 후 isLoggedIn이 true여야 한다", () => {
    authStore.getState().login(mockUser);
    expect(authStore.getState().isLoggedIn).toBe(true);
    // V129 normalization: FK 필드(`departmentId`/`teamId` 등)가 없으면 `null` 로 채워진다.
    expect(authStore.getState().user).toMatchObject(mockUser);
    expect(authStore.getState().user?.departmentId).toBe(null);
    expect(authStore.getState().user?.teamId).toBe(null);
  });

  it("logout 후 상태가 초기화되어야 한다", () => {
    authStore.getState().login(mockUser);
    authStore.getState().logout();
    expect(authStore.getState().isLoggedIn).toBe(false);
    expect(authStore.getState().user).toBe(null);
  });
});
