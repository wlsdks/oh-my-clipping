import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import type { ReviewPolicyStatus } from "@/types/reviewPolicy";
import { PolicyStatusGrid } from "../PolicyStatusGrid";

function buildStatus(overrides: Partial<ReviewPolicyStatus> = {}): ReviewPolicyStatus {
  return {
    categoryId: "cat-1",
    categoryName: "경제",
    autoApproveThreshold: 0.7,
    reviewThreshold: 0.4,
    pendingReviewCount: 12,
    last7DaysProcessed: 120,
    last7DaysAutoApproved: 80,
    last7DaysManuallyReviewed: 40,
    avgScore: 0.55,
    eventTypeDistribution: { "INCLUDE": 80, "NULL": 5 },
    lastReviewedAt: "2026-04-18T10:00:00Z",
    ...overrides,
  };
}

describe("PolicyStatusGrid", () => {
  it("카테고리명 / pendingReviewCount / 최근 7일 / 인라인 임계값 editor 를 렌더한다", () => {
    const categories = [buildStatus({ categoryId: "c1", categoryName: "경제" })];

    render(<PolicyStatusGrid categories={categories} onCategoryClick={() => {}} onThresholdChange={async () => {}} />);

    expect(screen.getByText("경제")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("최근 7일: 120건")).toBeInTheDocument();
    // 레이블 + editor 내 숫자가 분리되어 렌더된다.
    expect(screen.getByText("자동 승인 임계값:")).toBeInTheDocument();
    const editor = screen.getByTestId("threshold-display");
    expect(editor).toHaveTextContent("0.70");
  });

  it("autoApproveThreshold=null 이면 '임계값 미설정' 배지 + editor 에 '설정 안 됨' 을 렌더한다", () => {
    const categories = [
      buildStatus({ categoryId: "c1", categoryName: "에너지", autoApproveThreshold: null }),
    ];

    render(<PolicyStatusGrid categories={categories} onCategoryClick={() => {}} onThresholdChange={async () => {}} />);

    expect(screen.getByText("임계값 미설정")).toBeInTheDocument();
    // editor 는 항상 렌더되며, null 일 때는 "설정 안 됨" 으로 표시 (편집 가능)
    const editor = screen.getByTestId("threshold-display");
    expect(editor).toHaveTextContent("설정 안 됨");
  });

  it("인라인 editor 저장 시 onThresholdChange(categoryId, value) 가 호출된다", async () => {
    const onThresholdChange = vi.fn().mockResolvedValue(undefined);
    const onCategoryClick = vi.fn();
    const categories = [
      buildStatus({ categoryId: "c-energy", autoApproveThreshold: null }),
    ];

    render(
      <PolicyStatusGrid
        categories={categories}
        onCategoryClick={onCategoryClick}
        onThresholdChange={onThresholdChange}
      />,
    );

    // editor 표시 버튼 클릭 → 인풋 전환 → 값 입력 → Enter
    await userEvent.click(screen.getByTestId("threshold-display"));
    const input = screen.getByTestId("threshold-input");
    await userEvent.clear(input);
    await userEvent.type(input, "0.85{Enter}");

    expect(onThresholdChange).toHaveBeenCalledTimes(1);
    expect(onThresholdChange).toHaveBeenCalledWith("c-energy", 0.85);
    // editor 상호작용이 카드 onClick 까지 전파되지 않아야 한다 (필터 전환 방지)
    expect(onCategoryClick).not.toHaveBeenCalled();
  });

  it("카드 클릭 시 onCategoryClick(categoryId) 을 호출한다", async () => {
    const onClick = vi.fn();
    const categories = [
      buildStatus({ categoryId: "c-energy", categoryName: "에너지" }),
      buildStatus({ categoryId: "c-tech", categoryName: "테크" }),
    ];

    render(<PolicyStatusGrid categories={categories} onCategoryClick={onClick} onThresholdChange={async () => {}} />);

    const cards = screen.getAllByTestId("category-card");
    expect(cards).toHaveLength(2);
    await userEvent.click(cards[1]);

    expect(onClick).toHaveBeenCalledTimes(1);
    expect(onClick).toHaveBeenCalledWith("c-tech");
  });

  it("pendingReviewCount > 50 이면 warning 색 className 을 노출한다", () => {
    const categories = [
      buildStatus({ categoryId: "c1", categoryName: "경제", pendingReviewCount: 75 }),
      buildStatus({ categoryId: "c2", categoryName: "정책", pendingReviewCount: 10 }),
    ];

    render(<PolicyStatusGrid categories={categories} onCategoryClick={() => {}} onThresholdChange={async () => {}} />);

    const highPending = screen.getByText("75");
    const lowPending = screen.getByText("10");
    expect(highPending.className).toContain("var(--status-warning-text)");
    expect(lowPending.className).not.toContain("var(--status-warning-text)");
  });

  it("긴 카테고리명도 말줄임 없이 그대로 노출한다 (내부 관리자 툴 가시성 우선)", () => {
    const longName = "아주아주 긴 카테고리명을 그대로 노출해야 한다 관리자용";
    const categories = [buildStatus({ categoryId: "c1", categoryName: longName })];

    render(<PolicyStatusGrid categories={categories} onCategoryClick={() => {}} onThresholdChange={async () => {}} />);

    const el = screen.getByText(longName);
    expect(el).toBeInTheDocument();
    // truncate/line-clamp 클래스가 붙어있지 않은지 (말줄임 금지 규칙)
    expect(el.className).not.toMatch(/truncate|line-clamp/);
  });
});
