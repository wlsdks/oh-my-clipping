import { describe, it, expect, beforeEach, vi } from "vitest";
import { useStaleEditStore } from "@/lib/staleEditBus";
import type { StaleEditInfo } from "@/shared/types/common";

const sampleInfo: StaleEditInfo = {
  code: "STALE_EDIT",
  latestUpdatedAt: "2026-04-17T10:00:00Z",
  latestEditorName: "김관리",
  changedFieldNames: ["name", "maxItems"]
};

describe("staleEditBus", () => {
  beforeEach(() => {
    useStaleEditStore.getState().clear();
  });

  it("초기 상태는 pending이 null이어야 한다", () => {
    const state = useStaleEditStore.getState();
    expect(state.pending).toBeNull();
    expect(state.reloadFn).toBeNull();
    expect(state.draftKey).toBeNull();
  });

  it("show 호출 시 pending과 reloadFn이 저장된다", () => {
    const reload = vi.fn();
    useStaleEditStore.getState().show(sampleInfo, reload);

    const state = useStaleEditStore.getState();
    expect(state.pending).toEqual(sampleInfo);
    expect(state.reloadFn).toBe(reload);
  });

  it("draftKey 옵션이 전달되면 저장된다", () => {
    useStaleEditStore.getState().show(sampleInfo, vi.fn(), { draftKey: "draft:persona:abc" });
    expect(useStaleEditStore.getState().draftKey).toBe("draft:persona:abc");
  });

  it("clear 호출 시 모든 필드가 초기화된다", () => {
    useStaleEditStore.getState().show(sampleInfo, vi.fn(), { draftKey: "draft:x:1" });
    useStaleEditStore.getState().clear();

    const state = useStaleEditStore.getState();
    expect(state.pending).toBeNull();
    expect(state.reloadFn).toBeNull();
    expect(state.draftKey).toBeNull();
  });

  it("show 재호출 시 이전 상태를 덮어쓴다", () => {
    const first = vi.fn();
    const second = vi.fn();
    const secondInfo: StaleEditInfo = { ...sampleInfo, latestEditorName: "이관리" };

    useStaleEditStore.getState().show(sampleInfo, first);
    useStaleEditStore.getState().show(secondInfo, second);

    const state = useStaleEditStore.getState();
    expect(state.pending).toEqual(secondInfo);
    expect(state.reloadFn).toBe(second);
  });
});
