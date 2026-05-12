import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { StyleStatsTab } from "../StyleStatsTab";
import { personaAnalyticsService } from "@/services/personaAnalyticsService";
import { createQueryClientWrapper } from "@/test/queryClient";

vi.mock("@/services/personaAnalyticsService");

const liveResponse = {
  totals: {
    totalStyles: 18,
    presetCount: 12,
    customCount: 6,
    activeSubscriptions: 247,
    presetUsageRate: 0.78,
    customStyleRatio: 0.33,
    weekOverWeekDelta: null,
  },
  presetPortfolio: [],
  customSummary: {
    totalCustomPersonas: 6,
    activeCustomSubscriptions: 12,
    newThisWeek: 0,
    recentPersonas: [],
  },
  asOf: "2026-04-09T00:00:00Z",
};

function renderTab() {
  return render(
    <MemoryRouter>
      <StyleStatsTab />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

describe("StyleStatsTab (축소판)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("3 개 스타일 카드와 Analytics 링크를 렌더링하고 활성 구독 카드는 없다", async () => {
    (personaAnalyticsService.getLive as ReturnType<typeof vi.fn>).mockResolvedValue(
      liveResponse
    );

    renderTab();

    await waitFor(() => {
      expect(screen.getByText("18개")).toBeInTheDocument();
    });
    // 스타일 구성 카드 3개 확인
    expect(screen.getByText("총 스타일 수")).toBeInTheDocument();
    expect(screen.getByText("템플릿 사용률")).toBeInTheDocument();
    expect(screen.getByText("커스텀 비중")).toBeInTheDocument();
    // 활성 구독 카드는 이 페이지에 없어야 한다
    expect(screen.queryByText("활성 구독")).not.toBeInTheDocument();
    const link = screen.getByRole("link", { name: /Analytics 열기/ });
    expect(link).toHaveAttribute("href", "/admin/analytics?tab=personas");
  });

  it("Analytics 안내 문구가 표시된다", async () => {
    (personaAnalyticsService.getLive as ReturnType<typeof vi.fn>).mockResolvedValue({
      ...liveResponse,
      totals: { ...liveResponse.totals, totalStyles: 0 },
    });

    renderTab();

    await waitFor(() => {
      expect(screen.getByText(/더 자세한 분석이 필요하세요/)).toBeInTheDocument();
    });
  });

  it("로딩 중에는 스켈레톤이 보인다", () => {
    (personaAnalyticsService.getLive as ReturnType<typeof vi.fn>).mockReturnValue(
      new Promise(() => {})
    );

    const { container } = renderTab();

    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();
  });
});
