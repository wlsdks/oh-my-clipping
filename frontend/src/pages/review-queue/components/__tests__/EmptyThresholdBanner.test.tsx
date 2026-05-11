import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi } from "vitest";
import type { ReviewPolicyStatus } from "@/types/reviewPolicy";
import { EmptyThresholdBanner } from "../EmptyThresholdBanner";

function buildStatus(overrides: Partial<ReviewPolicyStatus> = {}): ReviewPolicyStatus {
  return {
    categoryId: "cat-1",
    categoryName: "경제",
    autoApproveThreshold: 0.7,
    reviewThreshold: 0.4,
    pendingReviewCount: 0,
    last7DaysProcessed: 0,
    last7DaysAutoApproved: 0,
    last7DaysManuallyReviewed: 0,
    avgScore: 0,
    eventTypeDistribution: {},
    lastReviewedAt: null,
    ...overrides,
  };
}

function renderWithRouter(categories: ReviewPolicyStatus[]) {
  return render(
    <MemoryRouter>
      <EmptyThresholdBanner categories={categories} />
    </MemoryRouter>,
  );
}

describe("EmptyThresholdBanner", () => {
  it("autoApproveThreshold=null 카테고리가 있으면 배너 + 카테고리명 + pill 버튼을 렌더한다", () => {
    const categories = [
      buildStatus({ categoryId: "c-eco", categoryName: "경제", autoApproveThreshold: 0.7 }),
      buildStatus({ categoryId: "c-energy", categoryName: "에너지", autoApproveThreshold: null }),
      buildStatus({ categoryId: "c-tech", categoryName: "테크", autoApproveThreshold: null }),
    ];

    renderWithRouter(categories);

    // 배너 제목 + 설명
    expect(screen.getByText("임계값 설정이 필요합니다")).toBeInTheDocument();
    expect(
      screen.getByText(/자동 승인 임계값이 비어있어 모든 기사가 REVIEW 로 쌓입니다/),
    ).toBeInTheDocument();

    // null 카테고리만 scroll-to-card 버튼으로 노출 — 경제 (0.7) 은 포함되면 안 됨
    expect(screen.getByRole("button", { name: "에너지" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "테크" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "경제" })).not.toBeInTheDocument();
  });

  it("모든 카테고리에 threshold 가 설정되어 있으면 null 을 렌더한다", () => {
    const categories = [
      buildStatus({ categoryId: "c1", categoryName: "경제", autoApproveThreshold: 0.7 }),
      buildStatus({ categoryId: "c2", categoryName: "정책", autoApproveThreshold: 0.5 }),
    ];

    const { container } = renderWithRouter(categories);

    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId("empty-threshold-banner")).not.toBeInTheDocument();
  });

  it("카테고리 pill 클릭 시 해당 policy-card 로 scrollIntoView 가 호출된다", async () => {
    const { default: userEvent } = await import("@testing-library/user-event");
    const categories = [
      buildStatus({ categoryId: "c-energy", categoryName: "에너지", autoApproveThreshold: null }),
    ];

    // scrollIntoView jsdom 스텁 + getElementById mock 으로 클릭 흐름만 검증
    const scrollSpy = vi.fn();
    const focusSpy = vi.fn();
    const fakeEl = { scrollIntoView: scrollSpy, focus: focusSpy } as unknown as HTMLElement;
    const originalGet = document.getElementById.bind(document);
    document.getElementById = (id: string) =>
      id === "policy-card-c-energy" ? fakeEl : originalGet(id);

    try {
      renderWithRouter(categories);
      await userEvent.click(screen.getByRole("button", { name: "에너지" }));
      expect(scrollSpy).toHaveBeenCalledTimes(1);
      expect(focusSpy).toHaveBeenCalledTimes(1);
    } finally {
      document.getElementById = originalGet;
    }
  });

  it("빠른 설정 버튼은 렌더하지 않는다 (0.5 quick-set 금지 — Product review)", () => {
    const categories = [
      buildStatus({ categoryId: "c1", categoryName: "에너지", autoApproveThreshold: null }),
    ];

    renderWithRouter(categories);

    // 빠른 설정/0.5/자동 설정 버튼류가 없어야 함
    expect(screen.queryByRole("button", { name: /빠른 설정|자동 설정|0\.5/ })).not.toBeInTheDocument();
  });
});
