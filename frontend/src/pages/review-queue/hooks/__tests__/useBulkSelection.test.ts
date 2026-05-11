import { renderHook, act } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { useBulkSelection } from "../useBulkSelection";

describe("useBulkSelection", () => {
  const items = ["a", "b", "c", "d", "e"];

  it("초기 상태는 빈 Set이다", () => {
    const { result } = renderHook(() => useBulkSelection(items));
    expect(result.current.checkedIds.size).toBe(0);
  });

  it("toggle로 단일 아이템을 선택/해제한다", () => {
    const { result } = renderHook(() => useBulkSelection(items));
    act(() => result.current.toggle("b"));
    expect(result.current.checkedIds.has("b")).toBe(true);
    act(() => result.current.toggle("b"));
    expect(result.current.checkedIds.has("b")).toBe(false);
  });

  it("toggleRange로 범위 선택한다", () => {
    const { result } = renderHook(() => useBulkSelection(items));
    act(() => result.current.toggle("b")); // anchor = b
    act(() => result.current.toggleRange("d")); // b~d
    expect(result.current.checkedIds).toEqual(new Set(["b", "c", "d"]));
  });

  it("selectAll로 전체 선택한다", () => {
    const { result } = renderHook(() => useBulkSelection(items));
    act(() => result.current.selectAll());
    expect(result.current.checkedIds).toEqual(new Set(items));
  });

  it("clearAll로 전체 해제한다", () => {
    const { result } = renderHook(() => useBulkSelection(items));
    act(() => result.current.selectAll());
    act(() => result.current.clearAll());
    expect(result.current.checkedIds.size).toBe(0);
  });

  it("items 변경(내용 다름) 시 lastCheckedId가 리셋된다", () => {
    const { result, rerender } = renderHook(
      ({ ids }) => useBulkSelection(ids),
      { initialProps: { ids: items } }
    );
    act(() => result.current.toggle("c")); // anchor = c
    rerender({ ids: ["x", "y"] });
    act(() => result.current.toggleRange("y")); // no anchor → just toggle y
    expect(result.current.checkedIds).toEqual(new Set(["y"]));
  });

  // 엣지 1-2: 새 array 참조지만 내용 동일 — 선택 유지되어야 함
  it("itemIds가 새 참조이지만 동일 내용이면 선택을 유지한다", () => {
    const { result, rerender } = renderHook(
      ({ ids }) => useBulkSelection(ids),
      { initialProps: { ids: ["a", "b", "c"] } }
    );
    act(() => result.current.toggle("b"));
    expect(result.current.checkedIds.has("b")).toBe(true);

    // 매번 새 array 참조로 rerender — react-query의 select 결과 패턴
    rerender({ ids: ["a", "b", "c"] });
    expect(result.current.checkedIds.has("b")).toBe(true);
    rerender({ ids: ["a", "b", "c"] });
    expect(result.current.checkedIds.has("b")).toBe(true);
  });

  it("itemIds에서 빠진 id는 자동으로 체크 해제된다", () => {
    const { result, rerender } = renderHook(
      ({ ids }) => useBulkSelection(ids),
      { initialProps: { ids: ["a", "b", "c"] } }
    );
    act(() => {
      result.current.toggle("a");
      result.current.toggle("c");
    });
    expect(result.current.checkedIds).toEqual(new Set(["a", "c"]));
    rerender({ ids: ["a", "b"] }); // c 빠짐
    expect(result.current.checkedIds).toEqual(new Set(["a"]));
  });

  it("toggleRange 후 anchor가 마지막 클릭 id로 갱신된다", () => {
    const { result } = renderHook(() => useBulkSelection(items));
    act(() => result.current.toggle("a")); // anchor = a
    act(() => result.current.toggleRange("c")); // a~c, anchor = c
    act(() => result.current.toggleRange("e")); // c~e
    expect(result.current.checkedIds).toEqual(new Set(["a", "b", "c", "d", "e"]));
  });

  it("순서만 바뀐 새 array로 rerender되면 선택은 유지하되 anchor는 리셋된다", () => {
    const { result, rerender } = renderHook(
      ({ ids }) => useBulkSelection(ids),
      { initialProps: { ids: ["a", "b", "c"] } }
    );
    act(() => result.current.toggle("a"));
    rerender({ ids: ["c", "b", "a"] });
    // a는 여전히 선택 상태
    expect(result.current.checkedIds.has("a")).toBe(true);
    // anchor는 리셋되었으므로 toggleRange는 단순 toggle로 동작
    act(() => result.current.toggleRange("c"));
    expect(result.current.checkedIds.has("c")).toBe(true);
  });
});
