import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { createQueryClientWrapper } from "@/test/queryClient";

// useEditingPresence 가 참조하는 서비스 싱글톤을 mocking.
vi.mock("@/services/editingPresenceService", () => ({
  editingPresenceService: {
    heartbeat: vi.fn().mockResolvedValue(""),
    release: vi.fn().mockResolvedValue(""),
    listActive: vi.fn().mockResolvedValue([])
  }
}));

// import 는 mock 선언 뒤에 수행한다 (vi.mock 은 hoist 되지만 참조는 이후 시점).
import { useEditingPresence } from "@/hooks/useEditingPresence";
import { editingPresenceService } from "@/services/editingPresenceService";

async function advancePresenceClock(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe("useEditingPresence", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("enabled=true 로 mount 되면 즉시 heartbeat 를 1회 호출한다", async () => {
    renderHook(
      () =>
        useEditingPresence({
          resourceType: "persona",
          resourceId: "p-1",
          enabled: true
        }),
      { wrapper: createQueryClientWrapper() }
    );

    await waitFor(() => {
      expect(editingPresenceService.heartbeat).toHaveBeenCalledWith("persona", "p-1");
    });
  });

  it("30초 경과 시 추가 heartbeat 을 호출한다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderHook(
      () =>
        useEditingPresence({
          resourceType: "persona",
          resourceId: "p-1",
          enabled: true
        }),
      { wrapper: createQueryClientWrapper() }
    );

    // 즉시 호출 1회
    await vi.waitFor(() => {
      expect(editingPresenceService.heartbeat).toHaveBeenCalledTimes(1);
    });

    await advancePresenceClock(30_000);

    await vi.waitFor(() => {
      expect(editingPresenceService.heartbeat).toHaveBeenCalledTimes(2);
    });
  });

  it("enabled=false 이면 heartbeat/polling 을 시작하지 않는다", () => {
    renderHook(
      () =>
        useEditingPresence({
          resourceType: "persona",
          resourceId: "p-1",
          enabled: false
        }),
      { wrapper: createQueryClientWrapper() }
    );

    expect(editingPresenceService.heartbeat).not.toHaveBeenCalled();
    expect(editingPresenceService.listActive).not.toHaveBeenCalled();
  });

  it("resourceId 가 없으면 아무 것도 호출하지 않는다", () => {
    renderHook(
      () =>
        useEditingPresence({
          resourceType: "persona",
          resourceId: null,
          enabled: true
        }),
      { wrapper: createQueryClientWrapper() }
    );

    expect(editingPresenceService.heartbeat).not.toHaveBeenCalled();
    expect(editingPresenceService.listActive).not.toHaveBeenCalled();
  });

  it("unmount 시 release 를 호출한다", async () => {
    const { unmount } = renderHook(
      () =>
        useEditingPresence({
          resourceType: "persona",
          resourceId: "p-1",
          enabled: true
        }),
      { wrapper: createQueryClientWrapper() }
    );

    await waitFor(() => {
      expect(editingPresenceService.heartbeat).toHaveBeenCalledTimes(1);
    });

    unmount();

    expect(editingPresenceService.release).toHaveBeenCalledWith("persona", "p-1");
  });
});
