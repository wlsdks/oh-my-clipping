import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../hooks/usePendingTasksData");

import { usePendingTasksData } from "../../hooks/usePendingTasksData";
import { PendingTasksSection } from "../PendingTasksSection";

const mockHook = vi.mocked(usePendingTasksData);

function renderSection() {
  return render(
    <MemoryRouter>
      <PendingTasksSection />
    </MemoryRouter>,
  );
}

function makeData(overrides?: Partial<ReturnType<typeof usePendingTasksData>>) {
  return {
    userAccounts: { count: 0, urgencyPreview: "" },
    clippingRequests: { count: 0, urgencyPreview: "" },
    reviewItems: { count: 0, urgencyPreview: "" },
    isLoading: false,
    error: null,
    ...overrides,
  };
}

describe("PendingTasksSection", () => {
  beforeEach(() => vi.clearAllMocks());

  it('모두 0건이면 "오늘 대기 없음 — 어제 처리 완료" 렌더', () => {
    mockHook.mockReturnValue(makeData());
    renderSection();
    expect(screen.getByText(/오늘 대기 없음 — 어제 처리 완료/)).toBeInTheDocument();
  });

  it("일부 대기 있을 때 항목별 카운트 + urgencyPreview 렌더", () => {
    mockHook.mockReturnValue(
      makeData({
        userAccounts: { count: 2, urgencyPreview: "가장 오래된 3일 전" },
        reviewItems: { count: 5, urgencyPreview: "" },
      }),
    );
    renderSection();
    expect(screen.getByText("2건")).toBeInTheDocument();
    expect(screen.getByText(/가장 오래된 3일 전/)).toBeInTheDocument();
    expect(screen.getByText("5건")).toBeInTheDocument();
  });

  it("urgencyPreview 없을 때 (0건 항목) '가장 오래된' 문구 렌더 안 함", () => {
    mockHook.mockReturnValue(
      makeData({
        userAccounts: { count: 1, urgencyPreview: "가장 오래된 1일 전" },
        clippingRequests: { count: 0, urgencyPreview: "" },
      }),
    );
    renderSection();
    // clippingRequests has no urgencyPreview, check it's not there twice
    const previews = screen.queryAllByText(/가장 오래된/);
    expect(previews).toHaveLength(1);
  });
});
