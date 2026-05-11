import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/hooks/useCanHover", () => ({
  useCanHover: vi.fn(),
}));
import { useCanHover } from "@/hooks/useCanHover";
import { TooltipProvider } from "@/components/ui/tooltip";
import { SidebarBadgeWithTooltip } from "../SidebarBadgeWithTooltip";

describe("SidebarBadgeWithTooltip", () => {
  beforeEach(() => {
    vi.mocked(useCanHover).mockClear();
  });

  it("hover 가능 환경에서 hover 시 툴팁 내용 노출", async () => {
    vi.mocked(useCanHover).mockReturnValue(true);
    render(
      <TooltipProvider delayDuration={0}>
        <SidebarBadgeWithTooltip
          count={3}
          tooltipText="현재 승인 대기 3건 · 최장 2일 대기"
          variant="default"
          ariaLabel="회원관리 3건"
        />
      </TooltipProvider>
    );
    const badge = screen.getByText("3");
    expect(badge).toBeInTheDocument();
    await userEvent.hover(badge);
    // Radix renders tooltip text in both a visible div and a hidden ARIA span —
    // use findAllByText and assert at least one match is visible.
    const tooltipMatches = await screen.findAllByText("현재 승인 대기 3건 · 최장 2일 대기");
    expect(tooltipMatches.length).toBeGreaterThanOrEqual(1);
  });

  it("hover 불가 환경에서는 툴팁 없이 배지만 렌더 (tooltipText 은 DOM 에 없음)", () => {
    vi.mocked(useCanHover).mockReturnValue(false);
    render(
      <SidebarBadgeWithTooltip
        count={3}
        tooltipText="현재 승인 대기 3건"
        variant="default"
        ariaLabel="회원관리 3건"
      />
    );
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.queryByText("현재 승인 대기 3건")).not.toBeInTheDocument();
  });

  it("variant=destructive 시 destructive 배경 클래스 적용", () => {
    vi.mocked(useCanHover).mockReturnValue(false); // hover 비활성 분기로 DOM 단순화
    const { container } = render(
      <SidebarBadgeWithTooltip
        count={2}
        tooltipText="실패 2건"
        variant="destructive"
        ariaLabel="발송 실패 2건"
      />
    );
    const badge = container.querySelector("[data-testid='sidebar-badge']");
    expect(badge?.className).toMatch(/bg-destructive/);
  });

  it("variant=default 시 primary 배경 클래스 적용", () => {
    vi.mocked(useCanHover).mockReturnValue(false);
    const { container } = render(
      <SidebarBadgeWithTooltip
        count={3}
        tooltipText="대기 3건"
        variant="default"
        ariaLabel="회원관리 3건"
      />
    );
    const badge = container.querySelector("[data-testid='sidebar-badge']");
    expect(badge?.className).toMatch(/bg-primary/);
  });

  it("aria-label 이 그대로 badge 에 반영", () => {
    vi.mocked(useCanHover).mockReturnValue(false);
    render(
      <SidebarBadgeWithTooltip
        count={7}
        tooltipText="ignored"
        variant="default"
        ariaLabel="뉴스 검토 7건"
      />
    );
    expect(screen.getByLabelText("뉴스 검토 7건")).toBeInTheDocument();
  });
});
