import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { BlockedChannelFilterBar } from "../BlockedChannelFilterBar";

describe("BlockedChannelFilterBar (smoke)", () => {
  it("검색 입력과 타입 칩을 렌더링하고 콜백으로 변경을 전달한다", () => {
    const onSearchChange = vi.fn();
    const onTypeFilterChange = vi.fn();
    render(
      <BlockedChannelFilterBar
        search=""
        typeFilter="all"
        sort="recent"
        onSearchChange={onSearchChange}
        onTypeFilterChange={onTypeFilterChange}
        onSortChange={() => {}}
      />,
    );

    const searchInput = screen.getByLabelText("차단 목록 검색") as HTMLInputElement;
    fireEvent.change(searchInput, { target: { value: "spam" } });
    expect(onSearchChange).toHaveBeenCalledWith("spam");

    // "공개" 칩 클릭 → typeFilter "public"
    fireEvent.click(screen.getByRole("button", { name: "공개" }));
    expect(onTypeFilterChange).toHaveBeenCalledWith("public");
  });
});
