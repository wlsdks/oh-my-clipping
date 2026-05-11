import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { useReadTracking } from "../useReadTracking";

describe("useReadTracking", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  // debounce된 persist를 즉시 flush (200ms)
  function flushPersist() {
    act(() => {
      vi.advanceTimersByTime(300);
    });
  }

  it("새 아이템은 미확인 상태다", () => {
    const { result } = renderHook(() => useReadTracking());
    expect(result.current.isRead("item-1")).toBe(false);
  });

  it("markAsRead 호출 후 확인 상태로 변경된다", () => {
    const { result } = renderHook(() => useReadTracking());
    act(() => result.current.markAsRead("item-1"));
    expect(result.current.isRead("item-1")).toBe(true);
  });

  it("여러 아이템을 한번에 읽음 처리한다", () => {
    const { result } = renderHook(() => useReadTracking());
    act(() => result.current.markManyAsRead(["a", "b", "c"]));
    expect(result.current.isRead("a")).toBe(true);
    expect(result.current.isRead("b")).toBe(true);
    expect(result.current.isRead("c")).toBe(true);
  });

  it("localStorage에 저장되어 새로고침 후에도 유지된다", () => {
    const { result, unmount } = renderHook(() => useReadTracking());
    act(() => result.current.markAsRead("item-1"));
    flushPersist();
    unmount();
    const { result: result2 } = renderHook(() => useReadTracking());
    expect(result2.current.isRead("item-1")).toBe(true);
  });

  it("debounce 이전에 unmount되면 cleanup 시점에 flush된다", () => {
    const { result, unmount } = renderHook(() => useReadTracking());
    act(() => result.current.markAsRead("item-unmount"));
    // debounce 시간 경과 전에 unmount → effect cleanup의 flush가 호출되어야 함
    unmount();
    const { result: result2 } = renderHook(() => useReadTracking());
    expect(result2.current.isRead("item-unmount")).toBe(true);
  });

  it("7일 이상 된 키를 자동 정리한다", () => {
    const eightDaysAgo = new Date();
    eightDaysAgo.setDate(eightDaysAgo.getDate() - 8);
    const oldKey = `reviewed-items-${eightDaysAgo.toLocaleDateString("sv-SE", {
      timeZone: "Asia/Seoul"
    })}`;
    localStorage.setItem(oldKey, JSON.stringify(["old-item"]));
    renderHook(() => useReadTracking());
    expect(localStorage.getItem(oldKey)).toBeNull();
  });

  it("KST 기준 날짜 키를 사용한다 (UTC 00시~09시에도 오늘 키 유지)", () => {
    // UTC 00:30(= KST 09:30)이어도 KST 기준 "오늘"로 저장되어 읽음 표시가 유지되어야 한다.
    // 이전 버전은 UTC 기반이라 UTC 자정 직후 키가 바뀌어 매일 KST 09시에 초기화되는 버그가 있었다.
    const utcDate = new Date();
    const kstDate = utcDate.toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" });
    const expectedKey = `reviewed-items-${kstDate}`;

    const { result } = renderHook(() => useReadTracking());
    act(() => result.current.markAsRead("kst-item"));
    flushPersist();

    expect(localStorage.getItem(expectedKey)).toContain("kst-item");
  });
});
