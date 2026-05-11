import { describe, it, expect, vi, beforeEach, beforeAll } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

// ── 의존 서비스 mock (hoisted) ──
vi.mock("@/services/companyService", () => ({
  companyService: {
    searchUserCompanies: vi.fn().mockResolvedValue([]),
    searchAdminCompanies: vi.fn().mockResolvedValue([]),
  }
}));

vi.mock("@/services/userService", () => ({
  userService: {
    browseCategories: vi.fn().mockResolvedValue([]),
    subscribeCategoryDm: vi.fn().mockResolvedValue(undefined),
    updateSlackMemberId: vi.fn().mockResolvedValue(undefined),
  }
}));

vi.mock("@/store/authStore", () => ({
  useAuthStore: Object.assign(
    vi.fn((selector: (s: { user: { hasSlackDm: boolean } }) => unknown) =>
      selector({ user: { hasSlackDm: false } })
    ),
    { getState: () => ({ user: { hasSlackDm: false }, login: vi.fn() }) }
  ),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  }
}));

import { QuickSetupStepSource } from "../QuickSetupStepSource";
import { createQuickSetupForm } from "../model/quickSetupTypes";
import { companyService } from "@/services/companyService";

const mockSearchAdmin = vi.mocked(companyService.searchAdminCompanies);

beforeAll(() => {
  Element.prototype.scrollIntoView = Element.prototype.scrollIntoView || (() => {});
});

beforeEach(() => {
  vi.clearAllMocks();
});

describe("QuickSetupStepSource — 기업 탭 hint 및 COMPETITOR disabled UI", () => {
  function renderComponent() {
    return render(
      <QuickSetupStepSource
        form={createQuickSetupForm()}
        onChange={vi.fn()}
      />
    );
  }

  function switchToCompanyTab() {
    const companyTabBtn = screen.getByRole("button", { name: /기업 검색/ });
    fireEvent.click(companyTabBtn);
  }

  it("기업 탭으로 전환하면 hint copy가 표시된다", () => {
    renderComponent();
    switchToCompanyTab();
    expect(
      screen.getByText(/두 조건이 모두 맞는 뉴스만/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/기업만 보고 싶으면 주제 탭을 비워두세요/)
    ).toBeInTheDocument();
  });

  it("키워드 탭에서는 기업 탭 hint가 표시되지 않는다", () => {
    renderComponent();
    // default tab is keyword
    expect(
      screen.queryByText(/두 조건이 모두 맞는 뉴스만/)
    ).not.toBeInTheDocument();
  });

  it("isCompetitor=true 결과 버튼은 disabled 속성을 가진다", async () => {
    mockSearchAdmin.mockResolvedValue([
      { corpCode: "00126380", corpName: "MegaCorp", stockCode: "999930", isCompetitor: false },
      { corpCode: "00000001", corpName: "경쟁회사A", stockCode: "", isCompetitor: true },
    ] as Parameters<typeof mockSearchAdmin>[0] extends string ? never : Awaited<ReturnType<typeof mockSearchAdmin>>);

    renderComponent();
    switchToCompanyTab();

    const input = screen.getByPlaceholderText(/기업명을 입력하세요/);
    fireEvent.change(input, { target: { value: "MegaCorp" } });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /경쟁회사A/ })).toBeInTheDocument();
    });

    const competitorBtn = screen.getByRole("button", { name: /경쟁회사A/ });
    expect(competitorBtn).toBeDisabled();
  });

  it("isCompetitor=false 결과 버튼은 disabled 속성을 갖지 않는다", async () => {
    mockSearchAdmin.mockResolvedValue([
      { corpCode: "00126380", corpName: "MegaCorp", stockCode: "999930", isCompetitor: false },
    ] as Awaited<ReturnType<typeof mockSearchAdmin>>);

    renderComponent();
    switchToCompanyTab();

    const input = screen.getByPlaceholderText(/기업명을 입력하세요/);
    fireEvent.change(input, { target: { value: "MegaCorp" } });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /MegaCorp/ })).toBeInTheDocument();
    });

    const normalBtn = screen.getByRole("button", { name: /MegaCorp/ });
    expect(normalBtn).not.toBeDisabled();
  });

  it("키보드 Enter 로도 isCompetitor 항목은 선택되지 않음", async () => {
    mockSearchAdmin.mockResolvedValue([
      { corpCode: "00000002", corpName: "경쟁회사C", stockCode: "", isCompetitor: true },
    ] as Awaited<ReturnType<typeof mockSearchAdmin>>);

    const onChange = vi.fn();
    render(
      <QuickSetupStepSource
        form={createQuickSetupForm()}
        onChange={onChange}
      />
    );
    switchToCompanyTab();

    const input = screen.getByPlaceholderText(/기업명을 입력하세요/);
    fireEvent.change(input, { target: { value: "경쟁" } });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /경쟁회사C/ })).toBeInTheDocument();
    });

    // ArrowDown to highlight index 0 (the competitor item)
    fireEvent.keyDown(input, { key: "ArrowDown" });
    // Enter — should be blocked by isCompetitor guard
    fireEvent.keyDown(input, { key: "Enter" });

    expect(onChange).not.toHaveBeenCalled();
  });

  it("isCompetitor=true 버튼 클릭 시 onChange가 호출되지 않는다", async () => {
    mockSearchAdmin.mockResolvedValue([
      { corpCode: "00000001", corpName: "경쟁회사B", stockCode: "", isCompetitor: true },
    ] as Awaited<ReturnType<typeof mockSearchAdmin>>);

    const onChange = vi.fn();
    render(
      <QuickSetupStepSource
        form={createQuickSetupForm()}
        onChange={onChange}
      />
    );
    switchToCompanyTab();

    const input = screen.getByPlaceholderText(/기업명을 입력하세요/);
    fireEvent.change(input, { target: { value: "경쟁" } });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /경쟁회사B/ })).toBeInTheDocument();
    });

    const competitorBtn = screen.getByRole("button", { name: /경쟁회사B/ });
    fireEvent.click(competitorBtn);

    expect(onChange).not.toHaveBeenCalled();
  });
});
