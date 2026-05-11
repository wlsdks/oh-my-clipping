import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useMediaQuery } from "../useMediaQuery";
import { useCanHover } from "../useCanHover";

vi.mock("../useMediaQuery", () => ({
  useMediaQuery: vi.fn(),
}));

describe("useCanHover", () => {
  beforeEach(() => {
    vi.mocked(useMediaQuery).mockClear();
  });

  it("hover 가능 환경에서 true 반환", () => {
    vi.mocked(useMediaQuery).mockReturnValue(true);
    const { result } = renderHook(() => useCanHover());
    expect(result.current).toBe(true);
    expect(useMediaQuery).toHaveBeenCalledWith("(hover: hover)");
  });

  it("hover 불가 환경에서 false 반환", () => {
    vi.mocked(useMediaQuery).mockReturnValue(false);
    const { result } = renderHook(() => useCanHover());
    expect(result.current).toBe(false);
  });

  it("렌더당 useMediaQuery 1회만 호출", () => {
    vi.mocked(useMediaQuery).mockReturnValue(true);
    renderHook(() => useCanHover());
    expect(useMediaQuery).toHaveBeenCalledTimes(1);
  });
});
