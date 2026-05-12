// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/kyInstance", () => ({
  api: {
    get: vi.fn(() => ({ json: vi.fn() })),
  },
}));

import { api } from "@/lib/kyInstance";
import { tokenHealthService } from "@/services/tokenHealthService";

const mockApiGet = vi.mocked(api.get);

describe("tokenHealthService.getStatus", () => {
  beforeEach(() => {
    mockApiGet.mockReset();
  });

  function mockResponse(json: () => Promise<unknown>): ReturnType<typeof api.get> {
    return { json } as ReturnType<typeof api.get>;
  }

  function mockJson(payload: unknown) {
    mockApiGet.mockReturnValue(mockResponse(vi.fn().mockResolvedValue(payload)));
  }

  it("`admin/system/token-health` 경로로 GET을 호출한다", async () => {
    mockJson({ slackBot: "ok", gemini: "ok", ok: true });

    await tokenHealthService.getStatus();

    expect(mockApiGet).toHaveBeenCalledWith("admin/system/token-health");
  });

  it("응답 JSON을 그대로 반환한다 (정상 상태)", async () => {
    const payload = { slackBot: "ok", gemini: "ok", ok: true };
    mockJson(payload);

    const result = await tokenHealthService.getStatus();

    expect(result).toEqual(payload);
  });

  it("장애 상태도 그대로 반환한다 (Slack EXPIRED + Gemini QUOTA)", async () => {
    const payload = { slackBot: "expired", gemini: "quota_exhausted", ok: false };
    mockJson(payload);

    const result = await tokenHealthService.getStatus();

    expect(result.slackBot).toBe("expired");
    expect(result.gemini).toBe("quota_exhausted");
    expect(result.ok).toBe(false);
  });

  it("네트워크 에러는 호출자에게 전파된다", async () => {
    mockApiGet.mockReturnValue(mockResponse(vi.fn().mockRejectedValue(new Error("network fail"))));

    await expect(tokenHealthService.getStatus()).rejects.toThrow("network fail");
  });
});
