import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Banner } from "@/components/shared/Banner";

describe("Banner", () => {
  it("children을 렌더링해야 한다", () => {
    render(<Banner>알림 메시지</Banner>);
    expect(screen.getByText("알림 메시지")).toBeInTheDocument();
  });

  it("기본 variant는 info여야 한다", () => {
    const { container } = render(<Banner>메시지</Banner>);
    expect(container.firstChild).toHaveClass("bg-[var(--status-neutral-bg)]");
  });

  it("error variant에 danger 토큰 클래스를 가져야 한다", () => {
    const { container } = render(<Banner variant="error">에러</Banner>);
    expect(container.firstChild).toHaveClass("bg-[var(--status-danger-bg)]");
  });
});
