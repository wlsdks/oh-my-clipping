import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useDebounce } from "@/hooks/useDebounce";

describe("useDebounce", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("지연 전에는 초기값을 반환해야 한다", () => {
    const { result } = renderHook(() => useDebounce("초기값", 300));
    expect(result.current).toBe("초기값");
  });

  it("지연 후에 최신값을 반환해야 한다", async () => {
    const { result, rerender } = renderHook(({ value }) => useDebounce(value, 300), {
      initialProps: { value: "초기값" }
    });
    rerender({ value: "변경값" });
    expect(result.current).toBe("초기값");
    act(() => vi.advanceTimersByTime(300));
    expect(result.current).toBe("변경값");
  });
});
