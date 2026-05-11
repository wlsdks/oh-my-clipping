import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useReviewSelection } from "../model/useReviewSelection";

function fireKey(key: string) {
  window.dispatchEvent(new KeyboardEvent("keydown", { key }));
}

describe("useReviewSelection", () => {
  const ids = ["a", "b", "c", "d"];
  const onAction = vi.fn();

  beforeEach(() => {
    onAction.mockClear();
  });

  it("initializes with first item selected when autoSelect is true", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    expect(result.current.selectedId).toBe("a");
  });

  it("initializes with null when autoSelect is false", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: false })
    );
    expect(result.current.selectedId).toBeNull();
  });

  it("navigates down with ArrowDown / j", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    act(() => fireKey("ArrowDown"));
    expect(result.current.selectedId).toBe("b");
    act(() => fireKey("j"));
    expect(result.current.selectedId).toBe("c");
  });

  it("navigates up with ArrowUp / k", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    act(() => fireKey("ArrowDown"));
    act(() => fireKey("ArrowDown"));
    expect(result.current.selectedId).toBe("c");
    act(() => fireKey("ArrowUp"));
    expect(result.current.selectedId).toBe("b");
    act(() => fireKey("k"));
    expect(result.current.selectedId).toBe("a");
  });

  it("does not go below last item", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    act(() => { for (let i = 0; i < 10; i++) fireKey("ArrowDown"); });
    expect(result.current.selectedId).toBe("d");
  });

  it("does not go above first item", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    act(() => fireKey("ArrowUp"));
    expect(result.current.selectedId).toBe("a");
  });

  it("s key calls onAction with approve", () => {
    renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    act(() => fireKey("s"));
    expect(onAction).toHaveBeenCalledWith("a", "approve");
  });

  it("x key calls onAction with exclude", () => {
    renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    act(() => fireKey("x"));
    expect(onAction).toHaveBeenCalledWith("a", "exclude");
  });

  it("Escape clears selection", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    act(() => fireKey("Escape"));
    expect(result.current.selectedId).toBeNull();
  });

  it("does not fire shortcuts when input is focused", () => {
    const input = document.createElement("input");
    document.body.appendChild(input);
    input.focus();
    renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    act(() => fireKey("s"));
    expect(onAction).not.toHaveBeenCalled();
    document.body.removeChild(input);
  });

  it("selectNext moves to next item", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: true })
    );
    act(() => result.current.selectNext());
    expect(result.current.selectedId).toBe("b");
  });

  it("selectNext at end stays on last item", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ["only"], onAction, autoSelect: true })
    );
    act(() => result.current.selectNext());
    expect(result.current.selectedId).toBe("only");
  });

  it("setSelectedId allows manual selection", () => {
    const { result } = renderHook(() =>
      useReviewSelection({ itemIds: ids, onAction, autoSelect: false })
    );
    act(() => result.current.setSelectedId("c"));
    expect(result.current.selectedId).toBe("c");
  });

  it("resets to first item when itemIds change", () => {
    const { result, rerender } = renderHook(
      ({ itemIds }) => useReviewSelection({ itemIds, onAction, autoSelect: true }),
      { initialProps: { itemIds: ids } }
    );
    act(() => fireKey("ArrowDown"));
    expect(result.current.selectedId).toBe("b");
    rerender({ itemIds: ["x", "y"] });
    expect(result.current.selectedId).toBe("x");
  });
});
