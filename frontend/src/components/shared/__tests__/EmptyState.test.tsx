import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmptyState } from "@/components/shared/EmptyState";

describe("EmptyState", () => {
  it("title을 렌더링해야 한다", () => {
    render(<EmptyState title="데이터 없음" />);
    expect(screen.getByText("데이터 없음")).toBeInTheDocument();
  });

  it("description이 있으면 렌더링해야 한다", () => {
    render(<EmptyState title="제목" description="설명" />);
    expect(screen.getByText("설명")).toBeInTheDocument();
  });

  it("description이 없으면 렌더링하지 않아야 한다", () => {
    render(<EmptyState title="제목" />);
    expect(screen.queryByText("설명")).not.toBeInTheDocument();
  });
});
