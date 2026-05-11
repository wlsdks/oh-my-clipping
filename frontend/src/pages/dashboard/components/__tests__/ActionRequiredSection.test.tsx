import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { UseActionRequiredDataResult } from "../../hooks/useActionRequiredData";

vi.mock("../../hooks/useActionRequiredData");

import { useActionRequiredData } from "../../hooks/useActionRequiredData";
import { ActionRequiredSection } from "../ActionRequiredSection";

const mockHook = vi.mocked(useActionRequiredData);

function renderSection() {
  return render(
    <MemoryRouter>
      <ActionRequiredSection />
    </MemoryRouter>,
  );
}

const base: UseActionRequiredDataResult = {
  items: [],
  isLoading: false,
  error: null,
  refetch: vi.fn(),
};

describe("ActionRequiredSection", () => {
  beforeEach(() => vi.clearAllMocks());

  it("items 비어있으면 DOM에 없음", () => {
    mockHook.mockReturnValue({ ...base, items: [] });
    renderSection();
    expect(screen.queryByTestId("action-required-section")).toBeNull();
  });

  it("item 있으면 렌더 + aria-live polite", () => {
    mockHook.mockReturnValue({
      ...base,
      items: [{ type: "delivery_failed", severity: "danger", count: 3 }],
    });
    renderSection();
    const section = screen.getByTestId("action-required-section");
    expect(section).toBeInTheDocument();
    expect(section).toHaveAttribute("aria-live", "polite");
    expect(screen.getByText("발송 실패 3건")).toBeInTheDocument();
  });

  it('CRITICAL_100 시 "예산 초과 · 요약 중단됨" 메시지', () => {
    mockHook.mockReturnValue({
      ...base,
      items: [{ type: "budget_alert", severity: "danger", budgetLevel: "CRITICAL_100" }],
    });
    renderSection();
    expect(screen.getByText("월 LLM 예산 초과 · 요약 중단됨")).toBeInTheDocument();
  });

  it('CRITICAL_90 시 "예산 90%+" 메시지', () => {
    mockHook.mockReturnValue({
      ...base,
      items: [{ type: "budget_alert", severity: "warning", budgetLevel: "CRITICAL_90" }],
    });
    renderSection();
    expect(screen.getByText("월 LLM 예산 90%+ 도달")).toBeInTheDocument();
  });
});
