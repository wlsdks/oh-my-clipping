import { describe, it, expect } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useModalState } from "@/hooks/useModalState";

describe("useModalState", () => {
  it("기본값은 닫힌 상태여야 한다", () => {
    const { result } = renderHook(() => useModalState());
    expect(result.current.isOpen).toBe(false);
  });

  it("open() 호출 시 isOpen이 true가 되어야 한다", () => {
    const { result } = renderHook(() => useModalState());
    act(() => result.current.open());
    expect(result.current.isOpen).toBe(true);
  });

  it("close() 호출 시 isOpen이 false가 되어야 한다", () => {
    const { result } = renderHook(() => useModalState(true));
    act(() => result.current.close());
    expect(result.current.isOpen).toBe(false);
  });

  it("toggle() 이 open 상태를 전환해야 한다", () => {
    const { result } = renderHook(() => useModalState());
    act(() => result.current.toggle());
    expect(result.current.isOpen).toBe(true);
    act(() => result.current.toggle());
    expect(result.current.isOpen).toBe(false);
  });
});
