import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SourcesTableFilters } from "../SourcesTableFilters";
import type { Category } from "@/types/category";

const CATEGORIES: Category[] = [
  { id: "cat-1", name: "AI" } as Category,
  { id: "cat-2", name: "반도체" } as Category,
];

function baseProps(overrides: Record<string, unknown> = {}) {
  return {
    query: "",
    onQueryChange: vi.fn(),
    categoryId: "",
    onCategoryChange: vi.fn(),
    region: "",
    onRegionChange: vi.fn(),
    categories: CATEGORIES,
    filteredCount: 10,
    totalCount: 20,
    ...overrides,
  };
}

describe("SourcesTableFilters", () => {
  describe("compliance filter dropdown", () => {
    it("렌더링되지 않음: onComplianceChange 가 없으면 저작권 셀렉트는 표시하지 않는다", () => {
      render(<SourcesTableFilters {...baseProps()} />);
      expect(screen.queryByText(/전체 저작권/i)).toBeNull();
    });

    it("렌더링됨: onComplianceChange 가 있으면 저작권 셀렉트를 표시한다", () => {
      render(
        <SourcesTableFilters
          {...baseProps({ onComplianceChange: vi.fn(), complianceStatus: "" })}
        />,
      );
      expect(screen.getByText(/저작권/i)).toBeInTheDocument();
    });

    it("현재 선택된 상태가 셀렉트 placeholder 대신 표시된다", () => {
      render(
        <SourcesTableFilters
          {...baseProps({ onComplianceChange: vi.fn(), complianceStatus: "EXPIRED" })}
        />,
      );
      // Radix Select 은 값 인식을 위해 SelectValue 안에서 표시함
      expect(screen.getByText("만료")).toBeInTheDocument();
    });
  });

  describe("검색 입력", () => {
    it("사용자가 타이핑하면 onQueryChange 가 호출된다", () => {
      const onQueryChange = vi.fn();
      render(<SourcesTableFilters {...baseProps({ onQueryChange })} />);
      const input = screen.getByPlaceholderText(/소스명/);
      fireEvent.change(input, { target: { value: "naver" } });
      expect(onQueryChange).toHaveBeenCalledWith("naver");
    });
  });

  describe("카운트 표시", () => {
    it("filteredCount/totalCount 를 tabular-nums 로 표시한다", () => {
      render(<SourcesTableFilters {...baseProps({ filteredCount: 5, totalCount: 42 })} />);
      expect(screen.getByText("5/42")).toBeInTheDocument();
    });
  });
});
