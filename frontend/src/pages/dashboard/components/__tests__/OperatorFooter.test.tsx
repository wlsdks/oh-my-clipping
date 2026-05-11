import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../hooks/useOperatorFooterData");

import { useOperatorFooterData } from "../../hooks/useOperatorFooterData";
import { OperatorFooter } from "../OperatorFooter";

const mockHook = vi.mocked(useOperatorFooterData);

function renderFooter() {
  return render(
    <MemoryRouter>
      <OperatorFooter />
    </MemoryRouter>,
  );
}

function baseData(
  overrides?: Partial<ReturnType<typeof useOperatorFooterData>>,
): ReturnType<typeof useOperatorFooterData> {
  return {
    activeSubscriptions: {
      activeCount: 42,
      newThisWeek: 5,
      deactivatedThisWeek: 2,
      netChange: 3,
    },
    showGettingStarted: false,
    isLoading: false,
    error: null,
    ...overrides,
  };
}

describe("OperatorFooter", () => {
  beforeEach(() => vi.clearAllMocks());

  it("활성 구독 렌더 + netChange 부호 (양수 → +)", () => {
    mockHook.mockReturnValue(baseData());
    renderFooter();
    expect(screen.getByText(/활성 구독 42개/)).toBeInTheDocument();
    expect(screen.getByText(/순 \+3/)).toBeInTheDocument();
  });

  it("netChange 음수일 때 부호 없이 렌더", () => {
    mockHook.mockReturnValue(baseData({ activeSubscriptions: { activeCount: 10, newThisWeek: 1, deactivatedThisWeek: 3, netChange: -2 } }));
    renderFooter();
    expect(screen.getByText(/순 -2/)).toBeInTheDocument();
  });

  it("showGettingStarted=true 시 시작하기 링크 노출", () => {
    mockHook.mockReturnValue(baseData({ showGettingStarted: true }));
    renderFooter();
    expect(screen.getByText(/시작하기 — 기본 설정 완료하기/)).toBeInTheDocument();
  });

  it("showGettingStarted=false 시 시작하기 링크 없음", () => {
    mockHook.mockReturnValue(baseData({ showGettingStarted: false }));
    renderFooter();
    expect(screen.queryByText(/시작하기 — 기본 설정 완료하기/)).toBeNull();
  });

  it("InfoTooltip 렌더", () => {
    mockHook.mockReturnValue(baseData());
    renderFooter();
    // InfoTooltip renders a button with aria-label="설명 보기"
    expect(screen.getByRole("button", { name: /설명 보기/ })).toBeInTheDocument();
  });
});
