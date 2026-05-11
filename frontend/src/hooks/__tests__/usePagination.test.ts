import { describe, it, expect } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { usePagination } from "@/hooks/usePagination";

describe("usePagination", () => {
  it("기본 page는 0이어야 한다", () => {
    const { result } = renderHook(() => usePagination());
    expect(result.current.page).toBe(0);
  });

  it("setPage로 페이지를 변경할 수 있어야 한다", () => {
    const { result } = renderHook(() => usePagination());
    act(() => result.current.setPage(3));
    expect(result.current.page).toBe(3);
  });

  it("기본 size는 20이어야 한다", () => {
    const { result } = renderHook(() => usePagination());
    expect(result.current.size).toBe(20);
  });

  it("initialSize를 변경할 수 있어야 한다", () => {
    const { result } = renderHook(() => usePagination(10));
    expect(result.current.size).toBe(10);
  });
});
