// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/kyInstance", () => ({
  api: {
    get: vi.fn(() => ({
      json: vi.fn()
    }))
  }
}));

import { authService } from "@/services/authService";

describe("authService.logout", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("서버 리다이렉트를 따르지 않고 로그아웃 POST만 호출해야 한다", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await authService.logout();

    expect(fetchMock).toHaveBeenCalledWith(
      "/logout",
      expect.objectContaining({
        method: "POST",
        credentials: "include",
        headers: {
          "X-Logout-Mode": "api",
          "ngrok-skip-browser-warning": "true"
        }
      })
    );
  });

  it("서버 5xx 응답이면 에러를 던져야 한다", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 500 })));

    await expect(authService.logout()).rejects.toThrow("로그아웃에 실패했어요");
  });
});
