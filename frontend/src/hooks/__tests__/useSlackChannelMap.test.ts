import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { createQueryClientWrapper } from "@/test/queryClient";

vi.mock("@/services/runtimeService", () => ({
  runtimeService: {
    listAdminSlackChannels: vi.fn(),
  },
}));

import { useSlackChannelMap } from "@/hooks/useSlackChannelMap";
import { runtimeService } from "@/services/runtimeService";

describe("useSlackChannelMap.formatChannel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(runtimeService.listAdminSlackChannels).mockImplementation((type) => {
      if (type === "public_channel") {
        return Promise.resolve({
          channels: [{ id: "C123ABC", name: "general", isPrivate: false }],
          slackConnectRequired: false,
        });
      }
      return Promise.resolve({
        channels: [{ id: "G456DEF", name: "private-room", isPrivate: true }],
        slackConnectRequired: false,
      });
    });
  });

  async function loadHook() {
    const hook = renderHook(() => useSlackChannelMap(), {
      wrapper: createQueryClientWrapper(),
    });
    await waitFor(() => {
      expect(hook.result.current.hasLoaded).toBe(true);
    });
    return hook;
  }

  it("매핑된 공개 채널은 #이름 으로 표시한다", async () => {
    const { result } = await loadHook();
    expect(result.current.formatChannel("C123ABC")).toBe("#general");
  });

  it("매핑된 비공개 채널은 #이름 으로 표시한다", async () => {
    const { result } = await loadHook();
    expect(result.current.formatChannel("G456DEF")).toBe("#private-room");
  });

  it("D로 시작하는 채널 ID 는 'DM (개인 메시지)'", async () => {
    const { result } = await loadHook();
    expect(result.current.formatChannel("D789XYZ")).toBe("DM (개인 메시지)");
  });

  it("매핑 실패 시 '알 수 없는 채널'", async () => {
    const { result } = await loadHook();
    expect(result.current.formatChannel("CUNKNOWN")).toBe("알 수 없는 채널");
  });

  describe("기본 동작 (dmIfBlank 미지정)", () => {
    it("빈 문자열은 '-' (카테고리 컨텍스트)", async () => {
      const { result } = await loadHook();
      expect(result.current.formatChannel("")).toBe("-");
    });

    it("null 은 '-'", async () => {
      const { result } = await loadHook();
      expect(result.current.formatChannel(null)).toBe("-");
    });

    it("undefined 는 '-'", async () => {
      const { result } = await loadHook();
      expect(result.current.formatChannel(undefined)).toBe("-");
    });
  });

  describe("dmIfBlank=true (사용자 요청 컨텍스트)", () => {
    it("빈 문자열은 'Slack DM'", async () => {
      const { result } = await loadHook();
      expect(result.current.formatChannel("", { dmIfBlank: true })).toBe(
        "Slack DM"
      );
    });

    it("null 은 'Slack DM'", async () => {
      const { result } = await loadHook();
      expect(result.current.formatChannel(null, { dmIfBlank: true })).toBe(
        "Slack DM"
      );
    });

    it("D-prefix 채널은 dmIfBlank 와 무관하게 'DM (개인 메시지)'", async () => {
      const { result } = await loadHook();
      expect(
        result.current.formatChannel("D789XYZ", { dmIfBlank: true })
      ).toBe("DM (개인 메시지)");
    });

    it("매핑된 채널은 dmIfBlank 와 무관하게 #이름", async () => {
      const { result } = await loadHook();
      expect(
        result.current.formatChannel("C123ABC", { dmIfBlank: true })
      ).toBe("#general");
    });
  });
});
