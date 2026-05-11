import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type {
  ReviewPolicyStatus,
  ReviewPolicyStatusResponse,
  ScoreDistribution,
} from "@/types/reviewPolicy";

// Service mocks — 실제 HTTP 호출 차단
vi.mock("@/services/reviewPolicyService", () => ({
  reviewPolicyService: {
    getPolicyStatus: vi.fn(),
    getScoreDistribution: vi.fn(),
  },
}));

import { reviewPolicyService } from "@/services/reviewPolicyService";
import { ReviewPolicyDashboard } from "../ReviewPolicyDashboard";

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
    eventTypeDistribution: { INCLUDE: 80 },
    lastReviewedAt: "2026-04-18T10:00:00Z",
    ...overrides,
  };
}

function buildStatusResponse(
  categories: ReviewPolicyStatus[],
): ReviewPolicyStatusResponse {
  return { categories, generatedAt: "2026-04-19T00:00:00Z" };
}

function buildDistribution(overrides: Partial<ScoreDistribution> = {}): ScoreDistribution {
  return {
    buckets: [
      { range: "0.0-0.1", count: 2 },
      { range: "0.1-0.2", count: 3 },
      { range: "0.2-0.3", count: 5 },
      { range: "0.3-0.4", count: 6 },
      { range: "0.4-0.5", count: 8 },
      { range: "0.5-0.6", count: 10 },
      { range: "0.6-0.7", count: 12 },
      { range: "0.7-0.8", count: 9 },
      { range: "0.8-0.9", count: 4 },
      { range: "0.9-1.0", count: 1 },
    ],
    totalCount: 60,
    medianScore: 0.55,
    meanScore: 0.52,
    ...overrides,
  };
}

function renderDashboard(props: { onCategoryClick?: (id: string) => void } = {}) {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
    },
  });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <ReviewPolicyDashboard onCategoryClick={props.onCategoryClick ?? (() => {})} />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("ReviewPolicyDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("status fetch 성공 시 카테고리 카드 그리드를 렌더한다", async () => {
    vi.mocked(reviewPolicyService.getPolicyStatus).mockResolvedValue(
      buildStatusResponse([
        buildStatus({ categoryId: "c-eco", categoryName: "경제" }),
        buildStatus({ categoryId: "c-tech", categoryName: "테크" }),
      ]),
    );
    vi.mocked(reviewPolicyService.getScoreDistribution).mockResolvedValue(
      buildDistribution(),
    );

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText("경제")).toBeInTheDocument();
    });
    expect(screen.getByText("테크")).toBeInTheDocument();
    expect(screen.getByTestId("review-policy-dashboard")).toBeInTheDocument();
  });

  it("status fetch 실패 시 에러 메시지를 노출한다", async () => {
    vi.mocked(reviewPolicyService.getPolicyStatus).mockRejectedValue(
      new Error("server down"),
    );
    vi.mocked(reviewPolicyService.getScoreDistribution).mockResolvedValue(
      buildDistribution(),
    );

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText("정책 현황을 불러오지 못했어요")).toBeInTheDocument();
    });
    // 에러 시 하위 컴포넌트는 렌더되지 않아야 한다
    expect(screen.queryByTestId("category-card")).not.toBeInTheDocument();
  });

  it("카테고리 카드 클릭 시 onCategoryClick(categoryId) 을 호출한다", async () => {
    const onClick = vi.fn();
    vi.mocked(reviewPolicyService.getPolicyStatus).mockResolvedValue(
      buildStatusResponse([
        buildStatus({ categoryId: "c-a", categoryName: "A카테고리" }),
        buildStatus({ categoryId: "c-b", categoryName: "B카테고리" }),
      ]),
    );
    vi.mocked(reviewPolicyService.getScoreDistribution).mockResolvedValue(
      buildDistribution(),
    );

    renderDashboard({ onCategoryClick: onClick });

    await waitFor(() => {
      expect(screen.getByText("A카테고리")).toBeInTheDocument();
    });
    const cards = screen.getAllByTestId("category-card");
    await userEvent.click(cards[1]);

    expect(onClick).toHaveBeenCalledTimes(1);
    expect(onClick).toHaveBeenCalledWith("c-b");
  });

  it("초기 로딩 시 로딩 텍스트를 노출한다", () => {
    // resolve 되지 않는 promise 로 로딩 상태 유지
    vi.mocked(reviewPolicyService.getPolicyStatus).mockImplementation(
      () => new Promise(() => {}),
    );
    vi.mocked(reviewPolicyService.getScoreDistribution).mockResolvedValue(
      buildDistribution(),
    );

    renderDashboard();

    expect(screen.getByText("정책 현황을 불러오는 중...")).toBeInTheDocument();
  });

  it("임계값이 null 인 카테고리가 있으면 EmptyThresholdBanner 를 노출한다", async () => {
    vi.mocked(reviewPolicyService.getPolicyStatus).mockResolvedValue(
      buildStatusResponse([
        buildStatus({
          categoryId: "c-empty",
          categoryName: "임계값없음",
          autoApproveThreshold: null,
        }),
      ]),
    );
    vi.mocked(reviewPolicyService.getScoreDistribution).mockResolvedValue(
      buildDistribution(),
    );

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByTestId("empty-threshold-banner")).toBeInTheDocument();
    });
  });
});
